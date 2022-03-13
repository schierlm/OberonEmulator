#!/bin/sh
set -e

rm -rf work
mkdir work

for FILE in Kernel Input Oberon Display; do
	cp wirth-personal/people.inf.ethz.ch/wirth/ProjectOberon/Sources/$FILE.Mod.txt work/$FILE.Mod
	dos2unix work/$FILE.Mod
done
cp Oberon2013Modifications/ORL.Mod.txt work/ORL.Mod
cp Oberon2013Modifications/DefragmentFreeSpace/DefragFiles.Mod.txt Oberon2013Modifications/DefragmentFreeSpace/Defragger.Mod.txt work
cp Oberon2013Modifications/CommandLineCompiler/CommandLineDefragger.Mod.txt work
patch -d work <vanilla-js-hardware-enumerator.patch
patch -d work <Oberon2013Modifications/ReproducibleBuild/ReproducibleDefragger.patch
sed 's/Modules.Load("System",/Modules.Load("CommandLineSystem",/' <work/Oberon.Mod >work/Oberon1.Mod
unix2mac work/*.Mod
sed 's/Oberon.RetVal/0/;s/BEGIN Texts.OpenWriter(W);/BEGIN Modules.Load("System", Mod); Texts.OpenWriter(W);/;s/LogPos := 0/LogPos := 0; Run/' <Oberon2013Modifications/CommandLineCompiler/CommandLineSystem.Mod.txt >work/CLS.Mod

[ -z "$1" ] && exit 0

JAVA="$1"
mkdir -p download
cd download
wget -nc https://github.com/pdewacht/oberon-risc-emu/raw/master/DiskImage/Oberon-2020-08-18.dsk
cd ..
cp download/Oberon-2020-08-18.dsk work/dsk

${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar --command-line --rom ../Java/CompatibleBootLoad.rom work/dsk <<'EOF'
!mouse 660,730
!mouse L
!sleep 1200
!mouse l
!sleep 200
!type PCLink1.Run
!sleep 500
!mouse M
!sleep 200
!mouse m
!sleep 1000
!cd work
!+CLS.Mod
!sleep 2000
!exit
EOF

${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar --command-line --rom ../Java/CompatibleBootLoad.rom work/dsk <<'EOF'
!mouse 660,730
!mouse L
!sleep 1200
!mouse l
!sleep 200
!type ORP.Compile CL
!sleep 500
!type S.Mod/s ~
!typechar 10
!sleep 500
!type CommandLineSy
!sleep 500
!type stem.Run ~
!sleep 500
!mouse M
!sleep 200
!mouse m
!sleep 1000
!mouse 660,715
!mouse M
!sleep 200
!mouse m
!sleep 2000
!cd work
+Kernel.Mod
+Input.Mod
+Oberon.Mod
+Oberon1.Mod
+Display.Mod
+ORL.Mod
ORP.Compile Kernel.Mod Input.Mod Oberon1.Mod Display.Mod ORL.Mod/s ~
ORL.Link Modules ~
ORL.Load Modules.bin ~
System.DeleteFiles ORL.Mod CLS.Mod Oberon1.Mod ORL.rsc ORL.smb ~
!sleep 500
!exit
EOF

dd if=/dev/zero of=work/tmp bs=1024 count=1
dd if=../JS/boot.rom of=work/boot.rom bs=1020 count=1
echo -n 'MB=?' >>work/boot.rom
cat work/tmp >>work/boot.rom
cat work/tmp work/dsk >download/base.dsk
rm work/tmp
cp download/base.dsk work/dsk

${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar --command-line --rom ../Java/JSBootLoad.rom work/dsk <<'EOF'
!cd work
+DefragFiles.Mod.txt
+Defragger.Mod.txt
+CommandLineDefragger.Mod.txt
ORP.Compile Oberon.Mod DefragFiles.Mod.txt/s Defragger.Mod.txt/s CommandLineDefragger.Mod.txt/s ~
CommandLineDefragger.Load
System.DeleteFiles Defragger.Mod.txt Defragger.rsc Defragger.smb DefragFiles.Mod.txt DefragFiles.rsc DefragFiles.smb ~
System.DeleteFiles CommandLineDefragger.Mod.txt CommandLineDefragger.rsc CommandLineDefragger.smb ~
System.DeleteFiles CommandLineSystem.smb CommandLineSystem.rsc ~
CommandLineDefragger.Defrag
!sleep 500
!exit
EOF

Oberon2013Modifications/DefragmentFreeSpace/trim_defragmented_image.sh work/dsk
${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar --encode-png ../JS/VanillaDiskImage.png --rom work/boot.rom work/dsk_trimmed
