#!/bin/sh
set -e

USER_AGENT="Mozilla/5.0 oberuntime-build/1.0"

mkdir -p download
cd download
wget -nc -U "$USER_AGENT" http://projectoberon.net/zip/systools.zip
wget -nc -U "$USER_AGENT" http://projectoberon.net/zip/apptools.zip
cd ..
rm -rf work
mkdir work
cd work
for i in ../download/*.zip; do unzip $i; done
cp ../wirth-personal/people.inf.ethz.ch/wirth/ProjectOberon/Sources/*.txt .
dos2unix *.txt
cd ..
if [ "$1" == "v2" ]; then
	cd Oberon2013Modifications
	./make_release.sh
	cd ..
	mv Oberon2013Modifications/work/*.txt work
	rm -rf Oberon2013Modifications/work
	cp BootLoad.Mod.txt Clipboard.Mod.txt work
	rm work/SmallPrograms.Mod.txt work/RISC.Mod.txt work/ORC.Mod.txt
else
	cp Oberon2013Modifications/*/*.Mod.txt Oberon2013Modifications/*/*.Text.txt Oberon2013Modifications/*/*.Tool.txt work
	cp *.Mod.txt *.Tool.txt work
fi
