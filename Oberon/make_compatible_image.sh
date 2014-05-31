#!/bin/sh
set -e

./get-source.sh

patch -d work <detect-screen-size.patch
patch -d work <pclink-globbing.patch
cp Clipboard.Mod.Compatible.txt work/Clipboard.Mod.txt
patch -d work <OberonFromScratch.Compatible.patch

./derive-files.sh

patch -d work <fix-start-offset.patch

sed 's/Calc.Tool/Calc.Tool RealCalc.Tool/' \
    -i work/System.Tool.Full.txt
sed 's/ResourceMonitor.Run/PCLink1.Run  ResourceMonitor.Run/' \
    -i work/System.Tool.Full.txt
