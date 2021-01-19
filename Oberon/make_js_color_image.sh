#!/bin/sh
set -e

./get-source.sh v2
cp ColorPalette.Mod.txt PaletteEdit.Mod.txt Oberon2013Modifications/DrawAddons/16Color/*.txt work

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
patch -d work <js-64mb.patch
mv work/Display.Mod.txt work/DisplayX.Mod.txt
sed 's/white\* = 1;/white\* = 15;/' -i work/DisplayX.Mod.txt
mv work/Display.Mod.16Colors.txt work/Display.Mod.txt

./derive-files.sh v2

patch -d work <fix-js-start-offset.patch
rm work/Draw.Tool.txt work/System.Tool.Full.txt
cp System.Tool.Color work/System.Tool.Full
cp Draw.Tool.Color work/Draw.Tool

[ -z "$1" ] && exit 0

./compile-image.sh "$1" ColorDiskImage 'CB=?'
