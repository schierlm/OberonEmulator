#!/bin/sh
set -e

patch -d work <../ProposedPatches/initialize-local-variables.patch
patch -d work <../ProposedPatches/log-allocation-failures.patch
patch -d work <../ProposedPatches/trap-backtrace.patch
patch -d work <../ProposedPatches/better-display-compatibility.patch
patch -d work <../ProposedPatches/mark-changing-compiler-output.patch
patch -d work <../ProposedPatches/memory-allocator-robust-fail.patch
patch -d work <../ProposedPatches/filesystem-encapsulation.patch
patch -d work <../ProposedPatches/fix-aliased-modules.patch
patch -d work <../ProposedPatches/graphicframes-initialize-tbuf.patch
patch -d work <../ProposedPatches/add-defragment-support.patch
