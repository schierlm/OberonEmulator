#!/bin/sh
set -e

./get-source.sh
patch -d work <detect-screen-size.patch

patch -d work <paravirtualized-keyboard.patch
patch -d work <paravirtualized-disk.patch
patch -d work <power-management.patch
patch -d work <power-management-keyboard-unresponsive.patch
patch -d work <reduce-filesystem-offset.patch

cp OberonFromScratch.Tool.JavaScript.txt work/OberonFromScratch.Tool.txt
cp work/System.Tool.txt work/System.Tool.Orig.txt
./derive-files.sh
cp work/System.Tool.Orig.txt work/System.Tool.Full.txt

patch -d work <fix-js-start-offset.patch
