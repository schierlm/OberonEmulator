#!/bin/sh
set -e

./get-source.sh

patch -d work <detect-screen-size.patch

./derive-files.sh