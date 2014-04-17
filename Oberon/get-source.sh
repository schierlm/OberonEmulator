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
cd ..
rm -rf work
mkdir work
cd work
for i in ../download/*.zip; do unzip $i; done
dos2unix *.txt
cd ..
cp *.Mod.txt *.Tool.txt work