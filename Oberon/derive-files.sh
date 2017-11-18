#!/bin/sh
set -e

cat work/Modules.Mod.txt | \
    sed 's/SYSTEM.PUT(/SYSTEM.PUT(ImageOffset + /' | \
    sed 's/SYSTEM.GET(/SYSTEM.GET(ImageOffset + /' \
    >work/ImageORL.Mod.txt

for x in B G P; do
    cp work/OR$x.Mod.txt work/XOR$x.Mod.txt
done

for x in FileDir Files; do
    cp work/$x.Mod.txt work/Image$x.Mod.txt
done

patch -d work <derive-image-orl.patch
patch -d work <derive-crosscompiler.patch
patch -d work <derive-imagetool.patch

mv work/System.Tool.txt work/System.Tool.Full.txt
sed '1s/^/Clipboard.Paste  Clipboard.CopySelection  Clipboard.CopyViewer\n\n/' \
    -i work/System.Tool.Full.txt
grep -v 'ChangeFont\|ORP\|Draw\|Tools' <work/System.Tool.Full.txt \
    >work/System.Tool.Min.txt
sed 's/PCLink1.Run/PCLink1.Run  ResourceMonitor.Run  ResourceMonitor.Stop/' \
    -i work/System.Tool.Full.txt
sed 's/Draw.Tool/^  Draw.Tool Calc.Tool RealCalc.Tool/' \
    -i work/System.Tool.Full.txt
