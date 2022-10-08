
CompilerInterface.Show ~

Release.Build
        --path=NewAos/
        --exclude="Applications GuiApplicationsMini GuiApplications Fun Testing Education Build EFI Contributions Oberon OberonGadgets OberonApplications OberonDocumentation OberonVoyager OberonAnts TrueTypeFonts CjkFonts ScreenFonts Pr3Fonts Pr6Fonts"
        Win64 ~

Compiler.Compile -p=Win64
        --objectFileExtension=.GofWw --define=WIN,AMD64,QUIETOUTPUT
        --symbolFileExtension=.SymWw --destPath=NewAos/
        Windows.Machine.Mod Windows.Kernel32.Mod Windows.WinTrace.Mod
        FoxScanner.Mod FoxSyntaxTree.Mod FoxParser.Mod FoxIntermediateBackend.Mod
        FoxSemanticChecker.Mod FoxGlobal.Mod Compiler.Mod
        LSPScannerMetadata.Mod LSPSyntaxTreeWalker.Mod
        LSPhConstants.Mod LSPhServer.Mod ~

FsTools.RenameFiles a2-lsph.exe => a2-lsph_old.exe ~

Linker.Link --path=NewAos/ --fileFormat=PE64CUI --fileName="a2-lsph.exe" --extension=GofWw --displacement=401000H
        Builtins Trace Kernel32 Heaps Modules Objects Kernel KernelLog Streams Commands Files WinFS
        Reals Diagnostics BitSets
        StringPool ObjectFile GenericLinker Reflection Loader StdIOShell Traps System
        Linker Compiler FoxOberonFrontend FoxARMBackend FoxAMDBackend LSPhConstants LSPhServer ~

FsTools.CloseFiles a2-lsph.exe ~

--------

Compiler.Compile -p=Win64 Release.Mod LSPRelease.Mod ~

LSPRelease.BuildLists
                NewAos/
                '--exclude="Applications GuiApplicationsMini GuiApplications Fun Testing Education Build EFI Contributions Oberon OberonGadgets OberonApplications OberonDocumentation OberonVoyager OberonAnts TrueTypeFonts CjkFonts ScreenFonts Pr3Fonts Pr6Fonts" Win64'
                ~
