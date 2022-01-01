# Oberon Language support with LSP wrapper

This package brings Oberon syntax highlighting to VSCode. In addition, it can
invoke an Oberon Language Server written in Java to provide code folding, auto
completion, reformatting, reference navigation and more. The Language server
will then connect to a Language Server Protocol Helper, which may (depending on
the exact language dialect) either be written as a native binary, or as a
Project Oberon disk image run inside the Oberon FPGA Emulator (written in Java).
The idea is to implement the Language Server Protocol helper in the language it
(or its compiler) is written in, to leverage synergies from the already existing
lexer and parser for the language.

## Features

- Supported file extensions `.Mod`, `.ob`, `.ob07` and `.Mod.txt`
- Basic syntax highlighting (TextMate grammar)
- Comment detection (no nested comments), bracket insertion and matching
- Auto closing brackets, strings and comments
- Surrounding with brackets and strings
- Automatic indentation after certain words (partially disabled since the
  complex expression seems to freeze VSCode sometimes)
- Optionally calling out to an LSP language server for more features:
  * Compiler warnings and errors
  * Unused variables
  * Outline and folding
  * Semantic highlighting
  * Jump to definition / references / call hierarchy
  * Autocompletion
  * Formatting
  * Rename non-exported variable/function in file
  * Hover for function definition; function signature help
  * Cache mode / Workspace Folder support (if disabled, only currently open
    files will get parsed/checked)

## Requirements

In case you want to use the language server, you have to download and install it
separately and configure its path (`oberonLsp.languageServer.location`). You
will also need a version of Java (8, 11 or 17) installed and configure your Java
path in case it is not in `$PATH` (`oberonLsp.languageServer.javaLocation`).

Also, the language server needs to be explicitly enabled
(`oberonLsp.languageServer.activate`), so if you do not want to use the language
server, nothing special needs to be done.

## Extension Settings

This extension contributes the following settings:

* `myExtension.enable`: enable/disable this extension
* `myExtension.thing`: set to `blah` to do something
* `oberonLsp.languageServer.activate`: Activate the language server
* `oberonLsp.languageServer.cache.activate`: Activate the cache mode.
* `oberonLsp.languageServer.cache.directory`: Cache directory relative to
  workspace folder, by default `.vscode/lsp-cache`.
* `oberonLsp.languageServer.javaLocation`: Location of your Java binary
* `oberonLsp.languageServer.location`:  Location (directory) of your language server
* `oberonLsp.languageServer.launchType`: Which Oberon dialect you want to use
  with the language server:
  * `po2013`: Project Oberon 2013
  * `extended`: Extended Oberon
  * `retro`: Oberon Retro compiler
  * `a2`: A2 / Bluebottle (needs native compiler helper binary). You can
    configure the compiler command line switches in
    `oberonLsp.languageServer.launchTypeA2.switches` and additional symbol path
    in `oberonLsp.languageServer.launchTypeA2.symbolpath`.
  * `other1`: Any other system, give arguments in
    `oberonLsp.languageServer.other1Arguments` (default: same as `po2013`)
  * `other2`: Any other system, give arguments in
    `oberonLsp.languageServer.other2Arguments` (default: Connect to language
    server helper running in Oberon emulator)
  * `other3`: Any other system, give arguments in
    `oberonLsp.languageServer.other2Arguments` (default: run Language Server
    under remote Java debugger)
  * `oberonLsp.languageServer.modifiedDiskImage`: Path to alternate disk image
    for `po2013` (useful if it contains symbol files for files not present in
    source code form in the workspace)

## Release Notes

### 0.9.0

Initial (beta) release.
