## Creating images for the JS Oberon Emulator

[This document is outdated. All images that support the Hardware Enumerator will
likely work unmodified in the JS emulator as well. If not, see below.]

### Oberon code modifications

As the JS emulator uses a different disk and input interface than the real FPGA board,
some small changes are required to the Oberon image. In case your modified image does
not change the core classes, the easiest way is to start with an existing image, which
obviously already has made these changes. Otherwise, you'll need to apply some patches
to your system before compiling. It is still advised to compile your system (if it
supports being compiled from vanilla Oberon) one one of the existing images.

First changed interface is the disk interface. The original hardware uses SPI to
interface to a SD card and this both adds complexity to the emulator and makes
the startup process slower. The "paravirtual" disk interface of the emulator
is a lot simpler - the system writes a disk sector (1K) and a memory address
to MMIO, and the sector "magically" appears at that address. In addition, the filesystem
offset has been reduced to zero.

The second changed interface is the keyboard. The original hardware uses PS/2
scancodes which are translated to ASCII by the Input module. The JS emulator receives
ASCII/Unicode characters from the browser, so translating them to PS/2 scancodes just to
translate them back in the Input module both adds complexity and limits flexibility
(think about different keyboard layouts).

The third change is in the main loop. On real hardware, the main loop busy-waits for
input and runs tasks (like the GC) in between. While this can be handled in an
emulator (by some educated guessing when a system is idle, to return CPU time to the
host, and by limiting the number of cycles the emulator can run at once), for JavaScript
(and websites in general) it is important that they do not use more CPU than needed.
Therefore, the main loop contains an extra instruction that regularly writes the
millisecond timestamp of the next task to a MMIO address. The emulator will use this
to freeze the CPU if no task is pending and no input events occurred recently.

As a fourth change, the Display module will read the desired screen resolution and
adjust its display accordingly. You will probably know this from other emulators
as well.

### Minimal patch for vanilla Oberon

```diff
--- Kernel.Mod.txt
+++ Kernel.Mod.txt
@@ -222,14 +222,14 @@
 
   PROCEDURE GetSector*(src: INTEGER; VAR dst: Sector);
   BEGIN src := src DIV 29; ASSERT(SYSTEM.H(0) = 0);
-    src := src * 2 + FSoffset;
-    ReadSD(src, SYSTEM.ADR(dst)); ReadSD(src+1, SYSTEM.ADR(dst)+512) 
+    SYSTEM.PUT(-28, SYSTEM.ADR(dst));
+    SYSTEM.PUT(-28, 080000000H + src);
   END GetSector;
   
   PROCEDURE PutSector*(dst: INTEGER; VAR src: Sector);
   BEGIN dst := dst DIV 29; ASSERT(SYSTEM.H(0) =  0);
-    dst := dst * 2 + FSoffset;
-    WriteSD(dst, SYSTEM.ADR(src)); WriteSD(dst+1, SYSTEM.ADR(src)+512)
+    SYSTEM.PUT(-28, SYSTEM.ADR(src));
+    SYSTEM.PUT(-28, 0C0000000H + dst);
   END PutSector;
 
 (*-------- Miscellaneous procedures----------*)
--- Input.Mod.txt
+++ Input.Mod.txt
@@ -2,7 +2,7 @@
   IMPORT SYSTEM;
 
   CONST msAdr = -40; kbdAdr = -36;
-  VAR kbdCode: BYTE; (*last keyboard code read*)
+  VAR kbdCode: INTEGER; (*last keyboard code read*)
     Recd, Up, Shift, Ctrl, Ext: BOOLEAN;
     KTabAdr: INTEGER;  (*keyboard code translation table*)
     MW, MH, MX, MY: INTEGER; (*mouse limits and coords*)
@@ -15,15 +15,8 @@
   BEGIN
     IF SYSTEM.BIT(msAdr, 28) THEN
       SYSTEM.GET(kbdAdr, kbdCode);
-      IF kbdCode = 0F0H THEN Up := TRUE
-      ELSIF kbdCode = 0E0H THEN Ext := TRUE
-      ELSE
-        IF (kbdCode = 12H) OR (kbdCode = 59H) THEN (*shift*) Shift := ~Up
-        ELSIF kbdCode = 14H THEN (*ctrl*) Ctrl := ~Up
-        ELSIF ~Up THEN Recd := TRUE (*real key going down*)
-        END ;
-        Up := FALSE; Ext := FALSE
-      END
+      kbdCode := kbdCode DIV 1000000H;
+      Recd := TRUE
     END;
   END Peek;
 
@@ -35,10 +28,7 @@
   PROCEDURE Read*(VAR ch: CHAR);
   BEGIN
     WHILE ~Recd DO Peek() END ;
-    IF Shift OR Ctrl THEN INC(kbdCode, 80H) END; (*ctrl implies shift*)
-  (* ch := kbdTab[kbdCode]; *)
-    SYSTEM.GET(KTabAdr + kbdCode, ch);
-    IF Ctrl THEN ch := CHR(ORD(ch) MOD 20H) END;
+    ch := CHR(kbdCode);
     Recd := FALSE
   END Read;
 
--- Oberon.Mod.txt
+++ Oberon.Mod.txt
@@ -360,7 +360,8 @@
   PROCEDURE Loop*;
     VAR V: Viewers.Viewer; M: InputMsg; N: ControlMsg;
        prevX, prevY, X, Y, t: INTEGER; keys: SET; ch: CHAR;
-  BEGIN
+       minTime: INTEGER;
+  BEGIN minTime := 0;
     REPEAT
       Input.Mouse(keys, X, Y);
       IF Input.Available() > 0 THEN Input.Read(ch);
@@ -382,9 +383,15 @@
           IF Y >= Display.Height THEN Y := Display.Height END ;
           M.Y := Y; M.keys := keys; V := Viewers.This(X, Y); V.handle(V, M); prevX := X; prevY := Y
         END;
+        SYSTEM.PUT(-64, minTime);
         CurTask := CurTask.next; t := Kernel.Time();
         IF t >= CurTask.nextTime THEN
           CurTask.nextTime := t + CurTask.period; CurTask.state := active; CurTask.handle; CurTask.state := idle
+          ;minTime := CurTask.nextTime;
+          FOR t := 1 TO NofTasks DO
+            CurTask := CurTask.next;
+            IF CurTask.nextTime < minTime THEN minTime := CurTask.nextTime END;
+          END
         END
       END
     UNTIL FALSE
--- Display.Mod.txt
+++ Display.Mod.txt
@@ -180,6 +180,11 @@
   END ReplPattern;
 
 BEGIN Base := base; Width := 1024; Height := 768;
+  SYSTEM.GET(base, arrow);
+  IF arrow = 53697A65H THEN
+    SYSTEM.GET(base+4, Width);
+    SYSTEM.GET(base+8, Height);
+  END;
   arrow := SYSTEM.ADR($0F0F 0060 0070 0038 001C 000E 0007 8003 C101 E300 7700 3F00 1F00 3F00 7F00 FF00$);
   star := SYSTEM.ADR($0F0F 8000 8220 8410 8808 9004 A002 C001 7F7F C001 A002 9004 8808 8410 8220 8000$);
   hook := SYSTEM.ADR($0C0C 070F 8707 C703 E701 F700 7F00 3F00 1F00 0F00 0700 0300 01$);
```

### Deploying the emulator to a webserver

After you have built the new image on an existing emulator somewhere on the Web, you can use the "Save PNG"
option to save the disk image as a PNG (graphics image file). This is done as older browsers (IEs especially)
do not support that JavaScript functions load arbitrary binary files via HTTP, but images can be loaded and
parsed just fine. In addition, PNG files are compressed, reducing both bandwidth needed (if your webserver does
not do transparent GZIP compression) and disk space on the webserver.

The "Save PNG" option also allows changing the boot ROM and/or the RAM size, but these options are outside
the scope of this document.

In addition to that PNG file, you will need the following files from this directory on your webserver:

- [`emu.html`](emu.html)
- [`risc.js`](risc.js)
- [`webdriver.js`](webdriver.js)

You will also need a `config.json`, that includes the name of your PNG file(s).
See [`config.json.example`](config.json.example).

In case you want to test the files locally (via `file://` protocol) in the browser, keep in mind that
modern browsers limit access to local files to JavaScript. So for the Chrome browser, you'd have
to start it with `--allow-file-access-from-files` switch. Or deploy to a webserver running on localhost.

In case the resulting PNG is too large, consider
[defragmenting it](https://github.com/schierlm/Oberon2013Modifications/tree/master/DefragmentFreeSpace),
if your Oberon system supports that.
