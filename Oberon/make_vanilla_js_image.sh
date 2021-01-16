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
patch -d work <vanilla-js.patch
patch -d work <Oberon2013Modifications/ReproducibleBuild/ReproducibleDefragger.patch
patch -d work <Oberon2013Modifications/CommandLineCompiler/CommandLineDefragger.patch
sed 's/Modules.Load("System",/Modules.Load("CommandLineSystem",/' <work/Oberon.Mod >work/Oberon1.Mod
unix2mac work/*.Mod
sed 's/Oberon.RetVal/0/' <Oberon2013Modifications/CommandLineCompiler/CommandLineSystem.Mod.txt >work/CLS.Mod

[ -z "$1" ] && exit 0

JAVA="$1"
mkdir -p download
cd download
wget -nc https://github.com/pdewacht/oberon-risc-emu/raw/master/DiskImage/Oberon-2020-08-18.dsk
cd ..
cp download/Oberon-2020-08-18.dsk work/dsk

${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar 0 0 work/dsk ../Java/CompatibleBootLoad.rom CommandLine <<'EOF'
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

${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar 0 0 work/dsk ../Java/CompatibleBootLoad.rom CommandLine <<'EOF'
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

${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar 0 0 work/dsk ../Java/JSBootLoad.rom CommandLine <<'EOF'
!cd work
+DefragFiles.Mod.txt
+Defragger.Mod.txt
ORP.Compile Oberon.Mod DefragFiles.Mod.txt/s Defragger.Mod.txt/s ~
Defragger.Load
System.DeleteFiles Defragger.Mod.txt Defragger.rsc Defragger.smb DefragFiles.Mod.txt DefragFiles.rsc DefragFiles.smb ~
System.DeleteFiles CommandLineSystem.smb CommandLineSystem.rsc ~
Defragger.Defrag
!sleep 500
!exit
EOF

Oberon2013Modifications/DefragmentFreeSpace/trim_defragmented_image.sh work/dsk
${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar EncodePNG ../JS/VanillaDiskImage.png work/dsk_trimmed work/boot.rom
