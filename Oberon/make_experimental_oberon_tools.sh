#!/bin/sh
set -e

./get-source.sh
./derive-files.sh
patch -d work <fix-js-start-offset.patch
PATCH2='s/TextFrames, Viewers/Viewers, TextFrames/'
PATCH3='s/TextFrames.Call(scriptFrame, Texts.Pos(script) - 1, FALSE)/Oberon.Activate(scriptFrame, scriptFrame.text, Texts.Pos(script) - 1)/'
PATCH4='s/Viewers.This(/Viewers.This(Viewers.CurDisplay, /'
sed "$PATCH2;$PATCH3;$PATCH4" -i work/Batch.Mod.txt
mkdir keep
mv work/Batch* work/BuildExp* work/Image* keep
rm work/*
mv keep/* work
rmdir keep
