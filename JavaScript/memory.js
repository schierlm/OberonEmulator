var ROMStart = 0x0FE000;
var IOStart = 0x0FFFC0;
var MemSize = 0x100000;
var MemWords = (MemSize / 4);
var DisplayStart = 0x0E7F00;

var ram = new Int32Array(MemSize/4);

function memReadWord(wordAddress, mapROM) {
	if (mapROM && wordAddress >= ROMStart / 4) {
		return disk[0][wordAddress - ROMStart / 4];
	} else if (wordAddress >= IOStart / 4) {
		return memReadIOWord(wordAddress);
	} else {
		return ram[wordAddress];
	}
}

function memWriteWord(wordAddress, value) {
		if (wordAddress >= IOStart / 4) {
			memWriteIOWord(wordAddress, value);
		} else if (wordAddress >= DisplayStart / 4) {
			ram[wordAddress] = value;
			memWriteIMGWord(wordAddress, value);
		} else {
			ram[wordAddress] = value;
		}
}

var startMillis = Date.now(), waitMillis = 0;
var paravirtPtr = 0;
var keyBuf = [];
var mouse = 0;

function memReadIOWord(wordAddress) {
	switch (wordAddress * 4 - IOStart) {
	case 0: {
		return (Date.now() - startMillis) | 0;
	}
	case 24: {
		var _mouse = mouse;
		if (keyBuf.length > 0) {
			_mouse |= 0x10000000;
		}
		return _mouse;
	}
	case 28: {
		if (keyBuf.length > 0) {
			return keyBuf.shift();
		}
		return 0;
	}
	case 32: {
		return clipboard.value.length;
	}
	default: {
			return 0;
	}
	}
}

function memWriteIOWord(wordAddress, value) {
	switch (wordAddress * 4 - IOStart) {
	case 0: {
		if (waitMillis == -1)
			waitMillis = 0;
		else
			waitMillis = startMillis + value;
		break;
	}
	case 4: {
		var leds = value;
		for(var i=0; i<8; i++) {
			var cn = "led";
			if (leds & (1 << i))
				cn = "led lit";
			document.getElementById("led"+i).className=cn;
		}
		break;
	}
	case 32: {
		var ptr = value & 0x7FFFFFFF;
		if ((value & 0x80000000) != 0) {
			var sb = "";
			var wrd = memReadWord(ptr / 4, false);
			outer: while (true) {
				for (var i = 0; i < 4; i++) {
					var b = ((wrd >> (8 * i)) & 0xFF);
					if (b == 0)
						break outer;
					sb += String.fromCharCode(b);
				}
				ptr += 4;
				wrd = memReadWord(ptr / 4, false);
			}
			clipboard.value = sb.split("\r").join("\n");
		} else {
			var text = clipboard.value.split("\n").join("\r");
			for (var i = 0; i < text.length; i += 4) {
				var vl = 0;
				for (var j = 0; j < Math.min(text.length - i, 4); j++) {
					vl |= ((text.charCodeAt(i + j)) & 0xFF) << (j * 8);
				}
				memWriteWord((ptr + i) / 4, vl);
			}
		}
	}
	case 36: {
		// paravirtualized storage
		if ((value & 0xC0000000) == 0) { // setPtr
			paravirtPtr = value | 0;
		}
		if ((value & 0xC0000000) == (0x80000000|0)) { // read
			var sector = (value - 0x80000000)|0;
			var s = disk[sector|0];
			if (!s) s = new Int32Array(256);
			ram.set(s, paravirtPtr / 4);
		}
		if ((value & 0xC0000000) == (0xC0000000|0)) { // write
			var sector = (value - 0xC0000000)|0;
			var s = new Int32Array(256);
			s.set(ram.subarray(paravirtPtr/4, paravirtPtr/4 + 256))
			disk[sector|0] = s;
		}
		break;
	}
	}
}

function hwMouseMoved(mouse_x, mouse_y) {
	var oldMouse = mouse;
	if (mouse_x >= 0 && mouse_x < 4096) {
		mouse = (mouse & ~0x00000FFF) | mouse_x;
	}
	if (mouse_y >= 0 && mouse_y < 4096) {
		mouse = (mouse & ~0x00FFF000) | (mouse_y << 12);
	}
	if (mouse != oldMouse) {
		waitMillis = -1;
		cpuResume();
	}
}

function hwMouseButton(button, down) {
	if (button >= 1 && button < 4) {
		var bit = 1 << (27 - button);
		if (down) {
			mouse |= bit;
		} else {
			mouse &= ~bit;
		}
	}
	waitMillis = -1;
	cpuResume();
}

function hwKeyboardInput(keyChar) {
	keyBuf.push(keyChar << 24);
	waitMillis = -1;
	cpuResume();
}

function memWriteIMGWord(wordAddress, value) {
	var offs = wordAddress - DisplayStart/4;
	var x = (offs % 32) * 32;
	var y = screen.height - 1 - (offs / 32 | 0);
	if (y < 0 || x >= screen.width) return;
	for (var i = 0; i < 32; i++) {
		var white = ((value & (1 << i)) != 0);
		pixelData.data[i*4] = white ? 0xfd : 0x65;
		pixelData.data[i*4+1] = white ? 0xf6 : 0x7b;
		pixelData.data[i*4+2] = white ? 0xe3 : 0x83;
		pixelData.data[i*4+3] = 255;
	}
	screenCtx.putImageData( pixelData, x, y ); 
}