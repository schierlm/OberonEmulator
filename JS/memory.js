var ROMStart = 0x0FE000;
var IOStart = 0x0FFFC0;
var MemSize = 0x100000;
var MemWords = (MemSize / 4);
var DisplayStart = 0x0E7F00;

var ram = new Int32Array(MemSize/4);

var backBufferMinX=4096, backBufferMinY=4096;
var backBufferMaxX=0, backBufferMaxY=0;
var backBufferDirty = false;

function memReadWord(wordAddress, mapROM) {
	if (mapROM && wordAddress >= ROMStart / 4) {
		return emulator.disk[0][wordAddress - ROMStart / 4];
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

var paravirtPtr = 0;
var clipboardBuffer = '', clipboardRemaining = 0;

function memReadIOWord(wordAddress) {
	switch (wordAddress * 4 - IOStart) {
	case 0: {
		return emulator.tickCount | 0;
	}
	case 24: {
		return emulator.getInputStatus();
	}
	case 28: {
		return emulator.getKeyCode();
	}
	case 40: {
		clipboardBuffer = clipboard.value.split("\n").join("\r");
		clipboardRemaining = 0;
		return clipboardBuffer.length;
	}
	case 44: {
		var ch = clipboardBuffer.charCodeAt(0);
		clipboardBuffer = clipboardBuffer.substring(1);
		return ch;
	}
	default: {
			return 0;
	}
	}
}

function memWriteIOWord(wordAddress, value) {
	switch (wordAddress * 4 - IOStart) {
	case 0: {
		emulator.wait(value);
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
	case 36: {
		// paravirtualized storage
		if ((value & 0xC0000000) == 0) { // setPtr
			paravirtPtr = value | 0;
		}
		if ((value & 0xC0000000) == (0x80000000|0)) { // read
			var sector = (value - 0x80000000)|0;
			var s = emulator.disk[sector|0];
			if (!s) s = new Int32Array(256);
			ram.set(s, paravirtPtr / 4);
		}
		if ((value & 0xC0000000) == (0xC0000000|0)) { // write
			var sector = (value - 0xC0000000)|0;
			var s = new Int32Array(256);
			s.set(ram.subarray(paravirtPtr/4, paravirtPtr/4 + 256))
			emulator.disk[sector|0] = s;
		}
		break;
	}
	case 40: {
		clipboardBuffer = '';
		clipboardRemaining = value;
		break;
	}
	case 44: {
		clipboardBuffer += String.fromCharCode(value);
		clipboardRemaining--;
		if (clipboardRemaining == 0) {
			clipboard.value = clipboardBuffer.split("\r").join("\n");
		}
		break;
	}
	}
}

function memWriteIMGWord(wordAddress, value) {
	var offs = wordAddress - DisplayStart/4;
	var x = (offs % 32) * 32;
	var y = screenCanvas.height - 1 - (offs / 32 | 0);
	if (y < 0 || x >= screenCanvas.width) return;
	var base = (y * screenCanvas.width + x) * 4;
	for (var i = 0; i < 32; i++) {
		var white = ((value & (1 << i)) != 0);
		backBuffer.data[base++] = white ? 0xfd : 0x65;
		backBuffer.data[base++] = white ? 0xf6 : 0x7b;
		backBuffer.data[base++] = white ? 0xe3 : 0x83;
		backBuffer.data[base++] = 255;
	}
	if (x < backBufferMinX) backBufferMinX = x;
	if (y < backBufferMinY) backBufferMinY = y;
	if (x > backBufferMaxX) backBufferMaxX = x + 31;
	if (y > backBufferMaxY) backBufferMaxY = y;
	if (!backBufferDirty) {
		setTimeout(drawBackBuffer, 1);
		backBufferDirty = true;
	}
}

function drawBackBuffer() {
	screenCtx.putImageData(backBuffer, 0, 0, backBufferMinX, backBufferMinY, backBufferMaxX - backBufferMinX + 1, backBufferMaxY - backBufferMinY + 1);
	backBufferMinX = backBufferMinY = 4096;
	backBufferMaxX = backBufferMaxY = 0;
	backBufferDirty = false;
}
