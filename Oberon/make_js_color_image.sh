#!/bin/sh
set -e

./get-source.sh
./apply-proposed-patches.sh
./apply-emulator-patches.sh

cp OberonFromScratch.Tool.JavaScript.txt work/OberonFromScratch.Tool.txt
cp Display.Mod.16Colors.txt work/Display.Mod.16Colors.txt

patch -d work <paravirtualized-keyboard.patch
patch -d work <paravirtualized-disk.patch
patch -d work <power-management.patch
patch -d work <power-management-keyboard-unresponsive.patch
cp work/BootLoad.Mod.txt work/BootLoad.Mod.copy.txt
patch -d work <16colors.patch
mv work/BootLoad.Mod.copy.txt work/BootLoad.Mod.txt
patch -d work <oberon-palette.patch
patch -d work <reduce-filesystem-offset.patch
patch -d work <js-4mb.patch
mv work/Display.Mod.16Colors.txt work/Display.Mod.txt

./derive-files.sh

patch -d work <fix-js-start-offset.patch
rm work/Draw.Tool.txt work/System.Tool.Full.txt
cp System.Tool.Color work/System.Tool.Full
cp Draw.Tool.Color work/Draw.Tool
