#!/bin/sh
set -e

./get-source.sh

patch -d work <detect-screen-size.patch
patch -d work <pclink-globbing.patch
cp Clipboard.Mod.Compatible.txt work/Clipboard.Mod.txt
cp OberonFromScratch.Tool.Compatible.txt work/OberonFromScratch.Tool.txt

patch -d work <../ProposedPatches/initialize-local-variables.patch
patch -d work <../ProposedPatches/system-clear-init-buf.patch
patch -d work <../ProposedPatches/log-allocation-failures.patch
patch -d work <../ProposedPatches/trap-backtrace.patch
patch -d work <../ProposedPatches/better-display-compatibility.patch
patch -d work <../ProposedPatches/increment-fix.patch

./derive-files.sh

patch -d work <fix-start-offset.patch

sed 's/Calc.Tool/Calc.Tool RealCalc.Tool/' \
    -i work/System.Tool.Full.txt
sed 's/ResourceMonitor.Run/PCLink1.Run  ResourceMonitor.Run/' \
    -i work/System.Tool.Full.txt
