OberonEmulator in Java
======================


Introduction
~~~~~~~~~~~~

This is an emulator for RISC Oberon, as designed by Niklaus Wirth (NW) and
Jürg Gutknecht (JG), and described in 

Project Oberon - The Design of an Operating System, a Compiler, and a Computer
Revised Edition 2013  Niklaus Wirth  Jürg Gutknecht  ISBN 0-201-54428-8

available online on http://www.projectoberon.com/

Apart from the original hardware peripherals (RS232 and SD card) it also
supports additional virtualized hardware (like access to the host clipboard)
that make it more comfortable to use.

System requirements
~~~~~~~~~~~~~~~~~~~

This program requires Java 7 or higher. Download it from www.java.com.


Usage
~~~~~

Start the emulator like this

java -jar OberonEmulator.jar 1024 768 FullDiskImage.Bin BootLoad.Bin PCLink

For additional options, run

java -jar OberonEmulator.jar

without additional parameters.


License
~~~~~~~

See license.txt.


Contact me
~~~~~~~~~~

Please send bug reports and suggestions to <schierlm@gmx.de> or via GitHub:
https://github.com/schierlm/OberonEmulator
