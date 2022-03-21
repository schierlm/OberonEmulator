(function($proto) {
	var $proto = RISCMachine.prototype;

	$proto.palette = null;
	$proto.hardwareEnumBuffer = [];

	$proto.ParseROM = function(romWords) {
		var mb = 1;
		var magic = (romWords[255] & 0xFFFFFF)
		if (magic == 0x3D424D || magic == 0x3D4243 || magic == 0x3D423F) {
			mb = (romWords[255] >>> 24) & 0xF;
			if ((romWords[255] >>> 24) == 0x3F) {
				mb = 1;
			}
			if (mb == 0) mb = 16;
		}
		this.colorSupported = magic == 0x3D4243;
		this.bootROM = romWords;
		return mb;
	}

	$proto.InitPalette = function(address0) {
		this.palette = [
			0xffffff, 0xff0000, 0x00ff00, 0x0000ff, 0xff00ff, 0xffff00, 0x00ffff, 0xaa0000,
			0x009a00, 0x00009a, 0x0acbf3, 0x008282, 0x8a8a8a, 0xbebebe, 0xdfdfdf, 0x000000
		];
	}

	$proto.setVideoMode = function(val) {
		if (val == 0 && this.palette != null) {
			this.palette = null;
			this.DisplayStart = 0x0E7F00 + this.MemSize - 0x100000;
		} else if (val == 1 && this.palette == null) {
			this.memReadPalette(this.PaletteStart);
		}
	}

	$proto.memReadIO = function(address0) {
		switch (address0) {
			case  0: return emulator.getTickCount();
			case  8: return emulator.link.getData();
			case 12: return emulator.link.getStatus();
			case 24: return emulator.getInputStatus();
			case 28: return emulator.getKeyCode();
			case 40: return emulator.clipboard.getSize();
			case 44: return emulator.clipboard.getData();
			case 48: return this.palette == null ? 0 : 1;
			case 60: return this.hardwareEnumBuffer.shift() | 0;
			default: return 0;
		}
	}

	$proto.memWriteIO = function(address0, val) {
		switch (address0) {
			case  0: return void(this.wait(val));
			case  4: return void(emulator.registerLEDs(val));
			case  8: return void(emulator.link.setData(val));
			case 12: return void(emulator.link.setStatus(val));
			case 32: return void(emulator.netCommand(val, this.getMainMemory()));
			case 36: return void(emulator.storageRequest(val, this.getMainMemory()));
			case 40: return void(emulator.clipboard.expect(val));
			case 44: return void(emulator.clipboard.putData(val));
			case 48: return void(this.setVideoMode(val));
			case 60: return void(this.hardwareEnumBuffer = emulator.runHardwareEnumerator(val));
		}
	}

	$proto.getBootROM = function() {
		return this.bootROM;
	}

})();
