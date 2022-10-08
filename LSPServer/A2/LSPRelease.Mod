MODULE LSPRelease;

  IMPORT Files, Texts, Commands, Strings, Streams, TextUtilities, Diagnostics, Release;

  PROCEDURE BuildLists*(context: Commands.Context);
    VAR
      path: ARRAY 128 OF CHAR;
      options, buf: ARRAY 1024 OF CHAR;
      scontext: Commands.Context;
      sw: Streams.StringWriter;
      arg: Streams.StringReader;
      listText: Texts.Text; listWriter: TextUtilities.TextWriter; listReader: TextUtilities.TextReader;
      len: SIZE;
      F1, F2: Files.File;
      R1, R2: Files.Rider;
      builds: Release.Builds;
      diagnostics: Diagnostics.DiagnosticsList;
      file: Release.File;
  BEGIN
    IF ~context.arg.GetString(path) THEN
      RETURN;
    END;
    IF ~context.arg.GetString(options) THEN
      RETURN;
    END;

    len := Strings.Length(path) + Strings.Length(options) + 17;
    Streams.OpenStringWriter(sw, len);
    sw.String('--list --path='); sw.String(path); sw.Char(' '); sw.String(options);
    sw.String(" ~");
    sw.Get(buf);
    Streams.OpenStringReader(arg, buf);
    NEW(listText); NEW(listWriter, listText);
    NEW(scontext, NIL, arg, listWriter, context.error, NIL);
    Release.Build(scontext);

    NEW(diagnostics);
    IF ~Release.ParseBuildFile("Release.Tool", builds, context.out, diagnostics) THEN
      RETURN
    END;

    len := Strings.Length(path) + 21;
    Streams.OpenStringWriter(sw, len);
    sw.String(path); sw.String("OberonBuildOrder.Tool");
    sw.Get(buf);
    NEW(listReader, listText);
    F1 := Files.New(buf);
    F1.Set(R1, 0);
    len := Strings.Length(path) + 20;
    Streams.OpenStringWriter(sw, len);
    sw.String(path); sw.String("OberonBuildSkip.Tool");
    sw.Get(buf);
    F2 := Files.New(buf);
    F2.Set(R2, 0);

    listReader.Ln(buf);
    WHILE buf [0] # 0X DO listReader.Ln(buf) END; listReader.Ln(buf); (* skip header *)
    file := builds.files;
    WHILE listReader.res = Streams.Ok DO
      Strings.TrimRight(buf, ' ');
      WHILE (file # NIL) & (file.name # buf) DO
        IF file.IsSourceCode() THEN
          F2.WriteBytes(R2, file.name, 0, Strings.Length(file.name));
          F2.Write(R2, 0AX)
        END;
        file := file.next
      END;
      IF file = NIL THEN
        context.out.String("File "); context.out.String(buf);
        context.out.String(" missing in file list!"); context.out.Ln
      ELSE
        file := file.next
      END;
      F1.WriteBytes(R1, buf, 0, Strings.Length(buf));
      F1.Write(R1, 0AX);
      listReader.Ln(buf)
    END;
    WHILE file # NIL DO
      IF file.IsSourceCode() THEN
        F2.WriteBytes(R2, file.name, 0, Strings.Length(file.name));
        F2.Write(R2, 0AX)
      END;
      file := file.next
    END;
    Files.Register(F1);
    Files.Register(F2);
    context.out.String("Done."); context.out.Ln; context.out.Update;
  END BuildLists;

END LSPRelease.
