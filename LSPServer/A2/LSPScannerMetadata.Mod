MODULE LSPScannerMetadata;
    IMPORT Scanner := FoxScanner;

    CONST
    	(* whitespace hints *)
        NoSpaceBefore* = 0;
        MaybeSpaceBefore* = 1;
        OneSpaceBefore* = 2;
        OneNewlineBefore* = 3;
        MoreNewlinesBefore*= 4;
        NoSpaceAfter* = 5;
        MaybeSpaceAfter* = 6;
        OneSpaceAfter* = 7;
        DedentOne*=8;
        DedentTwo*=9;
		DedentOneWithComment*=13;
        IndentOne*=10;
        IndentTwo*=11;
		IsComment*=12;

    TYPE
    	SyntaxElementKind* = ENUM
            ThisModule*, ModuleImport*, ModuleAliasDef*,

    		Constant*, Operator*, Type*, Keyword*, String*, Comment*, 
            Undefined*, Module*, Variable*, Parameter*, VariableParameter*, 
		    Property*, RecordField*, Procedure*, Alias*, 
            
            None*
        END;

    VAR
		syntaxKinds: ARRAY Scanner.EndOfText+1 OF SyntaxElementKind;
		formatToken: ARRAY Scanner.LastSubSymbol+1 OF SET;

    PROCEDURE GetSyntaxKind*(symbol: Scanner.Symbol): SyntaxElementKind;
    BEGIN
    RETURN syntaxKinds[symbol]
    END GetSyntaxKind;

    PROCEDURE GetFormatToken*(symbol: Scanner.Symbol): SET;
    BEGIN
    RETURN formatToken[symbol]
    END GetFormatToken;
    
    PROCEDURE Init;
        VAR i: INTEGER;
    BEGIN
		FOR i := 0 TO Scanner.EndOfText DO 
            IF Scanner.keywordSymbols[i] THEN 
                syntaxKinds[i] := SyntaxElementKind.Keyword 
            ELSE
                syntaxKinds[i] := SyntaxElementKind.None; 
            END;
            formatToken[i] := {OneSpaceBefore, OneSpaceAfter}; 
         END;
		syntaxKinds[Scanner.Equal] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.DotEqual] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.Unequal] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.DotUnequal] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.Less] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.DotLess] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.LessEqual] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.DotLessEqual] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.Greater] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.DotGreater] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.GreaterEqual] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.DotGreaterEqual] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.LessLessQ] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.GreaterGreaterQ] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.Questionmarks] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.ExclamationMarks] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.Times] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.TimesTimes] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.DotTimes] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.PlusTimes] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.Slash] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.Backslash] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.DotSlash] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.And] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.Plus] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.Minus] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.Not] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.Number] := SyntaxElementKind.Type;
		syntaxKinds[Scanner.Character] := SyntaxElementKind.Type;
		syntaxKinds[Scanner.String] := SyntaxElementKind.String;
		syntaxKinds[Scanner.Transpose] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.Questionmark] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.ExclamationMark] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.LessLess] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.GreaterGreater] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.Upto] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.Arrow] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.Period] := SyntaxElementKind.Operator;
		syntaxKinds[Scanner.Shortint] := SyntaxElementKind.Type;
		syntaxKinds[Scanner.Integer] := SyntaxElementKind.Type;
		syntaxKinds[Scanner.Longint] := SyntaxElementKind.Type;
		syntaxKinds[Scanner.Hugeint] := SyntaxElementKind.Type;
		syntaxKinds[Scanner.Real] := SyntaxElementKind.Type;
		syntaxKinds[Scanner.Longreal] := SyntaxElementKind.Type;
		syntaxKinds[Scanner.Comment] := SyntaxElementKind.Comment;
		formatToken[Scanner.Not] := {OneSpaceBefore, NoSpaceAfter};
		formatToken[Scanner.LeftParenthesis] := {MaybeSpaceBefore, NoSpaceAfter};
		formatToken[Scanner.LeftBracket] := {MaybeSpaceBefore, NoSpaceAfter};
		formatToken[Scanner.Semicolon] := {NoSpaceBefore, OneSpaceAfter};
		formatToken[Scanner.Transpose] := {MaybeSpaceBefore, MaybeSpaceAfter};
		formatToken[Scanner.RightBracket] := {NoSpaceBefore, MaybeSpaceAfter};
		formatToken[Scanner.RightParenthesis] := {NoSpaceBefore, MaybeSpaceAfter};
		formatToken[Scanner.Period] := {NoSpaceBefore, NoSpaceAfter};
		formatToken[Scanner.Comma] := {NoSpaceBefore, OneSpaceAfter};
		formatToken[Scanner.Colon] := {NoSpaceBefore, OneSpaceAfter};
		formatToken[Scanner.Comment] := {MaybeSpaceBefore, MaybeSpaceAfter, IsComment};
		formatToken[Scanner.EndOfText] := {};

		formatToken[Scanner.Case] := {OneSpaceBefore, OneSpaceAfter, IndentOne};
		formatToken[Scanner.While] := {OneSpaceBefore, OneSpaceAfter, IndentOne};
		formatToken[Scanner.Repeat] := {OneSpaceBefore, OneSpaceAfter, IndentOne};
		formatToken[Scanner.For] := {OneSpaceBefore, OneSpaceAfter, IndentOne};
		formatToken[Scanner.Loop] := {OneSpaceBefore, OneSpaceAfter, IndentOne};
		formatToken[Scanner.With] := {OneSpaceBefore, OneSpaceAfter, IndentOne};
		formatToken[Scanner.Begin] := {OneSpaceBefore, OneSpaceAfter, DedentTwo,IndentOne};
		formatToken[Scanner.Then] := {OneSpaceBefore, OneSpaceAfter, IndentOne};
		formatToken[Scanner.Do] := {OneSpaceBefore, OneSpaceAfter, IndentOne};
		formatToken[Scanner.End] := {OneSpaceBefore, OneSpaceAfter, DedentOne};
		formatToken[Scanner.Enum] := {OneSpaceBefore, OneSpaceAfter, IndentOne};
		formatToken[Scanner.Else] := {OneSpaceBefore, OneSpaceAfter, DedentOne, IndentOne};
		formatToken[Scanner.Elsif] := {OneSpaceBefore, OneSpaceAfter, DedentOne};
		formatToken[Scanner.Until] := {OneSpaceBefore, OneSpaceAfter, DedentOne};
		formatToken[Scanner.Finally] := {OneSpaceBefore, OneSpaceAfter, DedentOne,IndentOne};
		formatToken[Scanner.Type] := {OneSpaceBefore, OneSpaceAfter, DedentOneWithComment,IndentOne};
		formatToken[Scanner.Operator] := { DedentOneWithComment,IndentOne};
		formatToken[Scanner.Import] := { DedentOneWithComment,IndentOne};
		formatToken[Scanner.Module] := { IndentTwo};

        formatToken[Scanner.TimesMarker] := {NoSpaceBefore, NoSpaceAfter};
        formatToken[Scanner.MinusMarker] := {NoSpaceBefore, NoSpaceAfter};
        formatToken[Scanner.MinusUnary] := {MaybeSpaceBefore, NoSpaceAfter};
        formatToken[Scanner.PlusUnary] := {MaybeSpaceBefore, NoSpaceAfter};
		formatToken[Scanner.LeftParenthesisProcParamStart] := {NoSpaceBefore, NoSpaceAfter, IndentOne};
		formatToken[Scanner.LeftParenthesisCallParamStart] := {NoSpaceBefore, NoSpaceAfter, IndentOne};
		formatToken[Scanner.RightParenthesisParamEnd] := {NoSpaceBefore, NoSpaceAfter, DedentOne};
		formatToken[Scanner.CommaParamSeparator] := {NoSpaceBefore, OneSpaceAfter};
		formatToken[Scanner.SemicolonParamSeparator] := {MaybeSpaceBefore, OneSpaceAfter};
		formatToken[Scanner.SemicolonStatementSeparator] := {MaybeSpaceBefore, OneSpaceAfter};
		formatToken[Scanner.ProcedureDeclaration] := {OneSpaceBefore, OneSpaceAfter, DedentOneWithComment,IndentTwo};
		formatToken[Scanner.VarDeclaration] := {OneSpaceBefore, OneSpaceAfter, DedentOneWithComment,IndentOne};
		formatToken[Scanner.ConstDeclaration] := {OneSpaceBefore, OneSpaceAfter, DedentOneWithComment,IndentOne};

    END Init;

BEGIN
    Init;
END LSPScannerMetadata.