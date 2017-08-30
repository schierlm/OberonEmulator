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

function memReadIOWord(wordAddress) {
	switch (wordAddress * 4 - IOStart) {
		case  0: return emulator.tickCount | 0;
		case 24: return emulator.getInputStatus();
		case 28: return emulator.getKeyCode();
		case 40: return emulator.clipboard.size;
		case 44: return emulator.clipboard.get();
		default: return 0;
	}
}

function memWriteIOWord(wordAddress, value) {
	switch (wordAddress * 4 - IOStart) {
		// NB: The return statements are for control flow; none of these
		// methods should actually return anything.
		case  0: return emulator.wait(value);
		case  4: return emulator.registerLEDs(value);
		case 36: return emulator.storageRequest(value, ram);
		case 40: return emulator.clipboard.expect(value);
		case 44: return emulator.clipboard.put(value);
	}
}

function memWriteIMGWord(wordAddress, value) {
	ram[wordAddress] = value;
	let offset = wordAddress - DisplayStart / 4;
	let x = (offset % 32) * 32;
	let y = emulator.screen.height - 1 - (offset / 32 | 0);
	emulator.registerVideo(x, y, value);
}
