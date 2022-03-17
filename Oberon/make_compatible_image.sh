#!/bin/sh
set -e

./get-source.sh
./apply-proposed-patches.sh
./apply-emulator-patches.sh
./derive-files.sh

[ -z "$1" ] && exit 0

./compile-image.sh "$1" ../Java/CompatibleDiskImage '?B=?'