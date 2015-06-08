#!/bin/sh
set -e

patch -d work <../ProposedPatches/initialize-local-variables.patch
patch -d work <../ProposedPatches/system-clear-init-buf.patch
patch -d work <../ProposedPatches/log-allocation-failures.patch
patch -d work <../ProposedPatches/trap-backtrace.patch
patch -d work <../ProposedPatches/better-display-compatibility.patch
patch -d work <../ProposedPatches/mark-changing-compiler-output.patch
