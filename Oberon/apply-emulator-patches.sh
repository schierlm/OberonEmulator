#!/bin/sh
set -e

patch -d work <detect-screen-size.patch
patch -d work <larger-screen-size.patch
patch -d work <pclink-globbing.patch
patch -d work <pclink-two-ports.patch
patch -d work <Oberon2013Modifications/RealTimeClock/EmulatorSupport.patch