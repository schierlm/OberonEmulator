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
cp ../wirth-personal/personal/wirth/ProjectOberon/Sources/*.txt .
dos2unix *.txt
cd ..
cp *.Mod.txt *.Tool.txt work
