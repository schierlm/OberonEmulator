#!/bin/sh
set -e

patch -d work <pclink-globbing.patch
patch -d work <pclink-two-ports.patch
