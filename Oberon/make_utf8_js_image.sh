#!/bin/sh
set -e

./get-source.sh
patch -d work <../ProposedPatches/use-utf8-charset.patch
sed 's/prevX, prevY, X, Y, t, codepoint: INTEGER; keys: SET;/prevX, prevY, X, Y, t: INTEGER; keys: SET; ch: CHAR;/' -i work/Oberon.Mod.txt
./apply-proposed-patches.sh
./apply-emulator-patches.sh

cp OberonFromScratch.Tool.JavaScript.txt work/OberonFromScratch.Tool.txt

patch -d work <utf8-charset-for-extras.patch
patch -d work <utf8-paravirtualized-keyboard.patch
patch -d work <paravirtualized-disk.patch
patch -d work <power-management.patch
patch -d work <reduce-filesystem-offset.patch
sed 's/prevX, prevY, X, Y, t: INTEGER; keys: SET; ch: CHAR;/prevX, prevY, X, Y, t, codepoint: INTEGER; keys: SET;/' -i work/Oberon.Mod.txt

./derive-files.sh

patch -d work <fix-js-start-offset.patch
