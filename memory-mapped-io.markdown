## Memory mapped IO

RISC Oberon uses memory mapped IO to access IO peripherals, instead of providing separate opcodes for accessing them like for example the x86 architecture uses.

This document provides a short summary about different Oberon systems/emulators and their supported memory addresses. Addresses not supported by hardware Oberon are written in **bold**.

Access | Address | System | Description
--- | --- | --- | ---
Read/Write | -128 to -68 | Hardware, Java, JS | **Palette**: Modify 16-color palette (if available)
Read | -64 | Hardware, C, Java, JS | **Timer**: Provide the number of milliseconds since startup
**Write** | **-64** | **Java, JS** | **Power Management**: When a millisecond timestamp in the future is written here, and since the last write to this address there have not been any keyboard or mouse input since last write to this address, suspend the (virtual) processor until either the timestamp is reached or some input has occurred
Read | -60 | Hardware, C (Only switch 0, boot-from-serial) | **Switches**: Provide access to hardware switches on the board. Emulators always return 0 here
Write | -60 | Hardware, C, Java, JS | **LEDs**: The low-order byte written is displayed on 8 (virtual) LEDs
Read/Write | -56 | Hardware, C, Java, JS | **RS232**: Write bytes to / read bytes from RS232 interface
Read | -52 | Hardware, C, Java, JS | **RS232 status**: Returns 3 if data is available, or 2 otherwise. Next two bits are used for second RS232 port.
**Write** | **-52** | **Java, JS** | **RS232 select**: Select which RS232 port is connected to address -56.
Read/Write | -48 / -44 | Hardware, C, Java | **SPI**
Read | -40 | Hardware, C, Java, JS | ** Mouse input / keyboard status **
Read | -36 | Hardware, C, Java | **Keyboard input**:Return PS2 scancodes in least significant byte
**Read** | **-36** | **Java, JS** | **Paravirtualized keyboard input**: The most significant byte, if not zero, denotes that the given character was typed. #13 is used for Return,  #26 for F1, rest is standard ASCII codes (including Backspace, Tab and Escape). Note that it is possible that one emulator (**Java**) returns both PS2 scancodes in least significant byte and paravirtualized input in most significant byte at the same time.
**Write** | **-32** | **Java** | **HostFS: Write a pointer to a HostFS structure, which is then filled by the host**
**Write** | **-28** | **Java, JS** | **Paravirtualized Disk**: When the two most significant bits are 00, remember the remaining bits as a (word-aligned) memory address. When they are 10, read the 1K sector denoted by the sector number in the remaining bits, and store it into the remembered memory address. When they are 11, read 1K from memory address and store it into the given sector number instead.
**Read/Write** | **-24** | **C, Java, JS** | **Clipboard Control**
**Read/Write** | **-20** | **C, Java, JS** | **Clipboard Data**
**Read/Write** | **-16** | **Java** | **Display Mode**: When read, returns width (high half) and height (low half) of screen, max 4096x4096. When written, switches to 256-color mode and aligns the beginning of the video memory sliding window with the given (word) address of the internal screen memory. As in 256-color mode a stride of 4096 bytes (1024 words) is used, only 21 pixel rows can be "seen" at any given time, therefore a sliding window is needed.
