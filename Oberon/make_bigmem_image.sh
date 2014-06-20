#!/bin/sh
set -e

./get-source.sh
./apply-proposed-patches.sh
./apply-emulator-patches.sh

cp Display.Mod.Color.txt work/Display.Mod.txt
patch -d work <bigmem.patch
patch -d work <clipboard-integration.patch

patch -d work <paravirtualized-keyboard.patch
patch -d work <paravirtualized-disk.patch
patch -d work <power-management.patch
patch -d work <power-management-keyboard-unresponsive.patch

./derive-files.sh
