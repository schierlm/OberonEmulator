#!/bin/sh
set -e

mv work/System.Tool.txt work/System.Tool.Full.txt
sed '1s/^/Clipboard.Paste  Clipboard.CopySelection  Clipboard.CopyViewer\n\n/' \
    -i work/System.Tool.Full.txt
grep -v 'ChangeFont\|ORP\|Draw\|Tools' <work/System.Tool.Full.txt \
    >work/System.Tool.Min.txt
sed 's/PCLink1.Run/PCLink1.Run  ResourceMonitor.Run  ResourceMonitor.Stop\nOnScreenKeyboard.Show/' \
    -i work/System.Tool.Full.txt
sed 's/Draw.Tool/^  Draw.Tool DrawAddons.Tool Calc.Tool RealCalc.Tool/' \
    -i work/System.Tool.Full.txt
