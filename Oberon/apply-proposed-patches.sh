#!/bin/sh
set -e

patch -d work <../ProposedPatches/log-allocation-failures.patch
patch -d work <../ProposedPatches/mark-changing-compiler-output.patch
