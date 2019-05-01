#!/bin/sh
set -e
if [ -z "$1" ];  then
    echo "Filename missing"
    exit 1
fi

BYTELEN=$(grep -Eboa '!!TRIM!!-{496}!!TRIM!!' $1 | sed s/:.*//)
dd if=$1 of=$1_trimmed bs=$BYTELEN count=1
