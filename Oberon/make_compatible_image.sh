#!/bin/sh
set -e

./get-source.sh

patch -d work <detect-screen-size.patch
cp Clipboard.Mod.Compatible.txt work/Clipboard.Mod.txt

./derive-files.sh