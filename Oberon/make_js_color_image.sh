#!/bin/sh
set -e

./get-source.sh
cd Oberon2013Modifications
./make_release.sh
cd ..
mv Oberon2013Modifications/work/*.txt work
rm -rf Oberon2013Modifications/work
cp BootLoad.Mod.txt work
cp Oberon2013Modifications/DrawAddons/16Color/*.txt work

# TODO: Fix conflicts with emulator patches; revert this patch for now
patch -d work -R <Oberon2013Modifications/FontConversion/RemoveGlyphWidthLimit.patch

patch -d work <../ProposedPatches/log-allocation-failures.patch -F 3
patch -d work <../ProposedPatches/better-display-compatibility.patch
patch -d work <../ProposedPatches/filesystem-encapsulation.patch

./apply-emulator-patches.sh

cp OberonFromScratch.Tool.JavaScript.txt work/OberonFromScratch.Tool.txt
cp Display.Mod.16Colors.txt work/Display.Mod.16Colors.txt

patch -d work <paravirtualized-keyboard.patch
patch -d work -F 3 <paravirtualized-disk.patch
patch -d work <power-management.patch
patch -d work <power-management-keyboard-unresponsive.patch
cp work/BootLoad.Mod.txt work/BootLoad.Mod.copy.txt
patch -d work <16colors.patch
mv work/BootLoad.Mod.copy.txt work/BootLoad.Mod.txt
patch -d work <oberon-palette.patch

patch -d work <reduce-filesystem-offset.patch
patch -d work <js-4mb.patch
mv work/Display.Mod.16Colors.txt work/Display.Mod.txt

mv work/Modules.Mod.txt work/Modules0.Mod.txt
cp wirth-personal/people.inf.ethz.ch/wirth/ProjectOberon/Sources/Modules.Mod.txt work
dos2unix work/Modules.Mod.txt
cp work/ImageFileDir.Mod.txt work/ImageFileDir0.Mod.txt
cp work/ImageFiles.Mod.txt work/ImageFiles0.Mod.txt
cp work/ImageTool.Mod.txt work/ImageTool0.Mod.txt
patch -d work -R <Oberon2013Modifications/CrossCompiler/CrossCompiler.patch
./derive-files.sh
patch -d work <Oberon2013Modifications/CrossCompiler/CrossCompiler.patch
mv work/Modules0.Mod.txt work/Modules.Mod.txt
mv work/ImageTool0.Mod.txt work/ImageTool.Mod.txt
mv work/ImageFileDir0.Mod.txt work/ImageFileDir.Mod.txt
mv work/ImageFiles0.Mod.txt work/ImageFiles.Mod.txt

patch -d work <fix-js-start-offset.patch
rm work/Draw.Tool.txt work/System.Tool.Full.txt
cp System.Tool.Color work/System.Tool.Full
cp Draw.Tool.Color work/Draw.Tool
