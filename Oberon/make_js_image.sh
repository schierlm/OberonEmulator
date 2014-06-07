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

cp OberonFromScratch.Tool.JavaScript.txt work/OberonFromScratch.Tool.txt

patch -d work <../ProposedPatches/initialize-local-variables.patch
patch -d work <../ProposedPatches/system-clear-init-buf.patch
patch -d work <../ProposedPatches/log-allocation-failures.patch
patch -d work <../ProposedPatches/trap-backtrace.patch
patch -d work <../ProposedPatches/better-display-compatibility.patch
patch -d work <../ProposedPatches/increment-fix.patch

./derive-files.sh