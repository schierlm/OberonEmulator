#!/bin/sh
set -e

./get-source.sh
cp work/Input.Mod.txt work/Input0.Mod.txt
patch -d work <Oberon2013Modifications/UTF8Charset/UTF8Charset.patch
patch -d work <Oberon2013Modifications/VariableLinespace/VariableLineSpaceUTF8.patch
mv work/Input.Mod.txt work/Input1.Mod.txt
mv work/Input0.Mod.txt work/Input.Mod.txt
sed 's/prevX, prevY, X, Y, t, codepoint: INTEGER; keys: SET;/prevX, prevY, X, Y, t: INTEGER; keys: SET; ch: CHAR;/' -i work/Oberon.Mod.txt
./apply-proposed-patches.sh
mv work/Input1.Mod.txt work/Input.Mod.txt
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
