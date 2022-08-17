#!/bin/sh
set -e

./get-source.sh
patch -d work <raw-js-input.patch
./apply-emulator-patches.sh
./derive-files.sh

[ -z "$1" ] && exit 0

./compile-image.sh "$1" FullDiskImage 'MB=?' MinimalDiskImage
