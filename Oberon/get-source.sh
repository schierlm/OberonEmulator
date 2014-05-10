#!/bin/sh
set -e
mkdir -p download
cd download
wget -nc http://www.paddedcell.com/projectoberon/wirth/Sources/inner.zip
wget -nc http://www.paddedcell.com/projectoberon/wirth/Sources/outer.zip
wget -nc http://www.paddedcell.com/projectoberon/wirth/Sources/systools.zip
wget -nc http://www.paddedcell.com/projectoberon/wirth/Sources/net.zip
wget -nc http://www.paddedcell.com/projectoberon/wirth/Sources/or.zip
wget -nc http://www.paddedcell.com/projectoberon/wirth/Sources/graph.zip
wget -nc http://www.paddedcell.com/projectoberon/wirth/Sources/apptools.zip
wget -nc http://www.inf.ethz.ch/personal/wirth/ProjectOberon/Sources/PCLink1.Mod.txt
cd ..
rm -rf work
mkdir work
cd work
for i in ../download/*.zip; do unzip $i; done
cp ../download/*.txt .
dos2unix *.txt
cd ..
cp *.Mod.txt *.Tool.txt work