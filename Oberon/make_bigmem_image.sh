#!/bin/sh
set -e

./get-source.sh
./apply-proposed-patches.sh
./apply-emulator-patches.sh

cp DisplayC8.Mod.txt work
patch -d work <256colors.patch
patch -d work <clipboard-integration.patch

./derive-files.sh

[ -z "$1" ] && exit 0

./compile-image.sh "$1" ../Java/BigmemDiskImage '?B=?'