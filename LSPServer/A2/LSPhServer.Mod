MODULE LSPhServer;

  IMPORT Files, Commands, Strings, Streams, Diagnostics, Formats := FoxFormats, Global := FoxGlobal, Scanner := FoxScanner,
      LSPhConstants, Compiler, SyntaxTree := FoxSyntaxTree, SemanticChecker := FoxSemanticChecker,
      Basic := FoxBasic, InterfaceComparison := FoxInterfaceComparison, LSPScannerMetadata, LSPSyntaxTreeWalker;

  VAR errlog : Streams.Writer;

  TYPE
    ModuleInfoDiagnostics* = OBJECT (Compiler.SourceDiagnostics);

      VAR out: Streams.Writer;
        moduleName: SyntaxTree.IdentifierString;
        lastDefinitionEnd, lastSymbolEnd: Streams.Position;

      PROCEDURE &Init(o: Streams.Writer);
      BEGIN out := o; moduleName := "<unknown>"; lastDefinitionEnd := 0; lastSymbolEnd := 0
      END Init;

      PROCEDURE Send(CONST type: SIGNED8; CONST source : ARRAY OF CHAR; CONST position: Streams.Position; CONST message: ARRAY OF CHAR);
        VAR pos: INTEGER;
      BEGIN
        pos := SHORT(position);
        IF (pos < 0) OR (source # "Tmp.Tmp") THEN pos := 0 END;
        out.RawSInt(type);
        out.RawLInt(pos);
        out.RawString(message);
      END Send;

      PROCEDURE Error (CONST source : ARRAY OF CHAR; position : Streams.Position; CONST message : ARRAY OF CHAR);
      BEGIN Send(LSPhConstants.Error, source, position, message);
      END Error;

      PROCEDURE Warning (CONST source : ARRAY OF CHAR; position : Streams.Position; CONST message : ARRAY OF CHAR);
      BEGIN Send(LSPhConstants.Warning, source,  position, message);
      END Warning;

      PROCEDURE Information (CONST source : ARRAY OF CHAR; position : Streams.Position; CONST message : ARRAY OF CHAR);
      BEGIN Send(LSPhConstants.Information, source, position, message);
      END Information;

      PROCEDURE SyntaxElement(kindVal: SIGNED32; start, end, defEnd: Streams.Position; CONST defMod: ARRAY OF CHAR);
      BEGIN
        out.RawSInt(LSPhConstants.SyntaxElement);
        out.RawLInt(SHORT(start));
        out.RawLInt(SHORT(end));
        out.RawSInt(SHORT(SHORT(kindVal)));
        out.RawLInt(SHORT(defEnd));
        IF defEnd # -1 THEN out.RawString(defMod) END;
      END SyntaxElement;

      PROCEDURE SymbolScanned(CONST source: ARRAY OF CHAR; symbol: Scanner.Symbol; pos: Basic.Position);
        VAR kind: LSPScannerMetadata.SyntaxElementKind;
      BEGIN
        kind := LSPScannerMetadata.GetSyntaxKind(symbol); lastSymbolEnd := pos.end;
        IF source # "Tmp.Tmp" THEN 
           lastSymbolEnd := -1
        ELSIF kind # LSPScannerMetadata.SyntaxElementKind.None THEN
           SyntaxElement(MapSyntaxElementKind(kind), pos.start, pos.end, -1, "")
        END
      END SymbolScanned;

      PROCEDURE UpdateSubSymbol(symbol, subSymbol: Scanner.Symbol);
        VAR command: SIGNED8;
      BEGIN
        CASE subSymbol OF
          Scanner.LeftParenthesisProcParamStart: command := LSPhConstants.ProcParamStart;
          |Scanner.LeftParenthesisCallParamStart: command := LSPhConstants.CallParamStart;
          |Scanner.RightParenthesisParamEnd:command := LSPhConstants.ParamEnd;
          |Scanner.CommaParamSeparator, Scanner.SemicolonParamSeparator: command := LSPhConstants.ParamNext;
          ELSE command := -1;
        END;
        IF (lastSymbolEnd # -1) & (command # -1) THEN
          out.RawSInt(command);
          out.RawLInt(SHORT(lastSymbolEnd));
        END;
      END UpdateSubSymbol;

      PROCEDURE FoundDefinition(kind: SIGNED32; pos: Basic.Position);
      BEGIN
        IF (pos.end <= lastDefinitionEnd) THEN
          Send(LSPhConstants.Error, "Tmp.Tmp", pos.end, "LSP: Invalid definition order");
        END;
        lastDefinitionEnd := pos.end;
        SyntaxElement(kind, pos.start, pos.end, pos.end, moduleName)
      END FoundDefinition;

      PROCEDURE ModuleParsed(module: SyntaxTree.Module; VAR errors: BOOLEAN; VAR findScopeAtPosition: Streams.Position);
          VAR
            import: SyntaxTree.Import;
            ic: SyntaxTree.Identifier;
            s, s2: SyntaxTree.IdentifierString;
        BEGIN
          Global.GetModuleName(module, moduleName);
          FoundDefinition(LSPhConstants.SynModule, module.position);
          out.RawSInt(LSPhConstants.ModuleName);
          out.RawString(moduleName);
          import := module.moduleScope.firstImport;
          WHILE import # NIL DO
            ic := import.context;
            IF ic = SyntaxTree.invalidIdentifier THEN ic := module.context END;
            Global.GetModuleSectionName(import.moduleName, ic, s);
            IF import.modulePosition.end = import.position.end THEN
              SyntaxElement(LSPhConstants.SynModule, import.position.start, import.position.end, 1, s);
            ELSE
              Basic.GetString(import.name, s2);
              SyntaxElement(LSPhConstants.SynModule, import.position.start, import.position.end, 1, s2);
              out.RawSInt(LSPhConstants.ImportAlias);
              SyntaxElement(LSPhConstants.SynModule, import.modulePosition.start, import.modulePosition.end, 1, s);
            END;
            out.RawSInt(LSPhConstants.ModuleImport);
            out.RawString(s);
            import := import.nextImport;
          END;
          IF module.endRepeatPosition.end # Basic.invalidPosition.end THEN
            SyntaxElement(LSPhConstants.SynModule, module.endRepeatPosition.start, module.endRepeatPosition.end, 1, moduleName);
          END;
          out.RawSInt(LSPhConstants.DefinitionRepeat);
      END ModuleParsed;

      PROCEDURE ModuleChecked(module: SyntaxTree.Module; VAR errors: BOOLEAN; foundScope: SyntaxTree.Scope; foundScopeIsTraverse: BOOLEAN; foundScopeName: SyntaxTree.Identifier);
        VAR crash: BOOLEAN;
          v: ModuleInfoLSPIdentifierVisitor; bv: BufferingLSPIdentifierVisitor;
      BEGIN crash := TRUE;
        NEW(v, SELF, moduleName, errors);
        NEW(bv, v);
        LSPSyntaxTreeWalker.VisitIdentifiers(bv, module);
        bv.Flush();
        crash := FALSE;
      FINALLY
        IF crash THEN Log(moduleName); Log("LSPSyntaxTreeWalker threw an exception") END;
      END ModuleChecked;

      PROCEDURE CheckSymbolFileChanged(module: SyntaxTree.Module; symbolFile: Formats.SymbolFileFormat; importCache: SyntaxTree.ModuleScope);
      VAR
        sflags: SET;
          sdiagnostics: Diagnostics.DiagnosticsList;
        fname: Basic.FileName;
        importedModule: SyntaxTree.Module;
      BEGIN
        sflags := {};
            NEW(sdiagnostics);
        Global.ModuleFileName(module.name,module.context,fname);
        importedModule := symbolFile.Import(fname,importCache);
        IF importedModule = NIL THEN
                INCL(sflags, InterfaceComparison.Redefined)
        END;
        InterfaceComparison.CompareThis(module,symbolFile,sdiagnostics,importCache,sflags);
        IF (InterfaceComparison.Redefined IN sflags) OR (InterfaceComparison.Extended IN sflags) OR (sdiagnostics.entries # NIL) THEN
              out.RawSInt(LSPhConstants.SymbolFileChanged);
        END;
      END CheckSymbolFileChanged;

       PROCEDURE GenerateSymbolFile(symbolFile: Formats.SymbolFileFormat; module: SyntaxTree.Module; importCache: SyntaxTree.ModuleScope): BOOLEAN;
       VAR moduleFileName: Basic.FileName; smv: ModuleInfoLSPSymbolMappingVisitor;  symbols: SyntaxTree.Module; newImportCache: SyntaxTree.ModuleScope; result, crash : BOOLEAN;
      BEGIN result := FALSE; crash := TRUE;
        CheckSymbolFileChanged(module, symbolFile, importCache);
        IF symbolFile.Export(module, importCache) THEN
          Global.ModuleFileName(module.name,module.context,moduleFileName);
          newImportCache := SyntaxTree.NewModuleScope();
          symbols := symbolFile.Import(moduleFileName, newImportCache);
          NEW(smv, SELF);
          LSPSyntaxTreeWalker.VisitSymbolMapping(smv, symbols, module);
          result := TRUE;
        END;
        crash := FALSE;
      FINALLY
        IF crash THEN Log(moduleName); Log("GenerateSymbolFile threw an exception") END;
        RETURN result & ~crash;
      END GenerateSymbolFile;

    END ModuleInfoDiagnostics;

    AutoCompleteDiagnostics* = OBJECT (Compiler.SourceDiagnostics);

      VAR out: Streams.Writer; endPos: SIGNED32; foundDesignator: SyntaxTree.Designator;

      PROCEDURE &Init(o: Streams.Writer; endPos: SIGNED32);
      BEGIN out := o; SELF.endPos := endPos; foundDesignator := NIL;
      END Init;

      PROCEDURE Send(CONST syntaxType: INTEGER; CONST suggestion: ARRAY OF CHAR);
      BEGIN
        out.RawSInt(LSPhConstants.Completion);
        out.RawSInt(SHORT(SHORT(syntaxType)));
        out.RawString(suggestion);
      END Send;

      PROCEDURE Error (CONST source : ARRAY OF CHAR; position : Streams.Position; CONST message : ARRAY OF CHAR);
      END Error;

      PROCEDURE Warning (CONST source : ARRAY OF CHAR; position : Streams.Position; CONST message : ARRAY OF CHAR);
      END Warning;

      PROCEDURE Information (CONST source : ARRAY OF CHAR; position : Streams.Position; CONST message : ARRAY OF CHAR);
      END Information;

      PROCEDURE SymbolScanned(CONST source: ARRAY OF CHAR; symbol: Scanner.Symbol; pos: Basic.Position);
      END SymbolScanned;

      PROCEDURE UpdateSubSymbol(symbol, subSymbol: Scanner.Symbol);
      END UpdateSubSymbol;

      PROCEDURE ModuleParsed(module: SyntaxTree.Module; VAR errors: BOOLEAN; VAR findScopeAtPosition: Streams.Position);
      BEGIN errors := FALSE; findScopeAtPosition := endPos;
      END ModuleParsed;

      PROCEDURE ModuleChecked(module: SyntaxTree.Module; VAR errors: BOOLEAN; foundScope: SyntaxTree.Scope; foundScopeIsTraverse: BOOLEAN; foundScopeName: SyntaxTree.Identifier);
        VAR nameString: SyntaxTree.IdentifierString; i: SIZE;
      BEGIN errors := TRUE;
        IF (foundScope # NIL) THEN
          Basic.GetString(foundScopeName, nameString);
          i := Strings.Length(nameString);
          IF (endPos = endPos) & (nameString[i-1] = 'X') THEN
            nameString[i-1] := 0X;
            ScanScope(foundScope, foundScopeIsTraverse, nameString)
          END;
        END;
      END ModuleChecked;

      PROCEDURE ScanScope(scope: SyntaxTree.Scope; traverseScope: BOOLEAN; typedName: SyntaxTree.IdentifierString);
        VAR s: SyntaxTree.Symbol; nameString: SyntaxTree.IdentifierString; ownerRecord, base: SyntaxTree.RecordType;
      BEGIN
        IF traverseScope & (scope.outerScope # NIL) THEN ScanScope(scope.outerScope, traverseScope, typedName) END;
        IF (scope IS SyntaxTree.RecordScope) THEN
              ownerRecord := scope(SyntaxTree.RecordScope).ownerRecord;
              base := SemanticChecker.RecordBase(ownerRecord);
              IF (base # NIL) THEN ScanScope(base.recordScope, traverseScope, typedName) END
        END;
        s := scope.firstSymbol;
        WHILE s # NIL DO
          IF (~(s IS SyntaxTree.Operator)) & (s.name # SyntaxTree.invalidIdentifier) THEN
            Basic.GetString(s.name, nameString);
            IF (nameString[0] # 0X) & (Strings.StartsWith(typedName, 0, nameString)) THEN
              Send(MapSyntaxElementKind(LSPSyntaxTreeWalker.MapKind(s)), nameString)
            END
          END;
          s := s.nextSymbol;
        END;
      END ScanScope;

       PROCEDURE GenerateSymbolFile(symbolFile: Formats.SymbolFileFormat; module: SyntaxTree.Module; importCache: SyntaxTree.ModuleScope): BOOLEAN;
      BEGIN
      RETURN TRUE;
      END GenerateSymbolFile;

    END AutoCompleteDiagnostics;

    ReformatDiagnostics* = OBJECT (Compiler.SourceDiagnostics);

      VAR out: Streams.Writer; lastPosition: Basic.Position; lastFormat: SET;

      PROCEDURE &Init(o: Streams.Writer);
      BEGIN out := o; lastFormat := {}
      END Init;

      PROCEDURE FormatToken(position: Basic.Position; format: SET);
        VAR formatType: SIGNED8;
      BEGIN formatType := 0;
        IF LSPScannerMetadata.OneNewlineBefore IN format THEN
          formatType := 20
        ELSIF LSPScannerMetadata.MoreNewlinesBefore IN format THEN
          formatType := 30
        ELSIF LSPScannerMetadata.OneSpaceBefore IN format THEN
          formatType := 10
        ELSIF LSPScannerMetadata.MaybeSpaceBefore IN format THEN
          formatType := 90
        END;
        IF LSPScannerMetadata.MaybeSpaceAfter IN format THEN
          INC(formatType, 9)
        ELSIF LSPScannerMetadata.OneSpaceAfter IN format THEN
          INC(formatType)
        END;
        out.RawSInt(LSPhConstants.FormatToken);
        out.RawLInt(SHORT(position.start));
        out.RawLInt(SHORT(position.end));
        out.RawSInt(formatType);
        IF LSPScannerMetadata.IsComment IN format THEN
          out.RawSInt(LSPhConstants.TokenIsComment)
        END;
        IF LSPScannerMetadata.DedentOne IN format THEN
          out.RawSInt(LSPhConstants.OutdentThisLine)
        ELSIF LSPScannerMetadata.DedentOneWithComment IN format THEN
          out.RawSInt(LSPhConstants.OutdentThisLineAndComment)
        ELSIF LSPScannerMetadata.DedentTwo IN format THEN
          out.RawSInt(LSPhConstants.OutdentThisLine);
          out.RawSInt(LSPhConstants.OutdentThisLine)
        END;
        IF LSPScannerMetadata.IndentOne IN format THEN
          out.RawSInt(LSPhConstants.IndentNextLine)
        ELSIF LSPScannerMetadata.IndentTwo IN format THEN
          out.RawSInt(LSPhConstants.IndentNextLine);
          out.RawSInt(LSPhConstants.IndentNextLine)
        END;
      END FormatToken;

      PROCEDURE Error (CONST source : ARRAY OF CHAR; position : Streams.Position; CONST message : ARRAY OF CHAR);
      END Error;

      PROCEDURE Warning (CONST source : ARRAY OF CHAR; position : Streams.Position; CONST message : ARRAY OF CHAR);
      END Warning;

      PROCEDURE Information (CONST source : ARRAY OF CHAR; position : Streams.Position; CONST message : ARRAY OF CHAR);
      END Information;

      PROCEDURE SymbolScanned(CONST source: ARRAY OF CHAR; symbol: Scanner.Symbol; pos: Basic.Position);
      BEGIN
         IF source = "Tmp.Tmp" THEN
          IF lastFormat # {} THEN
            FormatToken(lastPosition, lastFormat);
          END;
          lastFormat := LSPScannerMetadata.GetFormatToken(symbol);
          lastPosition := pos;
        END
      END SymbolScanned;

      PROCEDURE UpdateSubSymbol(symbol, subSymbol: Scanner.Symbol);
      BEGIN lastFormat := LSPScannerMetadata.GetFormatToken(subSymbol);
      END UpdateSubSymbol;

      PROCEDURE ModuleParsed(module: SyntaxTree.Module; VAR errors: BOOLEAN; VAR findScopeAtPosition: Streams.Position);
        BEGIN errors := TRUE
      END ModuleParsed;

      PROCEDURE ModuleChecked(module: SyntaxTree.Module; VAR errors: BOOLEAN; foundScope: SyntaxTree.Scope; foundScopeIsTraverse: BOOLEAN; foundScopeName: SyntaxTree.Identifier);
      END ModuleChecked;

      PROCEDURE GenerateSymbolFile(symbolFile: Formats.SymbolFileFormat; module: SyntaxTree.Module; importCache: SyntaxTree.ModuleScope): BOOLEAN;
      BEGIN RETURN FALSE;
      END GenerateSymbolFile;

    END ReformatDiagnostics;

    DefinitionKind = ENUM Head, Definition, IncreaseScope, DecreaseScope END;

    DefinitionElem = POINTER TO DefinitionDesc;
    DefinitionDesc = RECORD
      next: DefinitionElem;
      kind: DefinitionKind;
      skind: LSPScannerMetadata.SyntaxElementKind;
      pos, exportedPos: Basic.Position;
      exported, command, removableIfUnused, procedure: BOOLEAN;
      procStart, procEnd: Streams.Position; procIdentifier: Basic.Position;
    END;

    BufferingLSPIdentifierVisitor = OBJECT (LSPSyntaxTreeWalker.LSPIdentifierVisitor)
      VAR visitor: LSPSyntaxTreeWalker.LSPIdentifierVisitor;
      VAR list: DefinitionElem;

      PROCEDURE &Init(visitor: LSPSyntaxTreeWalker.LSPIdentifierVisitor);
      VAR e: DefinitionElem;
      BEGIN NEW(e); e.kind := DefinitionKind.Head; e.next := NIL; SELF.visitor := visitor; list := e;
      END Init;

      PROCEDURE VisitDefinition(kind: LSPScannerMetadata.SyntaxElementKind; pos, exportedPos: Basic.Position; exported, command, removableIfUnused: BOOLEAN);
      VAR e, addPos: DefinitionElem;
      BEGIN addPos := list;
        WHILE (addPos.next # NIL) & (pos.end < addPos.next.pos.end) DO addPos := addPos.next END;
        NEW(e); e.next := addPos.next; e.kind := DefinitionKind.Definition;
        e.pos := pos; e.exportedPos := exportedPos; e.exported := exported;
        e.command := command;
        e.removableIfUnused := removableIfUnused; e.skind := kind;
        addPos.next := e;
      END VisitDefinition;

      PROCEDURE VisitLocalReference(kind: LSPScannerMetadata.SyntaxElementKind; pos, refPos: Basic.Position; writtenTo, endRepeat, overriding: BOOLEAN);
      BEGIN visitor.VisitLocalReference(kind, pos, refPos, writtenTo, endRepeat, overriding);
      END VisitLocalReference;

      PROCEDURE VisitImportedReference(kind: LSPScannerMetadata.SyntaxElementKind; pos: Basic.Position; symbolIndex: SIGNED32; moduleName, moduleContext: SyntaxTree.Identifier; writtenTo, overriding: BOOLEAN);
      BEGIN visitor.VisitImportedReference(kind, pos, symbolIndex, moduleName, moduleContext, writtenTo, overriding);
      END VisitImportedReference;

      PROCEDURE VisitIncreaseOutlineScope(containsCode: BOOLEAN);
        VAR e: DefinitionElem;
      BEGIN
        IF list.next # NIL THEN
          NEW(e); e.kind := DefinitionKind.IncreaseScope; e.procedure := containsCode;
          e.pos := list.next.pos;
          e.next := list.next; list.next := e;
        END;
      END VisitIncreaseOutlineScope;

      PROCEDURE VisitDecreaseOutlineScope(containsCode: BOOLEAN; procStart, procEnd: Streams.Position; procIdentifier: Basic.Position);
        VAR e: DefinitionElem;
      BEGIN
        IF list.next # NIL THEN
          NEW(e); e.kind := DefinitionKind.DecreaseScope; e.procedure := containsCode; e.procStart := procStart; e.procEnd := procEnd; e.procIdentifier := procIdentifier;
          e.pos := list.next.pos;
          e.next := list.next; list.next := e;
        END;
      END VisitDecreaseOutlineScope;

      PROCEDURE Flush();
      VAR reversed, curr, next: DefinitionElem;
      BEGIN curr := list.next; reversed := NIL;
        WHILE(curr # NIL) DO
          next := curr.next;
          curr.next := reversed;
          reversed := curr;
          curr := next;
        END; curr := reversed;
        WHILE(curr # NIL) DO
          CASE curr.kind OF
            DefinitionKind.Definition:
              visitor.VisitDefinition(curr.skind, curr.pos, curr.exportedPos, curr.exported, curr.command, curr.removableIfUnused);
            |DefinitionKind.IncreaseScope:
              visitor.VisitIncreaseOutlineScope(curr.procedure);
            |DefinitionKind.DecreaseScope:
              visitor.VisitDecreaseOutlineScope(curr.procedure, curr.procStart, curr.procEnd, curr.procIdentifier);
          END;
          curr := curr.next;
        END;
      END Flush;

    END BufferingLSPIdentifierVisitor;

    ModuleInfoLSPIdentifierVisitor* = OBJECT (LSPSyntaxTreeWalker.LSPIdentifierVisitor)

      VAR diag: ModuleInfoDiagnostics; moduleName: SyntaxTree.IdentifierString; errors: BOOLEAN;

      PROCEDURE &Init(diag: ModuleInfoDiagnostics; moduleName: SyntaxTree.IdentifierString; errors: BOOLEAN);
      BEGIN SELF.diag := diag; SELF.moduleName := moduleName; SELF.errors := errors
      END Init;

      PROCEDURE VisitDefinition(kind: LSPScannerMetadata.SyntaxElementKind; pos, exportedPos: Basic.Position; exported, command, removableIfUnused: BOOLEAN);
        VAR kindVal: SIGNED32;
      BEGIN
        kindVal := MapSyntaxElementKind(kind);
        IF kindVal # -1 THEN
          diag.FoundDefinition(kindVal, pos);
          IF exported THEN
            diag.out.RawSInt(LSPhConstants.NameExportedAt);
            diag.out.RawLInt(SHORT(exportedPos.end));
            IF command THEN
              diag.out.RawSInt(LSPhConstants.CommandExported);
            END
          END;
          IF ~removableIfUnused THEN
            diag.out.RawSInt(LSPhConstants.VarProcParam);
          END;
        END;
      END VisitDefinition;

      PROCEDURE VisitLocalReference(kind: LSPScannerMetadata.SyntaxElementKind; pos, refPos: Basic.Position; writtenTo, endRepeat, overriding: BOOLEAN);
        VAR kindVal: SIGNED32;
      BEGIN
        kindVal := MapSyntaxElementKind(kind);
        IF overriding THEN kindVal := LSPhConstants.SynOverrideReference END;
        IF kindVal # -1 THEN
          diag.SyntaxElement(kindVal, pos.start, pos.end, refPos.end, moduleName);
          IF writtenTo THEN
            diag.out.RawSInt(LSPhConstants.VarModified);
          END;
          IF endRepeat THEN
            diag.out.RawSInt(LSPhConstants.DefinitionRepeat);
          END;
        END;
      END VisitLocalReference;

      PROCEDURE VisitImportedReference(kind: LSPScannerMetadata.SyntaxElementKind; pos: Basic.Position; symbolIndex: SIGNED32; moduleName, moduleContext: SyntaxTree.Identifier; writtenTo, overriding: BOOLEAN);
        VAR kindVal: SIGNED32; str: SyntaxTree.IdentifierString;
      BEGIN
        kindVal := MapSyntaxElementKind(kind);
        IF overriding THEN kindVal := LSPhConstants.SynOverrideReference END;
        IF kindVal # -1 THEN
          Global.GetModuleSectionName(moduleName, moduleContext, str);
          diag.SyntaxElement(kindVal, pos.start, pos.end, -symbolIndex, str);
          IF writtenTo THEN
            diag.out.RawSInt(LSPhConstants.VarModified);
          END;
        END;
      END VisitImportedReference;

      PROCEDURE VisitIncreaseOutlineScope(containsCode: BOOLEAN);
      BEGIN
        diag.out.RawSInt(LSPhConstants.RecordStart);
      END VisitIncreaseOutlineScope;

      PROCEDURE VisitDecreaseOutlineScope(containsCode: BOOLEAN; procStart, procEnd: Streams.Position; procIdentifier: Basic.Position);
      BEGIN
        IF ~containsCode THEN
          diag.out.RawSInt(LSPhConstants.RecordEnd);
        ELSE
          diag.out.RawSInt(LSPhConstants.RecordEndAsProcedure);
          diag.out.RawLInt(SHORT(procStart));
          diag.out.RawLInt(SHORT(procEnd));
          diag.out.RawLInt(SHORT(procIdentifier.end));
        END
      END VisitDecreaseOutlineScope;

    END ModuleInfoLSPIdentifierVisitor;

    ModuleInfoLSPSymbolMappingVisitor = OBJECT (LSPSyntaxTreeWalker.LSPSymbolMappingVisitor)

        VAR diag: ModuleInfoDiagnostics;

        PROCEDURE &Init(diag: ModuleInfoDiagnostics);
        BEGIN SELF.diag := diag
        END Init;

        PROCEDURE VisitSymbolMatch(pos: Basic.Position; symbolIndex: SIGNED32);
        BEGIN
          diag.out.RawSInt(LSPhConstants.SymbolFileIndex);
          diag.out.RawLInt(-symbolIndex);
          diag.out.RawString(diag.moduleName);
          diag.out.RawLInt(SHORT(pos.end));
        END VisitSymbolMatch;

    END ModuleInfoLSPSymbolMappingVisitor;


  PROCEDURE MapSyntaxElementKind(kind: LSPScannerMetadata.SyntaxElementKind) : SIGNED32;
    VAR kindVal : SIGNED32;
  BEGIN
    CASE kind OF
      LSPScannerMetadata.SyntaxElementKind.Constant: kindVal := LSPhConstants.SynConstant;
      |LSPScannerMetadata.SyntaxElementKind.Operator: kindVal := LSPhConstants.SynOperator;
      |LSPScannerMetadata.SyntaxElementKind.Keyword: kindVal := LSPhConstants.SynKeyword;
      |LSPScannerMetadata.SyntaxElementKind.String: kindVal := LSPhConstants.SynString;
      |LSPScannerMetadata.SyntaxElementKind.Comment: kindVal := LSPhConstants.SynComment;
      |LSPScannerMetadata.SyntaxElementKind.Type: kindVal := LSPhConstants.SynType;
      |LSPScannerMetadata.SyntaxElementKind.Undefined: kindVal := LSPhConstants.SynUndefined;
      |LSPScannerMetadata.SyntaxElementKind.Module: kindVal := LSPhConstants.SynModule;
      |LSPScannerMetadata.SyntaxElementKind.Variable: kindVal := LSPhConstants.SynVariable;
      |LSPScannerMetadata.SyntaxElementKind.Property: kindVal := LSPhConstants.SynProcedure;
      |LSPScannerMetadata.SyntaxElementKind.Parameter: kindVal := LSPhConstants.SynParameter;
      |LSPScannerMetadata.SyntaxElementKind.VariableParameter: kindVal := LSPhConstants.SynVariable;
      |LSPScannerMetadata.SyntaxElementKind.RecordField: kindVal := LSPhConstants.SynRecordField;
      |LSPScannerMetadata.SyntaxElementKind.Procedure: kindVal := LSPhConstants.SynProcedure;
      |LSPScannerMetadata.SyntaxElementKind.Alias: kindVal := LSPhConstants.SynType;
      |LSPScannerMetadata.SyntaxElementKind.ThisModule: kindVal := -1;
      |LSPScannerMetadata.SyntaxElementKind.ModuleImport: kindVal := -1;
      |LSPScannerMetadata.SyntaxElementKind.ModuleAliasDef: kindVal := -1;
    END;
    RETURN kindVal
  END MapSyntaxElementKind;

  PROCEDURE Log(msg: ARRAY OF CHAR);
  BEGIN errlog.String(msg); errlog.Ln(); errlog.Update()
  END Log;

  PROCEDURE BuildContext(path: ARRAY OF CHAR): Commands.Context;
  VAR context: Commands.Context;
    sw: Streams.StringWriter;
    arg: Streams.StringReader;
    len: SIZE;
    buf: POINTER TO ARRAY OF CHAR;
  BEGIN
    len := Strings.Length(path) + 2;
    Streams.OpenStringWriter(sw, len);
    sw.Char('"'); sw.String(path); sw.Char('"');
    NEW(buf, len+1);
    sw.Get(buf^);
    Streams.OpenStringReader(arg, buf^);
    NEW(context, NIL, arg, NIL, NIL, NIL);
  RETURN context;
  END BuildContext;

  PROCEDURE Modules(CONST source: ARRAY OF CHAR; reader: Streams.Reader; position: SIGNED32; diagnostics: Diagnostics.Diagnostics; log: Streams.Writer;  CONST options: Compiler.CompilerOptions; VAR importCache: SyntaxTree.ModuleScope): BOOLEAN;
    VAR success, crash: BOOLEAN;
  BEGIN
    success := FALSE;
    crash := TRUE;
    success := Compiler.Modules(source, reader, position, diagnostics, log, options, importCache);
    crash := FALSE;
  FINALLY
    IF crash THEN Log(source); Log("FoxSemanticChecker threw an exception") END;
    RETURN success & ~crash;
  END Modules;

  PROCEDURE CompileDiag(err: Streams.Writer; optionString: ARRAY OF CHAR; diagnostics: Compiler.SourceDiagnostics);
    VAR
      error: BOOLEAN;
      options: Compiler.CompilerOptions;
      (*optString: ARRAY 1024 OF CHAR;*)
      optSR: Streams.StringReader;
      log: Streams.StringWriter;
      filename: Files.FileName;
      reader: Streams.Reader;
      importCache: SyntaxTree.ModuleScope;
  BEGIN
    error := FALSE; importCache := NIL;
    NEW(log, 1024);
    (* optionString := '-p=Win64 --objectFile="" --symbolFile=Textual '; *)
    Streams.OpenStringReader(optSR, optionString);
    filename := "Tmp.Tmp";
    IF Compiler.GetOptions(optSR, err, diagnostics, options) THEN
      reader := Basic.GetFileReader(filename);
      IF reader = NIL THEN
        diagnostics.Error(filename, Streams.Invalid, "failed to open"); error := TRUE;
      ELSE
        error := ~Modules(filename, reader, 0, diagnostics, log, options, importCache);
        IF error THEN diagnostics.Error(filename, reader.Pos(), "Compilation FAILED") END;
      END;
      err.Update;
    ELSE
      diagnostics.Error(filename, Streams.Invalid, "Parsing options FAILED")
    END;
  END CompileDiag;

  PROCEDURE Compile(out, err: Streams.Writer; optionString: ARRAY OF CHAR);
  VAR diagnostics: ModuleInfoDiagnostics;
  BEGIN
    NEW(diagnostics, out);
    CompileDiag(err, optionString, diagnostics);
  END Compile;

  PROCEDURE Complete(out, err: Streams.Writer; endPos: SIGNED32; optionString: ARRAY OF CHAR);
  VAR diagnostics: AutoCompleteDiagnostics;
  BEGIN
    NEW(diagnostics, out, endPos);
    CompileDiag(err, optionString, diagnostics);
  END Complete;

  PROCEDURE Reformat(out, err: Streams.Writer; optionString: ARRAY OF CHAR);
  VAR diagnostics: ReformatDiagnostics;
  BEGIN
    NEW(diagnostics, out);
    CompileDiag(err, optionString, diagnostics);
  END Reformat;

  PROCEDURE HandleStream(in: Streams.Reader; out, err: Streams.Writer; optionString: ARRAY OF CHAR);
    VAR
      running: BOOLEAN;
      name: ARRAY 1024 OF CHAR;
      code: SIGNED8;
      ilen: INTEGER;
      len, readlen, alen: SIZE;
      F: Files.File;
      R: Files.Rider;
  BEGIN running := TRUE;
    in.SetByteOrder(Streams.ByteOrder.LittleEndian);
    out.SetByteOrder(Streams.ByteOrder.LittleEndian);
    WHILE running DO
      in.RawSInt(code);
      IF (code = LSPhConstants.GetModuleInfo) OR (code = LSPhConstants.AutoComplete) OR (code = LSPhConstants.ReFormat) THEN
        F := Files.New("Tmp.Tmp");
        F.Set(R, 0);
        in.RawLInt(ilen); len := ilen;
        WHILE len > 0 DO
          IF len < 1024 THEN readlen := len ELSE readlen := 1024 END;
          in.Bytes(name, 0, readlen, alen);
          F.WriteBytes(R, name, 0, alen);
          DEC(len, alen);
        END;
        IF code = LSPhConstants.AutoComplete THEN F.WriteBytes(R, "X;END;END.", 0, 10) END;
        Files.Register(F);
        IF code = LSPhConstants.GetModuleInfo THEN
          Compile(out, err, optionString);
        ELSIF code = LSPhConstants.AutoComplete THEN
          Complete(out, err, ilen+1, optionString);
        ELSIF code = LSPhConstants.ReFormat THEN
          Reformat(out, err, optionString);
        END;
        out.RawSInt(LSPhConstants.OK)
      ELSIF code = LSPhConstants.SwitchEmbeddedMode THEN
      ELSIF (code = LSPhConstants.Exit) OR (code = 0) THEN
        running := FALSE
      ELSE
        out.RawSInt(LSPhConstants.Invalid)
      END;
      out.Update;
    END;
  END HandleStream;

  PROCEDURE Run*(context: Commands.Context);
    VAR
    name, optionString: ARRAY 1024 OF CHAR;
  BEGIN
    errlog := context.error;
    IF (~context.arg.GetString(optionString)) THEN
      RETURN;
    END;
    IF context.arg.GetString(name) THEN
      Files.SetWorkPath(BuildContext(name));
    END;
    WHILE context.arg.GetString(name) DO
      Files.AddSearchPath(BuildContext(name));
    END;
    HandleStream(context.in, context.out, context.error, optionString);
  END Run;

BEGIN
END LSPhServer.
