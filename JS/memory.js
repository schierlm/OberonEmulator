var ROMStart = 0x0FE000;
var IOStart = 0x0FFFC0;
var MemSize = 0x100000;
var MemWords = (MemSize / 4);
var DisplayStart = 0x0E7F00;

var ram = new Int32Array(MemSize/4);

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
			memWriteIMGWord(wordAddress, value);
		} else {
			ram[wordAddress] = value;
		}
}

var paravirtPtr = 0;

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
		return emulator.clipboard.size;
	}
	case 44: {
		return emulator.clipboard.get();
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
		emulator.registerLEDs(value);
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
		emulator.clipboard.expect(value);
		break;
	}
	case 44: {
		emulator.clipboard.put(value);
		break;
	}
	}
}

function memWriteIMGWord(wordAddress, value) {
	ram[wordAddress] = value;
	let offset = wordAddress - DisplayStart / 4;
	let x = (offset % 32) * 32;
	let y = emulator.screen.height - 1 - (offset / 32 | 0);
	emulator.registerVideo(x, y, value);
}
