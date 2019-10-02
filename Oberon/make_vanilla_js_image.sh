#!/bin/sh
set -e

./get-source.sh
patch -d work <Oberon2013Modifications/BugFixes/FixAliasedModules.patch
patch -d work <detect-screen-size.patch

patch -d work <vanilla-paravirtualized-keyboard.patch
patch -d work <paravirtualized-disk-minimal.patch
patch -d work <power-management.patch
patch -d work <power-management-keyboard-unresponsive.patch
patch -d work <reduce-filesystem-offset.patch

grep -v 'Defrag\|Splines' OberonFromScratch.Tool.JavaScript.txt >work/OberonFromScratch.Tool.txt
cp work/System.Tool.txt work/System.Tool.Orig.txt
./derive-files.sh
cp work/System.Tool.Orig.txt work/System.Tool.Full.txt
sed 's/state := BatchFailed/state := BatchRunning/' -i work/Batch.Mod.txt

patch -d work <fix-js-start-offset.patch
