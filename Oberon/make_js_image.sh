#!/bin/sh
set -e

./get-source.sh v2

# TODO: Fix conflicts with emulator patches; revert this patch for now
patch -d work -R <Oberon2013Modifications/FontConversion/RemoveGlyphWidthLimit.patch

patch -d work <../ProposedPatches/log-allocation-failures.patch -F 3
patch -d work <../ProposedPatches/better-display-compatibility.patch
patch -d work <../ProposedPatches/filesystem-encapsulation.patch

./apply-emulator-patches.sh

cp OberonFromScratch.Tool.JavaScript.txt work/OberonFromScratch.Tool.txt

patch -d work <paravirtualized-keyboard.patch
patch -d work -F 3 <paravirtualized-disk.patch
patch -d work <power-management.patch
patch -d work <power-management-keyboard-unresponsive.patch
patch -d work <reduce-filesystem-offset.patch
patch -d work <js-bigmem-display.patch

./derive-files.sh v2

patch -d work <fix-js-start-offset.patch

[ -z "$1" ] && exit 0

./compile-image.sh "$1" FullDiskImage 'MB=?' MinimalDiskImage
