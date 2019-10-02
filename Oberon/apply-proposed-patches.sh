#!/bin/sh
set -e

patch -d work <Oberon2013Modifications/ConvertEOL/ConvertEOL.patch
patch -d work <Oberon2013Modifications/DefragmentFreeSpace/DefragSupport.patch
patch -d work <Oberon2013Modifications/DrawAddons/MoreClasses.patch
patch -d work <Oberon2013Modifications/RealTimeClock/RealTimeClock.patch
patch -d work <Oberon2013Modifications/ZeroLocalVariables/ZeroLocalVariables.patch
patch -d work <../ProposedPatches/log-allocation-failures.patch
mv work/BootLoad.Mod.txt work/BootLoad0.Mod.txt
cp wirth-personal/people.inf.ethz.ch/wirth/ProjectOberon/Sources/BootLoad.Mod.txt work
patch -d work <Oberon2013Modifications/DoubleTrap/DoubleTrap.patch
patch -d work <Oberon2013Modifications/TrapBacktrace/PREPATCH_after_DoubleTrap.patch
dos2unix work/BootLoad.Mod.txt
patch -d work <Oberon2013Modifications/TrapBacktrace/TrapBacktrace.patch
mv work/BootLoad0.Mod.txt work/BootLoad.Mod.txt
patch -d work <Oberon2013Modifications/TrapBacktrace/POSTPATCH_after_DoubleTrap.patch
patch -d work <Oberon2013Modifications/OnScreenKeyboard/InjectInput.patch
patch -d work <../ProposedPatches/better-display-compatibility.patch
patch -d work <../ProposedPatches/mark-changing-compiler-output.patch
patch -d work <Oberon2013Modifications/BugFixes/NoMemoryCorruptionAfterMemoryAllocationFailure.patch
patch -d work <../ProposedPatches/filesystem-encapsulation.patch
patch -d work <Oberon2013Modifications/BugFixes/FixAliasedModules.patch
patch -d work <Oberon2013Modifications/BugFixes/InitializeGraphicFramesTbuf.patch
