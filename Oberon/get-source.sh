#!/bin/sh
set -e

USER_AGENT="Mozilla/5.0 oberuntime-build/1.0"

mkdir -p download
cd download
wget -nc -U "$USER_AGENT" http://projectoberon.net/zip/inner.zip
wget -nc -U "$USER_AGENT" http://projectoberon.net/zip/outer.zip
wget -nc -U "$USER_AGENT" http://projectoberon.net/zip/systools.zip
wget -nc -U "$USER_AGENT" http://projectoberon.net/zip/net.zip
wget -nc -U "$USER_AGENT" http://projectoberon.net/zip/or.zip
wget -nc -U "$USER_AGENT" http://projectoberon.net/zip/graph.zip
wget -nc -U "$USER_AGENT" http://projectoberon.net/zip/apptools.zip
wget -nc -U "$USER_AGENT" http://www.inf.ethz.ch/personal/wirth/ProjectOberon/Sources/PCLink1.Mod.txt
cd ..
rm -rf work
mkdir work
cd work
for i in ../download/*.zip; do unzip $i; done
cp ../download/*.txt .
dos2unix *.txt
cd ..
cp *.Mod.txt *.Tool.txt work
