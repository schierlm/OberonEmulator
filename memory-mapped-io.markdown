## Memory mapped IO

RISC Oberon uses memory mapped IO to access IO peripherals, instead of providing separate opcodes for accessing them like for example the x86 architecture uses.

This document provides a short summary about different Oberon systems/emulators and their supported memory addresses. Addresses not supported by hardware Oberon are written in **bold**.

Access | Address | System | Description
--- | --- | --- | ---
Read | -64 | Hardware, C, Java, JavaScript | **Timer**: Provide the number of milliseconds since startup
**Write** | **-64** | **Java, JavaScript** | **Power Management**: When a millisecond timestamp in the future is written here, and since the last write to this address there have not been any keyboard or mouse input since last write to this address, suspend the (virtual) processor until either the timestamp is reached or some input has occurred
Read | -60 | Hardware | **Switches**: Provide access to hardware switches on the board. Emulators always return 0 here
Write | -60 | Hardware, C, Java, JavaScript | **LEDs**: The low-order byte written is displayed on 8 (virtual) LEDs
Read/Write | -56 | Hardware, C (PCLink only), Java | **RS232**: Write bytes to / read bytes from RS232 interface
Read | -52 | Hardware, C (PCLink only), Java | **RS232 status**: Returns 3 if data is available, or 2 otherwise
Read/Write | -48 / -44 | Hardware, C, Java | **SPI**
Read | -40 | Hardware, C, Java, JavaScript | ** Mouse input / keyboard status **
Read | -36 | Hardware, C, Java | **Keyboard input**:Return PS2 scancodes in least significant byte
**Read** | **-36** | **Java, JavaScript** | **Paravirtualized keyboard input**: The most significant byte, if not zero, denotes that the given character was typed. #13 is used for Return,  #26 for F1, rest is standard ASCII codes (including Backspace, Tab and Escape). Note that it is possible that one emulator (**Java**) returns both PS2 scancodes in least significant byte and paravirtualized input in most significant byte at the same time.
**Read/Write** | **-32** | **Java, JavaScript** | **Paravirtualized Clipboard**: When read, return the current length of the clipboard text. When written with MSB = 0, the 31 other bits denote a (word-aligned) memory address from which to load text (null terminated) and fill the system clipboard. When written with MSB = 1, the current clipboard content is stored at the (word-aligned) address denoted by the other 31 bits.
**Write** | **-28** | **Java, JavaScript** | **Paravirtualized Disk**: When the two most significant bits are 00, remember the remaining bits as a (word-aligned) memory address. When they are 10, read the 1K sector denoted by the sector number in the remaining bits, and store it into the remembered memory address. When they are 11, read 1K from memory address and store it into the given sector number instead.
**Read/Write** | **-24** | **C, Java** | **Clipboard Control**
**Read/Write** | **-20** | **C, Java** | **Clipboard Data**
