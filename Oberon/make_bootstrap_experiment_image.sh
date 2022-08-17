#!/bin/sh
set -e

if ! [ -f download/base.dsk ]; then
	echo "Base disk not found. Please run ./make_vanilla_js_image.sh first!"
	exit 1
fi

rm -rf work
mkdir work

for FILE in Kernel FileDir Files Modules Input Display Viewers Fonts Texts Oberon \
		MenuViewers TextFrames System Edit ORS ORB ORG ORP; do
	cp wirth-personal/people.inf.ethz.ch/wirth/ProjectOberon/Sources/$FILE.Mod.txt work/$FILE.Mod
	dos2unix work/$FILE.Mod
done
cp Oberon2013Modifications/Clipboard.Mod.txt work/Clipboard.Mod

cp Oberon2013Modifications/ORL.Mod.txt work/ORL.Mod
cp Oberon2013Modifications/DefragmentFreeSpace/DefragFiles.Mod.txt Oberon2013Modifications/DefragmentFreeSpace/Defragger.Mod.txt work
cp Oberon2013Modifications/CommandLineCompiler/CommandLineDefragger.Mod.txt work
patch -d work <vanilla-js-hardware-enumerator.patch
patch -d work <Oberon2013Modifications/ReproducibleBuild/ReproducibleDefragger.patch

for MOD in Kernel FileDir Files Modules Input Display Viewers Fonts Texts Oberon \
		MenuViewers TextFrames System Edit Clipboard ORS ORB ORG ORP; do
	cp work/$MOD.Mod work/R5$MOD.Mod
	perl -pi -e 's/^MODULE /MODULE R5/;/IMPORT/ && s/([A-Za-z0-9]+)([,;])/\1 := R5\1\2/g;s/IMPORT SYSTEM := R5SYSTEM/IMPORT SYSTEM/;s/END ([A-Za-z0-9]+\.)/END R5\1/;' work/R5$MOD.Mod
done
cp System.Tool.Bootstrap.txt work/R5System.Tool

patch -d work <bootstrap-renames.patch

unix2mac work/*.Mod work/R5System.Tool

[ -z "$1" ] && exit 0
JAVA="$1"

cp download/base.dsk work/dsk

${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar --command-line --rom ../Java/JSBootLoad.rom work/dsk <<'EOF'
!cd work
+R5Bootstrap.Mod
+R5Clipboard.Mod
+R5Display.Mod
+R5Edit.Mod
+R5FileDir.Mod
+R5Files.Mod
+R5Fonts.Mod
+R5Input.Mod
+R5Kernel.Mod
+R5MenuViewers.Mod
+R5Modules.Mod
+R5ORB.Mod
+R5ORG.Mod
+R5ORP.Mod
+R5ORS.Mod
+R5Oberon.Mod
+R5Oberon.Mod.orig
+R5System.Mod
+R5System.Tool
+R5TextFrames.Mod
+R5Texts.Mod
+R5Viewers.Mod
+DefragFiles.Mod.txt
+Defragger.Mod.txt
+CommandLineDefragger.Mod.txt
+ORL.Mod
ORP.Compile R5Kernel.Mod/s R5FileDir.Mod/s R5Files.Mod/s R5Modules.Mod/s ~
ORP.Compile R5Input.Mod/s R5Display.Mod/s R5Viewers.Mod/s ~
ORP.Compile R5Fonts.Mod/s R5Texts.Mod/s R5Oberon.Mod/s ~
!sleep 500
!exit
EOF

${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar --command-line --rom ../Java/JSBootLoad.rom work/dsk <<'EOF'
ORP.Compile R5MenuViewers.Mod/s R5TextFrames.Mod/s ~
ORP.Compile R5System.Mod/s R5Edit.Mod/s ~
ORP.Compile R5Bootstrap.Mod/s R5Clipboard.Mod/s ~
ORP.Compile R5ORS.Mod/s ~
!sleep 500
!exit
EOF

${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar --command-line --rom ../Java/JSBootLoad.rom work/dsk <<'EOF'
ORP.Compile R5ORB.Mod/s ~
ORP.Compile R5ORG.Mod/s ~
ORP.Compile R5ORP.Mod/s ORL.Mod/s ~
ORP.Compile Oberon.Mod DefragFiles.Mod.txt/s Defragger.Mod.txt/s CommandLineDefragger.Mod.txt/s ~
ORL.Link R5Modules ~
ORL.Load R5Modules.bin ~
System.DeleteFiles Blink.Mod Blink.rsc Blink.smb BootLoad.Mod Checkers.Mod Checkers.rsc Checkers.smb Clipboard.Mod Clipboard.rsc Clipboard.smb ~
System.DeleteFiles Curves.Mod Curves.rsc Curves.smb Display.Mod Display.Orig.Mod Display.rsc Display.smb Draw.Mod Draw.Tool Draw.rsc Draw.smb ~
System.DeleteFiles EBNF.Mod EBNF.rsc EBNF.smb Edit.Mod Edit.rsc Edit.smb FileDir.Mod FileDir.rsc FileDir.smb Files.Mod Files.rsc Files.smb ~
System.DeleteFiles Fonts.Mod Fonts.rsc Fonts.smb GraphTool.Mod GraphTool.rsc GraphTool.smb GraphicFrames.Mod GraphicFrames.rsc GraphicFrames.smb ~
System.DeleteFiles Graphics.Mod Graphics.rsc Graphics.smb Hilbert.Mod Hilbert.rsc Hilbert.smb Input.Mod Input.Orig.Mod Input.rsc Input.smb ~
System.DeleteFiles Kernel.Mod Kernel.rsc Kernel.smb MacroTool.Mod MacroTool.rsc MacroTool.smb Math.Mod Math.rsc Math.smb ~
System.DeleteFiles MenuViewers.Mod MenuViewers.rsc MenuViewers.smb Modules.Mod Modules.bin Modules.rsc Modules.smb Net.Mod ORB.Mod ORB.rsc ORB.smb ~
System.DeleteFiles ORC.Mod ORG.Mod ORG.rsc ORG.smb ORL.Mod ORL.rsc ORL.smb ORP.Mod ORP.rsc ORP.smb ORS.Mod ORS.rsc ORS.smb ORTool.Mod ORTool.rsc ORTool.smb ~
System.DeleteFiles Oberon.Mod Oberon.rsc Oberon.smb Oberon10b.Scn.Fnt Oberon10i.Scn.Fnt Oberon12.Scn.Fnt Oberon12b.Scn.Fnt Oberon12i.Scn.Fnt ~
System.DeleteFiles Oberon16.Scn.Fnt Oberon8.Scn.Fnt Oberon8i.Scn.Fnt OberonSyntax.Text PCLink1.Mod PCLink1.rsc PCLink1.smb PIO.Mod R5Bootstrap.Mod ~
System.DeleteFiles R5Clipboard.Mod R5Display.Mod R5Edit.Mod R5FileDir.Mod R5Files.Mod R5Fonts.Mod R5Input.Mod R5Kernel.Mod R5MenuViewers.Mod ~
System.DeleteFiles R5Modules.Mod R5Modules.bin R5ORB.Mod R5ORG.Mod R5ORP.Mod R5ORS.Mod R5Oberon.Mod R5System.Mod R5TextFrames.Mod R5Texts.Mod ~
System.DeleteFiles R5Viewers.Mod RISC.Mod RS232.Mod RS232.rsc RS232.smb Rectangles.Mod Rectangles.rsc Rectangles.smb SCC.Mod SCC.rsc SCC.smb ~
System.DeleteFiles Sierpinski.Mod Sierpinski.rsc Sierpinski.smb SmallPrograms.Mod Stars.Mod Stars.rsc Stars.smb System.Mod System.Tool ~
System.DeleteFiles System.rsc System.smb TTL0.Lib TTL1.Lib TextFrames.Mod TextFrames.rsc TextFrames.smb Texts.Mod Texts.rsc Texts.smb ~
System.DeleteFiles Tools.Mod Tools.rsc Tools.smb Viewers.Mod Viewers.rsc Viewers.smb ~
CommandLineDefragger.Load
System.DeleteFiles Defragger.Mod.txt Defragger.rsc Defragger.smb DefragFiles.Mod.txt DefragFiles.rsc DefragFiles.smb ~
System.DeleteFiles CommandLineDefragger.Mod.txt CommandLineDefragger.rsc CommandLineDefragger.smb ~
System.DeleteFiles CommandLineSystem.smb CommandLineSystem.rsc ~
CommandLineDefragger.Defrag
!sleep 500
!exit
EOF

Oberon2013Modifications/DefragmentFreeSpace/trim_defragmented_image.sh work/dsk
${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar --encode-png ../Java/BootstrapExperimentDiskImage.png --rom ../Java/JSBootLoad.rom work/dsk_trimmed
