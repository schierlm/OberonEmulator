MODULE LSPSyntaxTreeWalker;

IMPORT
	Scanner := FoxScanner, SyntaxTree := FoxSyntaxTree, Global := FoxGlobal, Basic := FoxBasic, 
	SemanticChecker := FoxSemanticChecker, LSPScannerMetadata, Streams;

TYPE
	
    LSPIdentifierVisitor* = OBJECT
    
        PROCEDURE VisitDefinition*(kind: LSPScannerMetadata.SyntaxElementKind; pos, exportedPos: Basic.Position; exported, removableIfUnused: BOOLEAN);
        BEGIN
        END VisitDefinition;

		PROCEDURE VisitLocalReference*(kind: LSPScannerMetadata.SyntaxElementKind; pos, refPos: Basic.Position; writtenTo, endRepeat, overriding: BOOLEAN);
		BEGIN
		END VisitLocalReference;

		PROCEDURE VisitImportedReference*(kind: LSPScannerMetadata.SyntaxElementKind; pos: Basic.Position; symbolIndex: SIGNED32; moduleName, moduleContext: SyntaxTree.Identifier; writtenTo, overriding: BOOLEAN);
		BEGIN
		END VisitImportedReference;

		PROCEDURE VisitIncreaseOutlineScope*(containsCode: BOOLEAN);
		BEGIN
		END VisitIncreaseOutlineScope;
		
		PROCEDURE VisitDecreaseOutlineScope*(containsCode: BOOLEAN; procStart, procEnd: Streams.Position; procIdentifier: Basic.Position);
		BEGIN
		END VisitDecreaseOutlineScope;
	
    END LSPIdentifierVisitor;

    LSPSymbolMappingVisitor* = OBJECT
    
        PROCEDURE VisitSymbolMatch*(pos: Basic.Position; symbolIndex: SIGNED32);
        BEGIN
        END VisitSymbolMatch;

    END LSPSymbolMappingVisitor;
	
	IdentifierVisitor = OBJECT (SyntaxTree.Visitor)
	VAR
        visitor: LSPIdentifierVisitor;
		assigning: BOOLEAN;
		moduleContext, moduleName: SyntaxTree.Identifier;

        PROCEDURE &Init*(v: LSPIdentifierVisitor);
        BEGIN visitor := v; assigning := FALSE;
        END Init;

		PROCEDURE Type*(x: SyntaxTree.Type);
		BEGIN
			IF x # NIL THEN
				VType(x);
			END;
		END Type;

		PROCEDURE VisitType*(x: SyntaxTree.Type);
		BEGIN
		END VisitType;

		PROCEDURE VisitBasicType*(x: SyntaxTree.BasicType);
		BEGIN
		END VisitBasicType;

		PROCEDURE VisitBooleanType*(x: SyntaxTree.BooleanType);
		BEGIN
		END VisitBooleanType;

		PROCEDURE VisitSetType*(x: SyntaxTree.SetType);
		BEGIN
		END VisitSetType;

		PROCEDURE VisitSizeType*(x: SyntaxTree.SizeType);
		BEGIN
		END VisitSizeType;

		PROCEDURE VisitCharacterType*(x: SyntaxTree.CharacterType);
		BEGIN
		END VisitCharacterType;

		PROCEDURE VisitIntegerType*(x: SyntaxTree.IntegerType);
		BEGIN
		END VisitIntegerType;

		PROCEDURE VisitFloatType*(x: SyntaxTree.FloatType);
		BEGIN
		END VisitFloatType;

		PROCEDURE VisitComplexType*(x: SyntaxTree.ComplexType);
		BEGIN
		END VisitComplexType;

		PROCEDURE VisitByteType*(x: SyntaxTree.ByteType);
		BEGIN
		END VisitByteType;

		PROCEDURE VisitQualifiedType*(x: SyntaxTree.QualifiedType);
		BEGIN
			IF (x.qualifiedIdentifier.prefixSymbol # NIL) THEN
				VisitReference(x.qualifiedIdentifier.prefixSymbol, x.qualifiedIdentifier.position, FALSE, FALSE);
			END;
			IF (x.qualifiedIdentifier.suffixSymbol # NIL) THEN
				VisitReference(x.qualifiedIdentifier.suffixSymbol, x.qualifiedIdentifier.suffixPosition, FALSE, FALSE);
			END;
		END VisitQualifiedType;

		PROCEDURE VisitStringType*(x: SyntaxTree.StringType);
		BEGIN
		END VisitStringType;

		PROCEDURE VisitEnumerationType*(x: SyntaxTree.EnumerationType);
		VAR e: SyntaxTree.Constant; first: BOOLEAN;
		BEGIN
			IF x.enumerationBase # NIL THEN
					Type(x.enumerationBase);
			END;
			e := x.enumerationScope.firstConstant; first := TRUE;
			WHILE (e # NIL) DO
				VisitConstant(e);
				e := e.nextConstant;
			END;
		END VisitEnumerationType;

		PROCEDURE VisitRangeType*(x: SyntaxTree.RangeType);
		END VisitRangeType;

		PROCEDURE VisitArrayType*(x: SyntaxTree.ArrayType);
		BEGIN
			IF x.length # NIL THEN Expression(x.length) END;
			Type(x.arrayBase);
		END VisitArrayType;

		PROCEDURE VisitNilType*(x: SyntaxTree.NilType);
		BEGIN
		END VisitNilType;

		PROCEDURE VisitAddressType*(x: SyntaxTree.AddressType);
		BEGIN
		END VisitAddressType;

		PROCEDURE VisitObjectType*(x: SyntaxTree.ObjectType);
		BEGIN
		END VisitObjectType;

		PROCEDURE VisitAnyType*(x: SyntaxTree.AnyType);
		BEGIN
		END VisitAnyType;

		PROCEDURE VisitAnyRecordType*(x: SyntaxTree.AnyRecordType);
		BEGIN
		END VisitAnyRecordType;

		PROCEDURE VisitMathArrayType*(x: SyntaxTree.MathArrayType);
		BEGIN
			IF x.form # SyntaxTree.Tensor THEN 
				IF x.length = NIL THEN
				ELSE
					Expression(x.length);
				END;
				WHILE(x.arrayBase # NIL) & (x.arrayBase IS SyntaxTree.MathArrayType) DO
					x := x.arrayBase(SyntaxTree.MathArrayType);
					IF x.length = NIL THEN
					ELSE
						Expression(x.length);
					END;
				END;
			END;
			IF x.arrayBase # NIL THEN
				Type(x.arrayBase);
			END;
		END VisitMathArrayType;

		PROCEDURE VisitPointerType*(x: SyntaxTree.PointerType);
		VAR pointerBase: SyntaxTree.Type;
		BEGIN
			IF x.pointerBase # NIL THEN
				pointerBase := x.pointerBase;
				IF x.isHidden THEN
					Type(x.pointerBase);
				ELSIF (pointerBase IS SyntaxTree.RecordType) & (pointerBase(SyntaxTree.RecordType).isObject) THEN
					VisitRecordType(pointerBase(SyntaxTree.RecordType))
				ELSE
					Type(x.pointerBase);
				END;
			END;
		END VisitPointerType;

		PROCEDURE VisitPortType*(x: SyntaxTree.PortType);
		BEGIN
			IF x.sizeExpression # NIL THEN
				Expression(x.sizeExpression);
			END;
		END VisitPortType;

		PROCEDURE VisitCellType*(x: SyntaxTree.CellType);
		BEGIN
			Modifiers(x.modifiers);
			visitor.VisitIncreaseOutlineScope(FALSE);
			IF x.firstParameter # NIL THEN ParameterList(x.firstParameter) END;
			Scope(x.cellScope);
			IF (x.cellScope IS SyntaxTree.CellScope) & (x.cellScope(SyntaxTree.CellScope).bodyProcedure # NIL) THEN
				Body(x.cellScope(SyntaxTree.CellScope).bodyProcedure.procedureScope.body)
			END;
			visitor.VisitDecreaseOutlineScope(FALSE, -1, -1, Basic.invalidPosition);
		END VisitCellType;

		PROCEDURE VisitRecordType*(x: SyntaxTree.RecordType);
		BEGIN
			visitor.VisitIncreaseOutlineScope(x.isObject & (x.endRepeatPosition.end # -1));
			IF ~x.isObject & (x.modifiers # NIL) THEN
				Modifiers(x.modifiers);
			END;
			IF (x.baseType # NIL)  THEN
				IF (x.baseType IS SyntaxTree.RecordType) & (x.baseType(SyntaxTree.RecordType).pointerType # NIL) THEN
					Type(x.baseType(SyntaxTree.RecordType).pointerType)
				ELSE
					Type(x.baseType);
				END;
			END;
			IF x.isObject THEN
				Scope(x.recordScope);
				IF (x.recordScope.bodyProcedure # NIL) THEN
					Body(x.recordScope.bodyProcedure.procedureScope.body)
				END;
				IF x.endRepeatPosition.start # -1 THEN
					visitor.VisitLocalReference(LSPScannerMetadata.SyntaxElementKind.Type, x.endRepeatPosition, x.position, FALSE, TRUE, FALSE);
				END;
			ELSE
				VariableList(x.recordScope.firstVariable, TRUE);
				IF x.recordScope.procedures # NIL THEN ProcedureList(x.recordScope.procedures) END;
			END;
			visitor.VisitDecreaseOutlineScope(x.isObject & (x.endRepeatPosition.end # -1), x.position.start, x.endRepeatPosition.end, Basic.invalidPosition);
		END VisitRecordType;

		PROCEDURE VisitProcedureType*(x: SyntaxTree.ProcedureType);
		BEGIN
			visitor.VisitIncreaseOutlineScope(FALSE);

			IF (x.firstParameter # NIL) THEN
				ParameterList(x.firstParameter)
			END;
			IF x.returnType # NIL THEN
				Type(x.returnType)
			END;
			visitor.VisitDecreaseOutlineScope(FALSE, -1, -1, Basic.invalidPosition);
		END VisitProcedureType;

		(*** expressions ****)

		PROCEDURE ExpressionList(x: SyntaxTree.ExpressionList);
		VAR i: SIZE;  expression: SyntaxTree.Expression;
		BEGIN
			FOR i := 0 TO x.Length() - 1 DO
				expression := x.GetExpression( i );  Expression(expression);
			END;
		END ExpressionList;

		PROCEDURE Expression*(x: SyntaxTree.Expression);
		BEGIN
			IF x # NIL THEN
				VExpression(x);
			END;
		END Expression;

		PROCEDURE VisitExpression*(x: SyntaxTree.Expression);
		BEGIN
		END VisitExpression;

		PROCEDURE VisitSet*(x: SyntaxTree.Set);
		BEGIN
			ExpressionList(x.elements);
		END VisitSet;

		PROCEDURE VisitMathArrayExpression*(x: SyntaxTree.MathArrayExpression);
		BEGIN
			ExpressionList(x.elements);
		END VisitMathArrayExpression;

		PROCEDURE VisitUnaryExpression*(x: SyntaxTree.UnaryExpression);
		BEGIN
			Expression(x.left);
		END VisitUnaryExpression;

		PROCEDURE VisitBinaryExpression*(x: SyntaxTree.BinaryExpression);
		BEGIN
			Expression(x.left);
			Expression(x.right);
		END VisitBinaryExpression;

		PROCEDURE VisitRangeExpression*(x: SyntaxTree.RangeExpression);
		BEGIN
			IF ~x.missingFirst THEN Expression(x.first) END;
			IF ~x.missingLast THEN Expression(x.last) END;
			IF ~x.missingStep THEN Expression(x.step) END
		END VisitRangeExpression;
		
		PROCEDURE VisitConditionalExpression*(x: SyntaxTree.ConditionalExpression);
		BEGIN
			Expression(x.expression);
			Expression(x.condition);
			Expression(x.alternative);
		END VisitConditionalExpression;
		
		PROCEDURE VisitTensorRangeExpression*(x: SyntaxTree.TensorRangeExpression);
		BEGIN
		END VisitTensorRangeExpression;

		PROCEDURE VisitConversion*(x: SyntaxTree.Conversion);
		BEGIN
			IF x.typeExpression # NIL THEN Expression(x.typeExpression) END;
			Expression(x.expression);
		END VisitConversion;

		PROCEDURE VisitDesignator*(x: SyntaxTree.Designator);
		BEGIN
		END VisitDesignator;

		PROCEDURE VisitIdentifierDesignator*(x: SyntaxTree.IdentifierDesignator);
		BEGIN
		END VisitIdentifierDesignator;

		PROCEDURE VisitSelectorDesignator*(x: SyntaxTree.SelectorDesignator);
		VAR wasAssigning: BOOLEAN;
		BEGIN 
			wasAssigning := assigning; assigning := FALSE;
			Expression(x.left);
			assigning := wasAssigning;
		END VisitSelectorDesignator;

		PROCEDURE VisitBracketDesignator*(x: SyntaxTree.BracketDesignator);
			VAR wasAssigning: BOOLEAN;
		BEGIN
			Expression(x.left);
			wasAssigning := assigning;
			assigning := FALSE;
			ExpressionList(x.parameters);
			assigning := wasAssigning;
		END VisitBracketDesignator;

		PROCEDURE VisitParameterDesignator*(x: SyntaxTree.ParameterDesignator);
		BEGIN
			Expression(x.left);
			ExpressionList(x.parameters);
		END VisitParameterDesignator;

		PROCEDURE VisitIndexDesignator*(x: SyntaxTree.IndexDesignator);
		BEGIN
			Expression(x.left);
			ExpressionList(x.parameters);
		END VisitIndexDesignator;

		PROCEDURE VisitArrowDesignator*(x: SyntaxTree.ArrowDesignator);
		BEGIN
			Expression(x.left);
		END VisitArrowDesignator;

		PROCEDURE VisitSymbolDesignator*(x: SyntaxTree.SymbolDesignator);
		BEGIN
			IF (x.left # NIL) THEN
				Expression(x.left);
			END;
			VisitReference(x.symbol, x.position, x.assignable, FALSE);
		END VisitSymbolDesignator;

		PROCEDURE VisitSupercallDesignator*(x: SyntaxTree.SupercallDesignator);
		BEGIN
			Expression(x.left);
		END VisitSupercallDesignator;

		PROCEDURE VisitSelfDesignator*(x: SyntaxTree.SelfDesignator);
		BEGIN
		END VisitSelfDesignator;

		PROCEDURE VisitResultDesignator*(x: SyntaxTree.ResultDesignator);
		BEGIN
		END VisitResultDesignator;

		PROCEDURE VisitDereferenceDesignator*(x: SyntaxTree.DereferenceDesignator);
		BEGIN
			Expression(x.left);
		END VisitDereferenceDesignator;

		PROCEDURE VisitTypeGuardDesignator*(x: SyntaxTree.TypeGuardDesignator);
		BEGIN
			Expression(x.left);
			IF x.typeExpression # NIL THEN Expression(x.typeExpression) ELSE Type(x.type) END;
		END VisitTypeGuardDesignator;

		PROCEDURE VisitProcedureCallDesignator*(x: SyntaxTree.ProcedureCallDesignator);
		BEGIN
			Expression(x.left); ExpressionList(x.parameters);
		END VisitProcedureCallDesignator;

		PROCEDURE VisitInlineCallDesignator*(x: SyntaxTree.InlineCallDesignator);
		BEGIN
			Scope(x.block.scope);
			VisitStatementBlock(x.block);
		END VisitInlineCallDesignator;

		PROCEDURE GetConstructor(record: SyntaxTree.RecordType): SyntaxTree.Procedure;
		VAR procedure: SyntaxTree.Procedure;
		BEGIN
			procedure := record.recordScope.constructor;
			IF procedure = NIL THEN
				record := SemanticChecker.RecordBase(record);
				IF record # NIL THEN
					procedure := GetConstructor(record)
				END;
			END;
			RETURN procedure;
		END GetConstructor;
		
		PROCEDURE VisitBuiltinCallDesignator*(x: SyntaxTree.BuiltinCallDesignator);
		  VAR type0: SyntaxTree.Type; constructor: SyntaxTree.Procedure; leftVisited: BOOLEAN;
		BEGIN
			leftVisited := FALSE;
			IF (x.id = Global.New) & (x.expression = NIL) THEN
				IF x.returnType # NIL THEN
					type0 := x.returnType;
				ELSIF (x.parameters # NIL) & (x.parameters.Length() > 0) THEN
					type0 := x.parameters.GetExpression(0).type;
				ELSE
					type0 := NIL;
				END;
				IF type0 # NIL THEN type0 := type0.resolvedType END;
				IF (type0 # NIL) & (type0 IS SyntaxTree.PointerType) THEN
					type0 := type0(SyntaxTree.PointerType).pointerBase.resolvedType;
				END;
				IF (type0 # NIL) & (type0 IS SyntaxTree.RecordType) THEN
					constructor := GetConstructor(type0(SyntaxTree.RecordType));
					IF constructor # NIL THEN
						VisitReference(constructor, x.left.position, FALSE, FALSE);
						leftVisited := TRUE;
					END;
				END;
			END;
			IF ~leftVisited & (x.left # NIL) THEN
				Expression(x.left);
			ELSE
				CASE x.id OF
				Global.New:
					IF x.expression # NIL THEN
						Expression(x.expression);
					END;
				ELSE
				END;
			END;
			IF x.expression = NIL THEN
				ExpressionList(x.parameters);
			END;
		END VisitBuiltinCallDesignator;

		PROCEDURE VisitValue*(x: SyntaxTree.Value);
		BEGIN
		END VisitValue;

		PROCEDURE VisitBooleanValue*(x: SyntaxTree.BooleanValue);
		BEGIN
		END VisitBooleanValue;

		PROCEDURE VisitIntegerValue*(x: SyntaxTree.IntegerValue);
		BEGIN
		END VisitIntegerValue;

		PROCEDURE VisitCharacterValue*(x: SyntaxTree.CharacterValue);
		BEGIN
		END VisitCharacterValue;

		PROCEDURE VisitSetValue*(x: SyntaxTree.SetValue);
		BEGIN
		END VisitSetValue;

		PROCEDURE VisitMathArrayValue*(x: SyntaxTree.MathArrayValue);
		BEGIN
			VisitMathArrayExpression(x.array);
		END VisitMathArrayValue;

		PROCEDURE VisitRealValue*(x: SyntaxTree.RealValue);
		BEGIN
		END VisitRealValue;

		PROCEDURE VisitComplexValue*(x: SyntaxTree.ComplexValue);
		BEGIN
		END VisitComplexValue;

		PROCEDURE VisitStringValue*(x: SyntaxTree.StringValue);
		BEGIN
		END VisitStringValue;

		PROCEDURE VisitNilValue*(x: SyntaxTree.NilValue);
		BEGIN
		END VisitNilValue;

		PROCEDURE VisitEnumerationValue*(x: SyntaxTree.EnumerationValue);
		BEGIN
		END VisitEnumerationValue;

		PROCEDURE VisitProcedureValue*(x: SyntaxTree.ProcedureValue);
		BEGIN
		END VisitProcedureValue;

		PROCEDURE VisitDefinition(kind: LSPScannerMetadata.SyntaxElementKind; x: SyntaxTree.Symbol; removableIfUnused: BOOLEAN);
			VAR exported: BOOLEAN;
		BEGIN
			exported := (x.access * {SyntaxTree.ProtectedRead, SyntaxTree.ProtectedWrite, SyntaxTree.PublicRead, SyntaxTree.PublicWrite} # {});
			visitor.VisitDefinition(kind, x.position, x.exportPos, exported, removableIfUnused);
		END VisitDefinition;

		PROCEDURE VisitReference(symbol: SyntaxTree.Symbol; position: Basic.Position; assignable, overriding: BOOLEAN);
			VAR str : Scanner.IdentifierString; writtenTo: BOOLEAN;
		BEGIN
			IF (symbol.scope # NIL) THEN
				Global.GetModuleName(symbol.scope.ownerModule, str);
				writtenTo := assignable & (assigning OR ((symbol IS SyntaxTree.Parameter) & (symbol(SyntaxTree.Parameter).kind = SyntaxTree.VarParameter)));
				IF (symbol.scope.ownerModule.context = moduleContext) & (symbol.scope.ownerModule.name = moduleName) THEN
					visitor.VisitLocalReference(MapKind(symbol), position, symbol.position, writtenTo, FALSE, overriding);
				ELSE
					visitor.VisitImportedReference(MapKind(symbol), position, SHORT(symbol.position.end), symbol.scope.ownerModule.name, symbol.scope.ownerModule.context, writtenTo, overriding);
				END
			END;
		END VisitReference;

		PROCEDURE VisitSymbol(x: SyntaxTree.Symbol);
		BEGIN
		END VisitSymbol;

		PROCEDURE VisitTypeDeclaration(x: SyntaxTree.TypeDeclaration);
		BEGIN
			IF x.access # SyntaxTree.Hidden THEN
				VisitDefinition(LSPScannerMetadata.SyntaxElementKind.Type, x, TRUE);
				Type(x.declaredType);
			END
		END VisitTypeDeclaration;

		PROCEDURE TypeDeclarationList(x: SyntaxTree.TypeDeclaration);
		BEGIN
			WHILE(x # NIL) DO
				VisitTypeDeclaration(x);
				x := x.nextTypeDeclaration;
			END;
		END TypeDeclarationList;

		PROCEDURE VisitConstant*(x: SyntaxTree.Constant);
		BEGIN
			VisitDefinition(LSPScannerMetadata.SyntaxElementKind.Constant, x, TRUE);
			Expression(x.value);
		END VisitConstant;

		PROCEDURE ConstantList(x: SyntaxTree.Constant);
		BEGIN
			WHILE(x # NIL) DO
				VisitConstant(x);
				x := x.nextConstant;
			END;
		END ConstantList;

		PROCEDURE VisitVariable2*(x: SyntaxTree.Variable; withType, recordField: BOOLEAN);
			VAR kind: LSPScannerMetadata.SyntaxElementKind;
		BEGIN
			IF recordField THEN kind := LSPScannerMetadata.SyntaxElementKind.RecordField ELSE kind := LSPScannerMetadata.SyntaxElementKind.Variable END;
			VisitDefinition(kind, x, TRUE);
			IF x.initializer # NIL THEN
				Expression (x.initializer);
			END;
			IF x.extern # NIL THEN
				Expression(x.extern);
			END;
			IF withType & IsProperType(x.type) THEN
				Type(x.type);
			END;
		END VisitVariable2;

		PROCEDURE VisitVariable*(x: SyntaxTree.Variable);
		BEGIN VisitVariable2(x, TRUE, FALSE);
		END VisitVariable;

		PROCEDURE VariableList(x: SyntaxTree.Variable; recordField: BOOLEAN);
		BEGIN
			WHILE(x # NIL) DO
				VisitVariable2(x, ~((x.nextVariable # NIL) & (x.nextVariable.type = x.type)), recordField);
				x := x.nextVariable;
			END
		END VariableList;

		PROCEDURE AliasList(x: SyntaxTree.Alias);
		BEGIN
			WHILE(x # NIL) DO
				VisitDefinition(LSPScannerMetadata.SyntaxElementKind.Alias, x, TRUE);
				IF x.expression # NIL THEN
					Expression (x.expression);
				END;
				Type(x.type);
				x := x.nextAlias;;
			END
		END AliasList;

		PROCEDURE VisitParameter2*(x: SyntaxTree.Parameter; withType: BOOLEAN);
		BEGIN
			VisitDefinition(MapKind(x), x, FALSE);
			IF x.defaultValue # NIL THEN
				Expression(x.defaultValue);
			END;
			IF withType THEN 
			Type(x.type);
			END
		END VisitParameter2;

		PROCEDURE VisitParameter*(x: SyntaxTree.Parameter);
		BEGIN VisitParameter2(x, TRUE);
		END VisitParameter;

		PROCEDURE ParameterList*(x: SyntaxTree.Parameter);
		BEGIN
			WHILE(x # NIL) DO
				VisitParameter2(x, ~((x.nextParameter # NIL) & (x.nextParameter.type = x.type) & (x.nextParameter.kind = x.kind)));
				x := x.nextParameter;
			END;
		END ParameterList;

		PROCEDURE VisitProperty*(x: SyntaxTree.Property);
		BEGIN
			VisitDefinition(LSPScannerMetadata.SyntaxElementKind.Property, x, TRUE);
			IF x.initializer # NIL THEN
				Expression (x.initializer);
			END;
			IF x.extern # NIL THEN
				Expression(x.extern);
			END;
			IF IsProperType(x.type) THEN
				Type(x.type);
			END;
			IF x.value # NIL THEN
				Expression (x.value);
			END;
		END VisitProperty;

		PROCEDURE VisitProcedure*(x: SyntaxTree.Procedure);
		VAR type: SyntaxTree.ProcedureType;
		BEGIN
			IF x.hasNameIdentifier THEN
				VisitDefinition(LSPScannerMetadata.SyntaxElementKind.Procedure, x, TRUE);
				IF (x.endRepeatPosition.start # -1) THEN
					visitor.VisitLocalReference(LSPScannerMetadata.SyntaxElementKind.Procedure, x.endRepeatPosition, x.position, FALSE, TRUE, FALSE);
				END;
				IF x.super # NIL THEN
					VisitReference(x.super, x.position, FALSE, TRUE);
				END;
			END;
			visitor.VisitIncreaseOutlineScope(x.hasNameIdentifier & (x.procRangeStart # -1) & (x.procRangeEnd # -1));
			type := x.type(SyntaxTree.ProcedureType);
			IF (type.selfParameter # NIL) & ~(type.selfParameter.name = "@Self") THEN
				ParameterList(type.selfParameter);
			END;
			ParameterList(type.firstParameter);
			IF type.returnType # NIL THEN
				Type(type.returnType);
			END;
			IF x.extern = NIL THEN
				ProcedureScope(x.procedureScope);
			ELSE
				Expression(x.extern);
			END;
			visitor.VisitDecreaseOutlineScope(x.hasNameIdentifier & (x.procRangeStart # -1) & (x.procRangeEnd # -1), x.procRangeStart, x.procRangeEnd, x.position);
		END VisitProcedure;

		PROCEDURE VisitAlias*(x: SyntaxTree.Alias);
		BEGIN 
			VisitDefinition(LSPScannerMetadata.SyntaxElementKind.Alias, x, TRUE);
			IF x.expression # NIL THEN
				Expression (x.expression);
			END;
		END VisitAlias;

		PROCEDURE VisitOperator*(x: SyntaxTree.Operator);
		BEGIN VisitProcedure(x);
		END VisitOperator;

		PROCEDURE ProcedureList(list: SyntaxTree.ProcedureList);
		VAR x: SyntaxTree.Procedure; i: SIZE;
		BEGIN
			FOR i := 0 TO list.Length()-1 DO
				x := list.GetProcedure(i);
				VisitProcedure(x);
			END
		END ProcedureList;

		PROCEDURE VisitImport*(x: SyntaxTree.Import);
		VAR i: SIZE; kind: LSPScannerMetadata.SyntaxElementKind;
		BEGIN
            IF x.modulePosition.end = x.position.end THEN
				kind := LSPScannerMetadata.SyntaxElementKind.ModuleImport;
			ELSE
				kind := LSPScannerMetadata.SyntaxElementKind.ModuleAliasDef;
			END;
			visitor.VisitDefinition(kind, x.position, Basic.invalidPosition, FALSE, TRUE);
			visitor.VisitImportedReference(LSPScannerMetadata.SyntaxElementKind.ModuleImport, x.modulePosition, -1, x.moduleName, x.context, TRUE, FALSE);
			IF x.parameters # NIL THEN
				FOR i := 0 TO x.parameters.Length()-1 DO
					Expression(x.parameters.GetExpression(i));
				END;
			END;
		END VisitImport;

		PROCEDURE ImportList(x: SyntaxTree.Import);
		BEGIN
			WHILE(x # NIL) DO
				VisitImport(x);
				x := x.nextImport;
			END;
		END ImportList;

		PROCEDURE VisitBuiltin*(x: SyntaxTree.Builtin);
		BEGIN
		END VisitBuiltin;

		PROCEDURE BuiltinList(x: SyntaxTree.Builtin);
		BEGIN
			WHILE(x # NIL) DO
				VisitBuiltin(x);
				x := x.nextBuiltin;
			END;
		END BuiltinList;

		(*** scopes ****)

		PROCEDURE Scope*(x: SyntaxTree.Scope);
		BEGIN
			IF x IS SyntaxTree.CellScope THEN
				IF x(SyntaxTree.CellScope).firstImport # NIL THEN ImportList(x(SyntaxTree.CellScope).firstImport) END;
			ELSE
			END;
			IF x.firstConstant # NIL THEN ConstantList(x.firstConstant); END;
			IF x.firstTypeDeclaration # NIL THEN TypeDeclarationList(x.firstTypeDeclaration); END;
			IF x.firstVariable # NIL THEN  VariableList(x.firstVariable, FALSE);  END;
			IF x.firstAlias # NIL THEN AliasList(x.firstAlias) END;
			IF x.procedures # NIL THEN ProcedureList(x.procedures) END;
		END Scope;

		PROCEDURE ProcedureScope(x: SyntaxTree.ProcedureScope);
		BEGIN
			Scope(x);
			IF (x.body # NIL) THEN Body(x.body) END;
		END ProcedureScope;

		PROCEDURE Statement*(x: SyntaxTree.Statement);
		BEGIN
			IF x # NIL THEN
				VStatement(x);
			END
		END Statement;

		PROCEDURE StatementSequence*(x: SyntaxTree.StatementSequence);
		VAR statement: SyntaxTree.Statement;  i: SIZE;
		BEGIN
			FOR i := 0 TO x.Length() - 1 DO
				statement := x.GetStatement( i );
				Statement(statement)
			END;
		END StatementSequence;

		PROCEDURE VisitStatement*(x: SyntaxTree.Statement);
		BEGIN
		END VisitStatement;

		PROCEDURE VisitProcedureCallStatement*(x: SyntaxTree.ProcedureCallStatement);
		BEGIN
			Expression(x.call);
		END VisitProcedureCallStatement;

		PROCEDURE VisitAssignment*(x: SyntaxTree.Assignment);
		BEGIN 
			assigning := TRUE;
			Expression(x.left); 
			assigning := FALSE;
			Expression(x.right);
		END VisitAssignment;

		PROCEDURE VisitDeclarationStatement*(x: SyntaxTree.DeclarationStatement);
		BEGIN (* is part of scope *)			
		END VisitDeclarationStatement;

		PROCEDURE VisitCommunicationStatement*(x: SyntaxTree.CommunicationStatement);
		BEGIN
			Expression(x.left);
			Expression(x.right);
		END VisitCommunicationStatement;

		PROCEDURE IfPart(x: SyntaxTree.IfPart);
		BEGIN
			Expression(x.condition);
			StatementSequence(x.statements);
		END IfPart;

		PROCEDURE VisitIfStatement*(x: SyntaxTree.IfStatement);
		VAR i: SIZE;  elsif: SyntaxTree.IfPart;
		BEGIN
			IfPart(x.ifPart);
			FOR i := 0 TO x.ElsifParts() - 1 DO
				elsif := x.GetElsifPart( i );
				IfPart(elsif);
			END;
			IF x.elsePart # NIL THEN
				StatementSequence(x.elsePart);
			END;
		END VisitIfStatement;

		PROCEDURE WithPart(x: SyntaxTree.WithPart);
		BEGIN
			Type(x.type);
			StatementSequence(x.statements);
		END WithPart;

		PROCEDURE VisitWithStatement*(x: SyntaxTree.WithStatement);
		VAR i: SIZE;
		BEGIN
			Expression(x.variable);
			WithPart(x.GetWithPart(0));
			FOR i := 1 TO x.WithParts()-1 DO
				WithPart(x.GetWithPart(i));
			END;
			IF x.elsePart # NIL THEN
				StatementSequence(x.elsePart);
			END;
		END VisitWithStatement;

		PROCEDURE CasePart(x: SyntaxTree.CasePart);
		BEGIN
			ExpressionList(x.elements);
			StatementSequence(x.statements);
		END CasePart;

		PROCEDURE VisitCaseStatement*(x: SyntaxTree.CaseStatement);
		VAR i: SIZE;  case: SyntaxTree.CasePart;
		BEGIN
			Expression(x.variable);
			FOR i := 0 TO x.CaseParts() - 1 DO
				case := x.GetCasePart( i );
				CasePart(case);
			END;
			IF x.elsePart # NIL THEN
				StatementSequence(x.elsePart);
			END;
		END VisitCaseStatement;

		PROCEDURE VisitWhileStatement*(x: SyntaxTree.WhileStatement);
		BEGIN
			Expression(x.condition);
			StatementSequence(x.statements);
		END VisitWhileStatement;

		PROCEDURE VisitRepeatStatement*(x: SyntaxTree.RepeatStatement);
		BEGIN
			StatementSequence(x.statements);
			Expression(x.condition);
		END VisitRepeatStatement;

		PROCEDURE VisitForStatement*(x: SyntaxTree.ForStatement);
			VAR wasAssigning: BOOLEAN;
		BEGIN
			wasAssigning := assigning; assigning := TRUE;
			Expression(x.variable);
			assigning := wasAssigning;
			Expression(x.from);
			Expression(x.to);
			IF x.by # NIL THEN
				Expression(x.by);
			END;
			StatementSequence(x.statements);
		END VisitForStatement;

		PROCEDURE VisitLoopStatement*(x: SyntaxTree.LoopStatement);
		BEGIN StatementSequence(x.statements); 
		END VisitLoopStatement;

		PROCEDURE VisitExitableBlock*(x: SyntaxTree.ExitableBlock);
		BEGIN
			StatementSequence(x.statements);
		END VisitExitableBlock;

		PROCEDURE VisitExitStatement*(x: SyntaxTree.ExitStatement);
		BEGIN
		END VisitExitStatement;

		PROCEDURE VisitReturnStatement*(x: SyntaxTree.ReturnStatement);
		BEGIN
			IF x.returnValue # NIL THEN Expression(x.returnValue) END
		END VisitReturnStatement;

		PROCEDURE VisitAwaitStatement*(x: SyntaxTree.AwaitStatement);
		BEGIN
			Expression(x.condition);
		END VisitAwaitStatement;

		PROCEDURE Modifiers(x: SyntaxTree.Modifier);
		BEGIN
			WHILE x # NIL DO
				IF x.expression # NIL THEN
					Expression(x.expression);
				END;
				x := x.nextModifier;
			END;
		END Modifiers;

		PROCEDURE VisitStatementBlock*(x: SyntaxTree.StatementBlock);
		BEGIN
			Modifiers(x.blockModifiers);
			IF x.statements # NIL THEN StatementSequence(x.statements) END;
		END VisitStatementBlock;

		PROCEDURE VisitCode*(x: SyntaxTree.Code);
		BEGIN
		END VisitCode;

		PROCEDURE Body(x: SyntaxTree.Body);
		VAR
		BEGIN
			IF x.code # NIL THEN
				VisitCode(x.code);
			ELSE
				Modifiers(x.blockModifiers);
				IF x.statements # NIL THEN
					StatementSequence(x.statements);
				END;
				IF x.finally # NIL THEN
					StatementSequence(x.finally);
				END;
			END;
		END Body;

		PROCEDURE VisitModule*(x: SyntaxTree.Module);
		BEGIN
			IF x # NIL THEN
				moduleName := x.name;
				moduleContext := x.context;
				VisitDefinition(LSPScannerMetadata.SyntaxElementKind.ThisModule, x, TRUE);
				IF (x.type IS SyntaxTree.CellType) & (x.type(SyntaxTree.CellType).firstParameter # NIL) THEN (* for actors *)
					ParameterList(x.type(SyntaxTree.CellType).firstParameter);
				END;
				IF x.moduleScope.firstImport # NIL THEN
					ImportList(x.moduleScope.firstImport)
				END;
				Scope(x.moduleScope);
				IF x.moduleScope.firstBuiltin # NIL THEN
					BuiltinList(x.moduleScope.firstBuiltin)
				END;
				IF (x.moduleScope.bodyProcedure # NIL) & (x.moduleScope.bodyProcedure.procedureScope.body # NIL) THEN
					Body(x.moduleScope.bodyProcedure.procedureScope.body)
				END;
				IF (x.endRepeatPosition.start # -1) THEN
					visitor.VisitLocalReference(LSPScannerMetadata.SyntaxElementKind.ThisModule, x.endRepeatPosition, x.position, FALSE, TRUE, FALSE);
				END
			END
		END VisitModule;
	END IdentifierVisitor;

	SymbolMappingVisitor = OBJECT
	VAR
        visitor: LSPSymbolMappingVisitor;

        PROCEDURE &Init*(v: LSPSymbolMappingVisitor);
        BEGIN visitor := v;
        END Init;

        
		PROCEDURE VType*(xs, xm: SyntaxTree.Type);
		BEGIN
			WITH xs: SyntaxTree.ProcedureType DO VisitProcedureType(xs, xm(SyntaxTree.ProcedureType))
				|SyntaxTree.CellType DO VisitCellType(xs, xm(SyntaxTree.CellType))
				|SyntaxTree.RecordType DO VisitRecordType(xs, xm(SyntaxTree.RecordType))
				|SyntaxTree.PointerType DO VisitPointerType(xs, xm(SyntaxTree.PointerType))
				|SyntaxTree.MathArrayType DO VisitMathArrayType(xs, xm(SyntaxTree.MathArrayType))
				|SyntaxTree.ArrayType DO VisitArrayType(xs, xm(SyntaxTree.ArrayType))
				|SyntaxTree.EnumerationType DO VisitEnumerationType(xs, xm(SyntaxTree.EnumerationType))
			ELSE
			END;
		END VType;

		PROCEDURE VSymbol*(xs, xm: SyntaxTree.Symbol);
		BEGIN
			WITH xs:
			SyntaxTree.Module DO VisitModule(xs, xm( SyntaxTree.Module))
			| SyntaxTree.TypeDeclaration DO VisitTypeDeclaration(xs, xm( SyntaxTree.TypeDeclaration))
			| SyntaxTree.Parameter DO VisitParameter(xs, xm(SyntaxTree.Parameter))
			| SyntaxTree.Procedure DO VisitProcedure(xs, xm(SyntaxTree.Procedure))
			| SyntaxTree.Constant DO VisitSymbolMatch(xs, xm)
			| SyntaxTree.Property DO VisitSymbolMatch(xs, xm)
			| SyntaxTree.Variable DO VisitSymbolMatch(xs, xm)
			| SyntaxTree.Alias DO VisitSymbolMatch(xs, xm)
			ELSE
			END;
		END VSymbol;

		PROCEDURE Type*(xs, xm: SyntaxTree.Type);
		BEGIN
			IF xs # NIL THEN
				VType(xs, xm);
			END;
		END Type;

		PROCEDURE VisitEnumerationType*(xs, xm: SyntaxTree.EnumerationType);
		VAR es, em: SyntaxTree.Constant;
		BEGIN
			IF xs.enumerationBase # NIL THEN
					Type(xs.enumerationBase, xm.enumerationBase);
			END;
			es := xs.enumerationScope.firstConstant; 
			em := xs.enumerationScope.firstConstant;
			WHILE (es # NIL) DO
				WHILE (em # NIL) & (em.name # es.name) DO em := em.nextConstant END;
				IF (em # NIL) THEN VisitSymbolMatch(es, em); em := em.nextConstant END;
				es := es.nextConstant; 
			END;
		END VisitEnumerationType;

		PROCEDURE VisitArrayType*(xs, xm: SyntaxTree.ArrayType);
		BEGIN
			Type(xs.arrayBase, xm.arrayBase);
		END VisitArrayType;

		PROCEDURE VisitMathArrayType*(xs, xm: SyntaxTree.MathArrayType);
		BEGIN
			IF xs.arrayBase # NIL THEN
				Type(xs.arrayBase, xm.arrayBase);
			END;
		END VisitMathArrayType;

		PROCEDURE VisitPointerType*(xs, xm: SyntaxTree.PointerType);
		VAR pointerBase: SyntaxTree.Type;
		BEGIN
			IF xs.pointerBase # NIL THEN
				pointerBase := xs.pointerBase;
				IF xs.isHidden THEN
					Type(xs.pointerBase, xm.pointerBase);
				ELSIF (pointerBase IS SyntaxTree.RecordType) & (pointerBase(SyntaxTree.RecordType).isObject) THEN
					VisitRecordType(xs.pointerBase(SyntaxTree.RecordType), xm.pointerBase(SyntaxTree.RecordType))
				ELSE
					Type(xs.pointerBase, xm.pointerBase);
				END;
			END;
		END VisitPointerType;

		PROCEDURE VisitCellType*(xs, xm: SyntaxTree.CellType);
		BEGIN
			IF xs.firstParameter # NIL THEN ParameterList(xs.firstParameter, xm.firstParameter) END;
			Scope(xs.cellScope, xm.cellScope);
		END VisitCellType;

		PROCEDURE VisitRecordType*(xs, xm: SyntaxTree.RecordType);
		BEGIN
			IF (xs.baseType # NIL)  THEN
				IF (xs.baseType IS SyntaxTree.RecordType) & (xs.baseType(SyntaxTree.RecordType).pointerType # NIL) THEN
					Type(xs.baseType(SyntaxTree.RecordType).pointerType, xm.baseType(SyntaxTree.RecordType).pointerType);
				ELSE
					Type(xs.baseType, xm.baseType);
				END;
			END;
			IF xs.isObject THEN
				Scope(xs.recordScope, xm.recordScope);
			ELSE
				VariableList(xs.recordScope.firstVariable, xm.recordScope.firstVariable);
				IF xs.recordScope.procedures # NIL THEN ProcedureList(xs.recordScope.procedures, xm.recordScope.procedures) END;
			END;
		END VisitRecordType;

		PROCEDURE VisitProcedureType*(xs, xm: SyntaxTree.ProcedureType);
		BEGIN
			IF (xs.firstParameter # NIL) THEN
				ParameterList(xs.firstParameter, xm.firstParameter)
			END;
			IF xs.returnType # NIL THEN
				Type(xs.returnType, xm.returnType)
			END;
		END VisitProcedureType;

		PROCEDURE VisitSymbolMatch(xs, xm: SyntaxTree.Symbol);
		BEGIN
			visitor.VisitSymbolMatch(xm.position, SHORT(xs.position.end));
		END VisitSymbolMatch;

		PROCEDURE VisitTypeDeclaration(xs, xm: SyntaxTree.TypeDeclaration);
		BEGIN
			VisitSymbolMatch(xs, xm);
			Type(xs.declaredType, xm.declaredType);
		END VisitTypeDeclaration;

		PROCEDURE TypeDeclarationList(xs, xm: SyntaxTree.TypeDeclaration);
		BEGIN
			WHILE(xs # NIL) DO
				WHILE (xm # NIL) & (xs.name # xm.name) DO xm := xm.nextTypeDeclaration END;
				IF (xm # NIL) THEN VisitTypeDeclaration(xs, xm); xm := xm.nextTypeDeclaration END;
				xs := xs.nextTypeDeclaration;
			END;
		END TypeDeclarationList;

		PROCEDURE ConstantList(xs, xm: SyntaxTree.Constant);
		BEGIN
			WHILE(xs # NIL) DO
				WHILE (xm # NIL) & (xs.name # xm.name) DO xm := xm.nextConstant END;
				IF (xm # NIL) THEN VisitSymbolMatch(xs, xm); xm := xm.nextConstant END;
				xs := xs.nextConstant;
			END;
		END ConstantList;

		PROCEDURE VariableList(xs, xm: SyntaxTree.Variable);
		BEGIN
			WHILE(xs # NIL) DO
				WHILE (xm # NIL) & (xs.name # xm.name) DO xm := xm.nextVariable END;
				IF (xm # NIL) THEN  VisitSymbolMatch(xs, xm); xm := xm.nextVariable END;
				xs := xs.nextVariable;
			END
		END VariableList;

		PROCEDURE AliasList(xs, xm: SyntaxTree.Alias);
		BEGIN
			WHILE(xs # NIL) DO
				WHILE (xm # NIL) & (xs.name # xm.name) DO xm := xm.nextAlias END;
				IF (xm # NIL) THEN VisitSymbolMatch(xs, xm); Type(xs.type, xm.type); xm := xm.nextAlias END;
				xs := xs.nextAlias;
			END
		END AliasList;

		PROCEDURE VisitParameter*(xs, xm: SyntaxTree.Parameter);
		BEGIN
			VisitSymbolMatch(xs, xm);
   			Type(xs.type, xm.type);
		END VisitParameter;

		PROCEDURE ParameterList*(xs, xm: SyntaxTree.Parameter);
		BEGIN
			WHILE(xs # NIL) DO
				WHILE (xm # NIL) & (xs.name # xm.name) DO xm := xm.nextParameter END;
				IF (xm # NIL) THEN VisitParameter(xs, xm); xm := xm.nextParameter END;
				xs := xs.nextParameter;
			END;
		END ParameterList;

		PROCEDURE VisitProcedure*(xs, xm: SyntaxTree.Procedure);
		VAR ts, tm: SyntaxTree.ProcedureType;
		BEGIN
			IF xs.hasNameIdentifier THEN
				VisitSymbolMatch(xs, xm)
			END;
			ts := xs.type(SyntaxTree.ProcedureType);
			tm := xm.type(SyntaxTree.ProcedureType);
			IF (ts.selfParameter # NIL) & ~(ts.selfParameter.name = "@Self") THEN
				ParameterList(ts.selfParameter, tm.selfParameter);
			END;
			ParameterList(ts.firstParameter, tm.firstParameter);
			IF ts.returnType # NIL THEN
				Type(ts.returnType, tm.returnType);
			END;
			IF xs.extern = NIL THEN
				Scope(xs.procedureScope, xm.procedureScope);
			END;
		END VisitProcedure;

		PROCEDURE ProcedureList(ls, lm: SyntaxTree.ProcedureList);
		VAR xs, xm: SyntaxTree.Procedure; i, j: SIZE;
		BEGIN j := 0;
			FOR i := 0 TO ls.Length()-1 DO
			  IF (lm # NIL) & (j < lm.Length()) THEN
				xs := ls.GetProcedure(i);
				xm := lm.GetProcedure(j);
				WHILE (xm.name # xs.name) & (j < lm.Length() - 1) DO INC(j); xm := lm.GetProcedure(j) END;
				IF (xs.name = xm.name) THEN VisitProcedure(xs, xm); INC(j) END;
				END;
			END
		END ProcedureList;

		(*** scopes ****)

		PROCEDURE Scope*(xs, xm: SyntaxTree.Scope);
		BEGIN
			IF xs.firstConstant # NIL THEN ConstantList(xs.firstConstant, xm.firstConstant); END;
			IF xs.firstTypeDeclaration # NIL THEN TypeDeclarationList(xs.firstTypeDeclaration, xm.firstTypeDeclaration); END;
			IF xs.firstVariable # NIL THEN  VariableList(xs.firstVariable, xm.firstVariable);  END;
			IF xs.firstAlias # NIL THEN AliasList(xs.firstAlias, xm.firstAlias) END;
			IF xs.procedures # NIL THEN ProcedureList(xs.procedures, xm.procedures) END;
		END Scope;

		PROCEDURE VisitModule*(xs, xm: SyntaxTree.Module);
		BEGIN
			IF (xs # NIL) & (xm # NIL) THEN
				VisitSymbolMatch(xs, xm);
				IF (xs.type IS SyntaxTree.CellType) & (xs.type(SyntaxTree.CellType).firstParameter # NIL) THEN
					ParameterList(xs.type(SyntaxTree.CellType).firstParameter, xm.type(SyntaxTree.CellType).firstParameter);
				END;
				Scope(xs.moduleScope, xm.moduleScope);
			END
		END VisitModule;
	END SymbolMappingVisitor;

	PROCEDURE IsProperType(type: SyntaxTree.Type): BOOLEAN;
	BEGIN
		 RETURN (type # NIL) & (~(type IS SyntaxTree.QualifiedType) OR (type(SyntaxTree.QualifiedType).qualifiedIdentifier # NIL));
	END IsProperType;

	PROCEDURE MapKind*(x: SyntaxTree.Symbol) : LSPScannerMetadata.SyntaxElementKind;
		VAR result: LSPScannerMetadata.SyntaxElementKind
	BEGIN
		WITH x:
			SyntaxTree.Parameter DO
				IF x(SyntaxTree.Parameter).kind = SyntaxTree.VarParameter THEN
					result := LSPScannerMetadata.SyntaxElementKind.VariableParameter;
				ELSE
					result := LSPScannerMetadata.SyntaxElementKind.Parameter;
				END;
			| SyntaxTree.Property DO result := LSPScannerMetadata.SyntaxElementKind.Property;
			| SyntaxTree.TypeDeclaration DO  result := LSPScannerMetadata.SyntaxElementKind.Type;
			| SyntaxTree.Constant DO result := LSPScannerMetadata.SyntaxElementKind.Constant;
			| SyntaxTree.Variable DO result := LSPScannerMetadata.SyntaxElementKind.Variable;
			| SyntaxTree.Procedure DO result := LSPScannerMetadata.SyntaxElementKind.Procedure;
			| SyntaxTree.Alias DO result := LSPScannerMetadata.SyntaxElementKind.Alias;
			| SyntaxTree.Import DO result := LSPScannerMetadata.SyntaxElementKind.Module;
			| SyntaxTree.Module DO result := LSPScannerMetadata.SyntaxElementKind.Module;
		ELSE
			result := LSPScannerMetadata.SyntaxElementKind.Undefined;
		END;
	RETURN result
	END MapKind;

	PROCEDURE VisitIdentifiers*(v : LSPIdentifierVisitor; module: SyntaxTree.Module);
        VAR p: IdentifierVisitor;
    BEGIN
        NEW(p, v);
        p.VisitModule(module);
    END VisitIdentifiers;

	PROCEDURE VisitSymbolMapping*(v : LSPSymbolMappingVisitor; symbols, module: SyntaxTree.Module);
        VAR p: SymbolMappingVisitor;
    BEGIN
        NEW(p, v);
        p.VisitModule(symbols, module);
    END VisitSymbolMapping;

END LSPSyntaxTreeWalker.
