MODULE LSPhConstants;

  CONST
    (* instruction codes *)
    GetModuleInfo* = 100; AutoComplete* = 101; ReFormat* = 102; SwitchEmbeddedMode* = 103; Exit* = 104;
     
    (* status codes, where needed *)
    OK* = 0; Invalid* = 1;

    (* answer packets *)
    ModuleName* = 10; ModuleImport* = 11; Error* = 12; SymbolFileChanged* = 13;
    SyntaxElement* = 14; ProcedureStart* = 15; ProcedureEnd* = 16; VarModified* = 17;
    RecordStart* = 18; RecordEnd* = 19; NameExported* = 20; ImportAlias* = 21;
    ProcParamStart* = 22; CallParamStart* = 23; ParamNext*= 24; ParamEnd* = 25;
    ForwardPointer* = 26; ForwardPointerFixup* = 27; DefinitionRepeat* = 28;
    DefinitionUsed* = 29; DeclarationBlockStart* = 30; DeclarationBlockEnd* = 31;
    DefinitionListStart* = 32; DefinitionListValue* = 33; DefinitionListEnd*= 34;
    SymbolFileIndex* = 35; CommandExported* = 36;
    
    (* answer packets / A2 *)
    Warning* = 60; Information* = 61; NameExportedAt* = 62; VarProcParam* = 63;
    RecordEndAsProcedure* = 64; LogPosLength* = 65;

    (* autocomplete answer packets *)
    Completion* = 80; 

    (* reformat answer packets *)
    FormatToken* = 90; FormatTokenUpdate* = 91; IndentNextLine* = 92; OutdentThisLine*= 93;
    OutdentThisLineAndComment* = 94; TokenIsComment* = 95;

    (* syntax elements  *)
    SynOperator* = 1; SynType* = 2; SynKeyword* = 3; SynString* = 4; SynComment* = 5; SynConstant* = 6;
    SynUndefined* = 7; SynModule* = 8; SynVariable* = 9; SynParameter* = 10; SynRecordField* = 11;
    SynProcedure* = 12;

    (* pseudo syntax elements / references *)
    SynOverrideReference* = 13;
  
END LSPhConstants.
