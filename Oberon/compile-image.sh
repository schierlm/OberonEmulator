#!/bin/sh
set -e

[ -z "$1" ] && exit 0
[ -z "$2" ] && exit 0
[ -z "$3" ] && exit 0

if ! [ -f download/base.dsk ]; then
	echo "Base disk not found. Please run ./make_vanilla_js_image.sh first!"
	exit 1
fi

JAVA="$1"
dd if=/dev/zero of=work/tmp bs=1024 count=1
dd if=../JS/boot.rom of=work/boot.rom bs=1020 count=1
echo -n "$3" >>work/boot.rom
cat work/tmp >>work/boot.rom
rm work/tmp
cp download/base.dsk work/dsk

cd work
rm *.orig
sed 's/Modules.Load("System"/Modules.Load("CommandLineSystem"/;s/NewTask(GC, 1000)/NewTask(GC, 10)/' <Oberon.Mod.txt >OberonX.Mod.txt
cp Defragger.Mod.txt Defragger0.Mod.txt
patch <../Oberon2013Modifications/CommandLineCompiler/CommandLineDefragger.patch
mv Defragger.Mod.txt DefraggerX.Mod.txt
mv Defragger0.Mod.txt Defragger.Mod.txt
for i in *.txt; do unix2mac $i; mv $i ${i%.txt}; done
cp ../wirth-personal/people.inf.ethz.ch/wirth/ProjectOberon/Sources/Oberon.Mod.txt .
cp ../wirth-personal/people.inf.ethz.ch/wirth/ProjectOberon/Sources/System.Mod.txt .
cp ../wirth-personal/people.inf.ethz.ch/wirth/ProjectOberon/Sources/ORP.Mod.txt .
cp ../Oberon2013Modifications/CommandLineCompiler/CommandLineSystem.Mod.txt .
dos2unix *.Mod.txt
patch -p1 <../Oberon2013Modifications/CommandExitCodes/CommandExitCodes.patch
sed 's/Modules.Load("System"/Modules.Load("CommandLineSystem"/;s/NewTask(GC, 1000)/NewTask(GC, 10)/' <Oberon.Mod.txt >OberonY.Mod
rm System.Mod.txt ORP.Mod.txt Oberon.Mod.txt
mv CommandLineSystem.Mod.txt CommandLineSystem.Mod
cd ..
echo '!cd work' > work/.cmds
echo '+OberonY.Mod' >> work/.cmds
echo '+CommandLineSystem.Mod' >> work/.cmds
echo '!exit' >> work/.cmds
${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar 0 0 work/dsk ../Java/JSBootLoad.rom CommandLine <work/.cmds
: > work/.cmds
for FILE in OberonY MenuViewers TextFrames System CommandLineSystem Edit ORS ORB ORG ORP; do
	echo "ORP.Compile $FILE.Mod/s ~" >> work/.cmds
	echo "!sleep 300" >> work/.cmds
done
echo 'System.DeleteFiles OberonY.Mod ~' >> work/.cmds
echo '!exit' >> work/.cmds
${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar 0 0 work/dsk ../Java/JSBootLoad.rom CommandLine <work/.cmds

cd work
rm OberonY.Mod CommandLineSystem.Mod
mv System.Tool.Full System.Tool
echo '!cd work' > .cmds
for FILE in *.Fnt *.Mod *.Lib *.Text *.Tool; do
	echo +$FILE >> .cmds
done
cd ..
echo '!exit' >> work/.cmds
${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar 0 0 work/dsk ../Java/JSBootLoad.rom CommandLine <work/.cmds
head -n 4 Oberon2013Modifications/BuildModifications.Tool.txt > work/.cmds
echo '!exit' >> work/.cmds
${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar 0 0 work/dsk ../Java/JSBootLoad.rom CommandLine <work/.cmds

echo '!autosleep 150' > work/.cmds
head -n -6 Oberon2013Modifications/BuildModifications.Tool.txt | tail -n +6 >> work/.cmds
if [ -f work/DisplayX.Mod ]; then
	echo "ORP.Compile DisplayX.Mod ~" >> work/.cmds
	echo "!sleep 300" >> work/.cmds
fi
for FILE in PIO Net CommandLineSystem OberonX PCLink1 Clipboard SCC Net Blink Checkers Clipboard EBNF GraphTool Hilbert Sierpinski Stars Tools; do
	echo "ORP.Compile ${FILE}.Mod ~" >> work/.cmds
done
for FILE in ColorPalette PaletteEdit ColorPictureTiles ColorPictureGrab; do
	[ -f work/${FILE}.Mod ] && echo "ORP.Compile ${FILE}.Mod ~" >> work/.cmds
done

echo 'System.DeleteFiles ORC.Mod RISC.Mod SmallPrograms.Mod ~' >> work/.cmds
echo '!exit' >> work/.cmds
${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar 0 0 work/dsk ../Java/JSBootLoad.rom CommandLine <work/.cmds
cp work/dsk work/mindsk
cp work/dsk work/rscdsk
echo 'ORP.Compile DefraggerX.Mod/s ~' > work/.cmds
echo 'System.DeleteFiles OberonX.Mod DefraggerX.Mod CommandLineSystem.Mod CommandLineSystem.rsc CommandLineSystem.smb ~' >> work/.cmds
if [ -f work/DisplayX.Mod ]; then
	echo 'ORP.Compile Display.Mod ~' >> work/.cmds
	echo 'System.DeleteFiles DisplayX.Mod ~' >> work/.cmds
fi
echo 'Defragger.Load' >> work/.cmds
echo 'ORP.Compile Oberon.Mod Defragger.Mod/s ~' >> work/.cmds
echo 'Defragger.Defrag' >> work/.cmds
echo '!exit' >> work/.cmds
${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar 0 0 work/dsk ../Java/JSBootLoad.rom CommandLine <work/.cmds
${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar EncodePNG ../JS/$2.png work/dsk work/boot.rom

[ -z "$4" ] && exit 0

mv work/System.Tool.Min work/System.Tool
echo 'Script.Run' > work/Minify.Script
echo ' |> System.Directory * ~' >> work/Minify.Script
echo ' |> ScriptVars.CaptureViewer FileList - "System.Directory" ~' >> work/Minify.Script
echo ' |> ScriptVars.SetChars Newline 13 ~' >> work/Minify.Script
echo ' |> ScriptVars.Replace FileList 0 Newline 9999 " " ~' >> work/Minify.Script
echo ' |> ScriptVars.Insert FileList "System.DeleteFiles " 0 ~' >> work/Minify.Script
echo ' |> ScriptVars.Insert FileList " ~" -1 ~' >> work/Minify.Script
for FILE in System.Tool Oberon10.Scn.Fnt Input.rsc Display.rsc Viewers.rsc Fonts.rsc Texts.rsc Oberon.rsc MenuViewers.rsc TextFrames.rsc System.rsc Edit.rsc Clipboard.rsc PCLink1.rsc; do
	echo ' |> ScriptVars.Replace FileList 0 " '$FILE' " 1 " " ~' >> work/Minify.Script
done
echo ' |> ScriptVars.Expand %FileList% ~ %~' >> work/Minify.Script
echo ' ||' >> work/Minify.Script
echo '!cd work' > work/.cmds
echo '+System.Tool' >> work/.cmds
echo '+Minify.Script' >> work/.cmds
echo 'ORP.Compile Oberon.Mod DefraggerX.Mod/s ~' >> work/.cmds
echo 'Defragger.Load' >> work/.cmds
echo 'Script.RunFile Minify.Script ~' >> work/.cmds
echo 'Defragger.Defrag' >> work/.cmds
echo '!exit' >> work/.cmds
${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar 0 0 work/mindsk ../Java/JSBootLoad.rom CommandLine <work/.cmds
${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar EncodePNG ../JS/$4.png work/mindsk work/boot.rom

cp work/Kernel.Mod work/Kernel.Mod.txt
cp work/System.Mod work/System.RS.Mod.txt
cp work/Modules.Mod work/Modules.RS.Mod.txt
cp work/Oberon.Mod work/Oberon.Mod.txt
mac2unix work/Kernel.Mod.txt work/System.RS.Mod.txt work/Modules.RS.Mod.txt work/Oberon.Mod.txt
cp Oberon2013Modifications/MinimalFonts/Fonts.Embedded.Mod.txt work
cp Oberon2013Modifications/RescueSystem/*.txt work

sed 's/FSoffset = 0H;/FSoffset = 80000H;/' -i work/Kernel.Mod.txt
patch -d work <Oberon2013Modifications/RescueSystem/RescueSystem.patch -F 3
patch -d work <Oberon2013Modifications/RescueSystem/POSTPATCH_after_DefragSupport.patch
patch -d work <rescue-system-paravirtualized-disk.patch
sed 's/FSoffset = 80080H;/FSoffset = 80H;/' -i work/Kernel.Mod.txt

for FILE in Kernel.Mod System.RS.Mod Modules.RS.Mod Oberon.Mod Fonts.Embedded.Mod RescueSystemLoader.Mod RescueSystemTool.Mod System.Tool.RS; do
	unix2mac work/$FILE.txt
	mv work/$FILE.txt work/$FILE
done
echo 'ORP.Compile Oberon.Mod ~' > work/.cmds
echo 'System.RenameFiles Oberon.rsc => Oberon.rsc.RS ~' >> work/.cmds
echo '!cd work' >> work/.cmds
for FILE in Kernel.Mod System.RS.Mod Modules.RS.Mod Oberon.Mod Fonts.Embedded.Mod RescueSystemLoader.Mod RescueSystemTool.Mod System.Tool.RS; do echo +$FILE >> work/.cmds; done
echo 'ORP.Compile RescueSystemTool.Mod/s RescueSystemLoader.Mod/s OberonX.Mod ~' >> work/.cmds
echo 'ORP.Compile Kernel.Mod FileDir.Mod Files.Mod Modules.Mod ~' >> work/.cmds
echo 'ORL.Link Modules ~' >> work/.cmds
echo 'ORL.Load Modules.bin ~' >> work/.cmds
echo 'RescueSystemTool.MoveFilesystem' >> work/.cmds
echo '!exit' >> work/.cmds
${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar 0 0 work/rscdsk ../Java/JSBootLoad.rom CommandLine <work/.cmds

sed 's/Modules.Load("System"/Modules.Load("CommandLineSystem"/;s/NewTask(GC, 1000)/NewTask(GC, 10)/' <work/Oberon.Mod >work/OberonX.Mod
echo '!cd work' > work/.cmds
echo '+OberonX.Mod' >> work/.cmds
echo 'ORP.Compile Modules.RS.Mod Fonts.Embedded.Mod System.RS.Mod ~' >> work/.cmds
echo 'ORL.Link Modules ~' >> work/.cmds
echo 'System.RenameFiles Modules.bin => Modules.bin.RS Fonts.rsc => Fonts.rsc.RS System.rsc => System.rsc.RS ~' >> work/.cmds
echo 'ORP.Compile Modules.Mod Fonts.Mod System.Mod OberonX.Mod ~' >> work/.cmds
echo 'ORL.Link Modules ~' >> work/.cmds
echo 'System.CopyFiles Input.rsc => Input.rsc.RS Display.rsc => Display.rsc.RS Viewers.rsc => Viewers.rsc.RS' \
     'Texts.rsc => Texts.rsc.RS MenuViewers.rsc => MenuViewers.rsc.RS TextFrames.rsc => TextFrames.rsc.RS' \
     'Edit.rsc => Edit.rsc.RS PCLink1.rsc => PCLink1.rsc.RS Clipboard.rsc => Clipboard.rsc.RS' \
     'ORS.rsc => ORS.rsc.RS ORB.rsc => ORB.rsc.RS ORG.rsc => ORG.rsc.RS ORP.rsc => ORP.rsc.RS ORL.rsc => ORL.rsc.RS ~' >> work/.cmds
echo 'RescueSystemTool.LoadRescue' >> work/.cmds
echo 'System.DeleteFiles Fonts.Embedded.Mod Modules.RS.Mod System.RS.Mod ~' >> work/.cmds
echo '!exit' >> work/.cmds
${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar 0 0 work/rscdsk ../Java/JSBootLoad.rom CommandLine <work/.cmds

echo 'ORP.Compile DefraggerX.Mod/s ~' > work/.cmds
echo 'System.DeleteFiles OberonX.Mod DefraggerX.Mod CommandLineSystem.Mod CommandLineSystem.rsc CommandLineSystem.smb ~' >> work/.cmds
echo 'Defragger.Load' >> work/.cmds
echo 'ORP.Compile Oberon.Mod Defragger.Mod/s ~' >> work/.cmds
echo 'Defragger.Defrag' >> work/.cmds
echo '!exit' >> work/.cmds
${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar 0 0 work/rscdsk ../Java/JSBootLoad.rom CommandLine <work/.cmds
${JAVA} -Djava.awt.headless=true -jar ../Java/OberonEmulator.jar EncodePNG ../JS/${2/Disk/WithRescueDisk}.png work/rscdsk work/boot.rom

# TODO rescue system does not boot to rescue mode!
# TODO check RESCUE images: RebuildToolBuilder find nothing uncompiled and mods all have valid keys! Also test RAM sizes
