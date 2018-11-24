#!/bin/sh
set -e

for MOD in Kernel FileDir Files Modules \
		Input Display Viewers Fonts Texts Oberon \
		MenuViewers TextFrames System Edit Clipboard \
		ORS ORB ORG ORP; do
	cp work/$MOD.Mod.txt work/R5$MOD.Mod.txt
	perl -pi -e 's/^MODULE /MODULE R5/;/IMPORT/ && s/([A-Za-z0-9]+)([,;])/\1 := R5\1\2/g;s/IMPORT SYSTEM := R5SYSTEM/IMPORT SYSTEM/;s/END ([A-Za-z0-9]+\.)/END R5\1/;' work/R5$MOD.Mod.txt
done

cp OberonFromScratch.Tool.Bootstrap.txt work/OberonFromScratch.Tool.txt
cp System.Tool.Bootstrap.txt work/R5System.Tool.txt

patch -d work <bootstrap-renames.patch
# TODO apply patch
