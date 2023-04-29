## Hardware enumerator

### Versioning

This document describes version 1.0.3 of the hardware enumerator. New (major) versions
may introduce incompatible changes; therefore, when software is unaware of the
implemented version, the best way to handle is to assume no hardware enumerator
to be present. Minor versions are backwards compatible. Patch versions only add new
hardware IDs and do not change any other behavior.

### Motivation

While Project Oberon was originally designed to run on a single board, there are
now multiple boards (and emulators) with varying capabilities available. Not all
of them provide the same hardware support. As a consequence, when moving a disk
(image) from one system to another one, often parts of the system need to be
recompiled to take advantage of the changed hardware configuration.

This document tries to provide an interface that can be called by the software
to obtain insight about the installed hardware.

### Conventions for magic values

Many values returned by the hardware enumerator are 32-bit magic values. To make
them more human readable, they are sometimes written as 4-byte ASCII strings in
single quotation marks, interpreted in network byte order (e.g. the value
`41626364H` may be written as `'Abcd'`). In other cases the values may be
written in hexadecimal (suffixed by `H`) or decimal.

### Basic concept

Information in the hardware enumerator is divided into multiple hardware
descriptors, each indexed by a 32-bit integer value and consisting of a finite
list of 32-bit integer values. Zero values at the end of the list are not
significant, i.e. it cannot be determined by software if they are present or
not. Hardware descriptors are expected not to change after bootup, so that the
software can cache them if desired. Of course they may change between different
boots as the hardware may have changed.

The main hardware descriptor uses an index of `0`. Its first entry is the
version number (`1` at present), and each of the following entries
describes the hardware ID one kind of hardware present. Additional information
about that kind of hardware may (in case the hardware requires it) be found in
another hardware descriptor whose index is the hardware ID.

A hardware ID (called "enhanced") may supersede another (less capable) hardware ID. If a
device supports both the superseded and the enhanced hardware ID, the enhanced ID
has to appear first in the main hardware descriptor. Software is expected to
skip unsupported hardware IDs and find the superseded entry that way. Once the
enhanced hardware ID has been seen, software is expected to skip the superseded ID
and only parse the enhanced one. *So far, no hardware IDs have been superseded.*

Access to the hardware enumerator is done by accessing the MMIO port `-4` (the
last word available). When reading from this port after bootup, an infinite
series of `0` words are returned. When writing a value to the port, the hardware
descriptor indexed by that value is returned. After the whole hardware
descriptor has been read, an infinite series of `0` words is returned again.

### Fallback mechanism

When reading MMIO address `-4` on the original board, only `0` words are
returned. Therefore, the main hardware descriptor appears to be version 0. In this case,
(and also in case a higher version is read), the system may assume the hardware
configuration to be as follows:

- **0**: 1, 'mVid', 'Timr', 'Swtc', 'LEDs', 'SPrt', 'SPIf', 'MsKb'
- **'mVid'**: 1, 0, 1024, 768, 128, 0E7F00H
- **'Timr'**: -64
- **'Swtc'**: 12, -60
- **'LEDs'**: 8, -60
- **'SPrt'**: 1, -52, -56
- **'SPIf'**: -44, -48, 'SDCr', 'wNet', 0
- **'MsKb'**: -40, -36

## Assigned hardware IDs

### `mVid`: Monochrome video support

The board supports monochrome video, in one or more resolutions.

Values in the `mVid` descriptor (in that order):

- Number of supported video modes (at least 1). Monochrome video modes start
  with mode 0.
- MMIO address to read to get the current video mode, or to write to set it. May
  be 0 if only one mode is supported.

*The following values are repeated for each video mode:*
- Horizontal resolution (width)
- Vertical resolution (height)
- Scan line span in bytes (i.e. how many bytes to increment the address to get to the pixel below)
- Base address of framebuffer

### `mDyn`: Monochrome video dynamic resolution

The board supports monochrome video with dynamic resolutions. Probably only
relevant for emulated systems which can choose arbitrary resolutions. The MMIO
address used for this may be the same as the MMIO address for choosing fixed
resolutions. To choose a dynamic resolution, the value's bit 31 needs to be
cleared and 30 needs to be set. The 15 upper remaining bits (29-15) designate
the width, the 15 lower bits (14-0) designate the height. When reading back the
MMIO address, the same value is returned if the switch has been successful. By
providing 15 bits, a maximum resolution of 32767×32767 is supported. (Note that
the standard mouse interface only supports a 4096×4096 screen.)

In some emulators, the resolution of the host window can be passed to the
software inside the emulator (seamless resize). When supported, a switch to a
dynamic resolution of 0×0 will switch the resolution to the preferred resolution,
which can be read back. When first invoked, the emulator may use this as a hint
to make the window resizable.

Values in the `mDyn` descriptor:
- MMIO address to write the resolution to (may be same as used in `mVid`
  descriptor)
- Maximum horizontal resolution (width)
- Maximum vertical resolution (height)
- Increment in horizontal resolution (valid resolutions need to be a multiple of this value)
- Increment in vertical resolution (likely 1)
- Scan line span in bytes, or -1 to determine it dynamically from the horizontal resolution
- Base address of framebuffer
- `1` if seamless resize is supported, else `0`.

### `16cV`: 16-color video support

The board supports 16-color video mode. It may support both monochrome and 16-color modes, therefore the first color mode may not be value 0.

Values in the `16cV` descriptor (in that order):

- Number of supported video modes (at least 1)
- Number of the first supported video mode
- MMIO address to read to get the current video mode, or to write to set it. May
  be 0 if only one mode is supported, and may be the same address as used in `mVid` and/or `mDyn`.
- MMIO address where the palette starts. May be 0 if palette changes are not supported.

*The following values are repeated for each video mode:*
- Horizontal resolution (width)
- Vertical resolution (height)
- Scan line span in bytes (i.e. how many bytes to increment the address to get to the pixel below)
- Base address of framebuffer

### `16cD`: 16-color video dynamic resolution

The board supports 16-color video with dynamic resolutions. Probably only
relevant for emulated systems which can choose arbitrary resolutions. The MMIO
address used for this may be the same as the other video related MMIO addresses.
To choose a dynamic resolution, the value's bit 31 and 30 need to be set. The 15
upper remaining bits (29-15) designate the width, the 15 lower bits (14-0)
designate the height. When reading back the MMIO address, the same value is
returned if the switch has been successful. By providing 15 bits, a maximum
resolution of 32767×32767 is supported. (Note that the standard mouse interface
only supports a 4096×4096 screen.)

In some emulators, the resolution of the host window can be passed to the
software inside the emulator (seamless resize). When supported, a switch to a
dynamic resolution of 0×0 will switch the resolution to the preferred resolution,
which can be read back. When first invoked, the emulator may use this as a hint
to make the window resizable.

Values in the `16cD` descriptor:
- MMIO address to write the resolution to
- MMIO address where the palette starts. May be 0 if palette changes are not supported.
- Maximum horizontal resolution (width)
- Maximum vertical resolution (height)
- Increment in horizontal resolution (valid resolutions need to be a multiple of this value)
- Increment in vertical resolution (likely 1)
- Scan line span in bytes, or -1 to determine it dynamically from the horizontal resolution
- Base address of framebuffer
- `1` if seamless resize is supported, else `0`.

### `8bcV`, `8bcD`: 8-bit-color video support and dynamic resolution

These descriptors work the same as `16cV` and `16cD`, only that they describe
256-color (8-bit color) mode.

To choose a dynamic resolution, the value's bit 31 needs to be set, and bit 30
as well as the most significant bit of the width and height need to be cleared
(This requirement reduces the resolution to 16384×16384, but provides two more
available bits for further enhancements).

### `vRTC`: Provide a real time clock hint

Probably most useful for emulators. It may be used to provide the clock time (as
returned by `Kernel.Clock`) for a time that corresponds to a timer value that is
in the past, but larger than zero.

Values in the `vRTC` descriptor:

- Timer value in the past
- Clock value corresponding to the same time.

The system may then "tick" the real time clock forwards until it reaches the
current timer value.

### `Timr`: Timer (with optional power manaagement)

Defines an MMIO address to obtain the current timer and/or trigger power
management. For power management, the CPU needs a way to detect input (keyboard,
mouse, serial) to wake up without actually running (e.g. interrupts). It also
needs to keep track if any input happened since the last write to the power
management port. When a value is written to the power management port and no
input has happened since the last write, the board may pause the CPU until
either input happens or the timer is larger than the written value.

Values in the `Timr` descriptor

- MMIO address for the timer / power management
- `1` in case power management is supported, `0` otherwise.

### `Swtc`: Switches

Defines an MMIO address where switches can be read

Values in the `Swtc` descriptor:
- Number of switches
- MMIO Address for switches (lowest bits are used)

### `LEDs`: LEDs

Defines an MMIO address where LEDs can be written

Values in the `LEDs` descriptor:
- Number of LEDs
- MMIO address to set LEDs (lowest bits are used)

### `SPrt`: Serial (RS232) port(s)

Defines MMIO addresses used for serial port(s).

Values in the `SPrt` descriptor:
- Number of serial ports
- MMIO address for reading status. Also used for selecting port (by writing
  0-based index), if more than one port supported.
- MMIO address for reading/writing data

### `SPIf`: Serial Peripheral Interface

Defines the MMIO address for SPI

Values in the `SPIf` descriptor:
- MMIO address for SPI control
- MMIO address for SPI data
- Zero-terminated list of device IDs connected to the SPI, the first value
  being for device #1, etc.

Known device IDs:

- `SDCr`: SD Card
- `wNet`: Wireless network (as defined in Chapter 10 of the Project Oberon book)

### `MsKb`: Mouse and keyboard

Defines mouse and keyboard input.

Values in the `MsKb` descriptor:
- MMIO address for mouse input / keyboard status
- MMIO address for keyboard input
- Keyboard input mode: `0`: Standard PS/2, `1`: Paravirtualized (ASCII/Unicode codepoints), `2`: Both.

### `JSKb`: Raw JavaScript Event keyboard emulation

This is probably only used by JavaScript emulators, as it is designed around the
<a href="https://w3c.github.io/uievents/#events-keyboardevents">W3C UI Events</a> specification.

When an emulator signals `JSKb´ support, the keyboard still starts in the keyboard mode defined by the
`MsKb` descriptor. Writing 0 to the MMIO address will return to this mode as well.

Writing any other value is treated as a pointer to the record specified below:

    RECORD
      code: ARRAY 32 OF CHAR;
      key:  ARRAY 16 OF CHAR;
    END;

After reading a key, the record is filled with the `code` and `key` attributes.

The other attributes are encoded as bit field in the read key value:

- Bits 0 to 15: If the `key` attribute is a single character, its unicode codepoint.
  If the `key` attribute is empty, `0`. For all other `key` attributes, `0FFFFh`.
- Bit 16: `shiftKey` attribute.
- Bit 17: `ctrlKey` attribute.
- Bit 18: `altKey` attribute.
- Bit 19: `metaKey` attribute.
- Bit 20: `isComposing` attribute.
- Bit 21: `repeat` attribute.
- Bits 22 to 23: Event type. 1=Press, 2=Up, 3=Down. 0 if no keyboard event available.
- Bits 24 to 27: `location` attribute
- Bits 28 to 31: reserved (0).

Values in the `JSKb` descriptor:
- MMIO address

### `HsFs`: Host filesystem

Used in emulators to access files on the host.

Values in the `HsFs` descriptor:
- MMIO address for host FS

### `vDsk`: Paravirtualized disk

Used in emulators for faster disk access and/or to keep the emulator simpler by
skipping SPI implementation.

Values of the `vDsk` descriptor:
- MMIO address for paravirtualized disk.

### `vClp`: Paravirtualized clipboard

Used in emulators to provide access to the host clipboard

Values of the `vClp` descriptor:
- MMIO address for paravirtualized clipboard control
- MMIO address for paravirtualized clipboard data

### `vNet`: Paravirtualized WizNet compatible network

Used in emulators to provide access to the paravirtualized network

Values of the `vNet` descriptor:
- MMIO address

### `vHTx': Paravirtual Host Transfer

Used in emulators to enable the guest to actively copy files from/to the host's native
filesystem. Unlike Host FS this filesystem does not follow Oberon filename semantics and
only allows copy operations. Optionally, the interface may also allow to run commands on
the host and return their output. The output text should include a textual representation
of the process' exit status (if supported by the host).

Values of the `vHTx` descriptor:
- MMIO address

### `DbgC`: Debug console (for emulators)

Provided by emulators to output null-terminated debug messages
to some debug console (e.g. stdout), to provide a slightly
higher-level debug interface than just LEDs.

Values of the `DbgC` descriptor:
- MMIO address

### `DChg`: Disk Change indicator (primarily for emulators)

Provided by emulators to inform the user about disk changes.

When writing 0 to the MMIO address, mark the disk as clean (unchanged).
When writing 1 to the MMIO address, mark it as dirty (changed).
When writing 2 to the MMIO address, stop auto-updating disk status on every sector write.
When writing 3 to the MMIO address, enable auto-updating disk status on every sector write.

Values of the `DChg` descriptor:
- MMIO address

### `ICIv`: Instruction cache invalidation

When present, the CPU has an instruction cache that is not automatically
invalidated when memory (or data cache) is written to. Therefore, before
newly written code can be executed, the instruction cache needs to be
invalidated (and any data cache flushed) by writing a `0` to a MMIO
address. This can also be used by emulators that do just-in-time
instruction translation to efficiently flush their translated instructions.

In case a value other than `0` is written to the MMIO address, the
hardware may also invalidate all its instruction cache. Or (if feasible) it
can only invalidate those instructions whose memory address is greater or
equal than the written value.

Values of the `ICIv` descriptor:
- MMIO address

### 'Rset': Reset vector

Defines how to reset the system. There are two ways of reset: A soft reset
(that leaves the RAM intact but resets execution back to the Oberon loop)
and a hard reset (That resets the RAM).

Reset is performed by continuing execution at a specific address in ROM.
If the system only provides an address to perform a soft reset, the hard
reset can be performed by setting register LNK (R15) to zero before
jumping to the soft reset target.

Values of the `Rset` descriptor:
- Jump target for soft reset
- Jump target for hard reset, or 0 if there is no dedicated one

### `Boot`: Reserved for bootloader

This descriptor can be used by the system to communicate information to the
boot loader which may change between reboots. Its format is unspecified,
user space should ignore it.
