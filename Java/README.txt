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

java -jar OberonEmulator.jar <imagefile>

For additional options (like network emulation), run

java -jar OberonEmulator.jar

without additional parameters.


Limiting features
~~~~~~~~~~~~~~~~~

As different Oberon emulators have different additional (paravirtualized)
features, and there is also a native RISC version that has different features,
features of this emulator can be disabled for testing interoperability of
disk images.

This is done by specifying a feature set and optionally including or excluding
features individually. Feature set and feature names are case insensitive but
written in ALL_UPPERCASE here for clarity.

Feature sets available:
  NONE   - No special features at all
  ALL    - All features supported by the emulator (default)
  NATIVE - Features that are supported by the native RISC implementation
  JS     - Features supported by the JavaScript emulator
  C      - Features supported by Peter De Wachter's C emulator

Individual features available (and their inclusion in [N]ative, [C] and [J]S):

                         NCJ
  FLOATING_POINT         +++  FAD/FSB/FML/FDV opcodes
  BW_GRAPHICS            +++  Black and white graphics
  COLOR_GRAPHICS         ---  256-color graphics with dynamic screen size
  COLOR16_GRAPHICS       --+  16-color graphics with dynamic screen size
  DYNSIZE_GRAPHICS       -++  dynamic screen size (determined at startup)
  DYNAMIC_RESOLUTION     --+  change resolution at runtime
  SEAMLESS_RESIZE        --+  guest size tracks emulator window
  NATIVE_KEYBOARD        ++-  keyboard events using native PS/2 opcodes
  PARAVIRTUAL_KEYBOARD   --+  keyboard events using ASCII keycodes
  WILDCARD_PCLINK        --+  downloading multiple files with wildcards
  NATIVE_DISK            ++-  Disk access via SPI
  PARAVIRTUAL_DISK       --+  Disk access via direct memory access
  PARAVIRTUAL_CLIPBOARD  -++  Clipboard access
  HOST_FILESYSTEM        ---  Host filesystem
  SPI                    ++-  Serial Peripheral Interface
  SERIAL                 +++  RS232 serial port
  MULTI_SSERIAL          --+  More than one serial port
  SPI_NETWORK            +--  (wireless) network via SPI
  PARAVIRTUAL_WIZNET     --+  WizNet compatible paravirtual network
  POWER_MANAGEMENT       --+  Interface for communicating idle times
  LARGE_ADDRESS_SPACE    -+-  4GB address space instead of 1MB

Disk images
~~~~~~~~~~~

This emulator contains three classes of disk images. Each of them support
different feature set, but all of them should work in other systems or
emulators that either support hardware enumerator or are 100% compatible to
the original board.

JSMinimalDiskImage, JSFullDiskImage, JSFullWithRescueDiskImage, JSColorDiskImage:
  These images are compatible with the JavaScript emulator and are useful as
  a base for building custom images for it.

CompatibleDiskImage:
  This image try to stay compatible with native hardware and emulators, without
  including support for features only present in this emulator.

JavaDiskImage, JavaWithRescueDiskImage:
  These images use all the features of this emulator, like paravirtualized
  hardware devices, host filesystem, and various color graphics modes. To use
  the HostFS feature, first boot the image and use PCLink's globbing feature
  to copy all files (*) into a directory of the host. Then you can enable
  HostFS pointing to the same directory. If preferred, you can trim the disk
  image (without rescue system) to the first 64 kilobytes, as the rest is
  not used.

License
~~~~~~~

See license.txt.


Contact me
~~~~~~~~~~

Please send bug reports and suggestions to <schierlm@gmx.de> or via GitHub:
https://github.com/schierlm/OberonEmulator
