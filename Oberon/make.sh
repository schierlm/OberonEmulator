#!/bin/sh
set -e

./get-source.sh

patch -d work <detect-screen-size.patch
patch -d work <paravirtualized-keyboard.patch
patch -d work <paravirtualized-disk.patch
patch -d work <power-management.patch
patch -d work <power-management-keyboard-unresponsive.patch
patch -d work <reduce-filesystem-offset.patch
patch -d work <reality-lost.patch

./derive-files.sh