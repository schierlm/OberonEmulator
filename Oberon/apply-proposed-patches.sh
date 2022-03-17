#!/bin/sh
set -e

patch -d work <../ProposedPatches/log-allocation-failures.patch -F 3
patch -d work <../ProposedPatches/mark-changing-compiler-output_cross.patch
