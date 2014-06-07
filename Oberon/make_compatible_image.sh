#!/bin/sh
set -e

./get-source.sh
./apply-proposed-patches.sh
./apply-emulator-patches.sh
./derive-files.sh
