#!/bin/sh
set -e

./get-source.sh debug
patch -d work <raw-js-input.patch
./apply-emulator-patches.sh
./derive-files.sh

cp OberonFromScratch.Tool.JavaScript.txt work/OberonFromScratch.Tool.txt
sed -i '6,$ s/Mod\/s OR/Mod\/s ~\nORP.Compile OR/g' work/BuildModifications.Tool.txt
sed -i '6,$ s/Mod\/s \(GraphicFrames\|Curves\|ColorPictureTiles\|Clock\)/Mod\/s ~\nORP.Compile \1/g' work/BuildModifications.Tool.txt
sed -i '6,$ s/Mod\/s \(EditU\|ScriptBlocks\|DebugInspect\|MacroTool\|Pixelizr\)/Mod\/s ~\nORP.Compile \1/g' work/BuildModifications.Tool.txt
sed -i '6,$ s/\/s/\/s\/d/g' work/BuildModifications.Tool.txt

[ -z "$1" ] && exit 0

## 1MB memory is tight for building debug symbols, therefore restart a few more times
head -n 23 work/BuildModifications.Tool.txt >work/build.cmds
tail -n +24 work/BuildModifications.Tool.txt | head -n 4 >work/build-extra1.cmds
tail -n +28 work/BuildModifications.Tool.txt | head -n 12 >work/build-extra2.cmds
tail -n +40 work/BuildModifications.Tool.txt | head -n 23 >work/build-extra3.cmds
tail -n +64 work/BuildModifications.Tool.txt | head -n 10 >work/build-extra4.cmds
tail -n +74 work/BuildModifications.Tool.txt | grep -v 'DEBUG VERSION ONLY:' >work/build-extra5.cmds
echo '' >work/build-extra6.cmds
for FILE in PIO Net CommandLineSystemY OberonX PCLink1 SCC Net Blink Checkers Clipboard EBNF GraphTool Hilbert Sierpinski Stars; do
	echo "ORP.Compile ${FILE}.Mod/s/d ~" >> work/build-extra6.cmds
done
echo "ORP.Compile Tools.Mod/s/d ~" > work/build-extra7.cmds

./compile-image.sh "$1" DebuggableDiskImage 'MB=?' || true
