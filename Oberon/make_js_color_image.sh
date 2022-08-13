#!/bin/sh
set -e

./get-source.sh
patch -d work <raw-js-input.patch
./apply-emulator-patches.sh
./derive-files.sh

cp OberonFromScratch.Tool.JavaScript.txt work/OberonFromScratch.Tool.txt

rm work/Draw.Tool.txt
cp System.Tool.Color work/System.Final.Tool
cp Draw.Tool.Color work/Draw.Tool
cp ColorStartupScript.Text.txt work

[ -z "$1" ] && exit 0

./compile-image.sh "$1" ColorDiskImage 'CB=?'
