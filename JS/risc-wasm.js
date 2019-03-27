function RISCMachine(romWords) {
	this.megabytes = 1;
	if ((romWords[255] & 0xFFFFFF) == 0x3D424D) {
		var mb = (romWords[255] >>> 24) & 0xF;
		if (mb == 0) mb = 16;
		this.megabytes = mb;
	}
	this.bootROM = romWords;
}

(function($proto) {
	var $proto = RISCMachine.prototype;

	$proto.palette = null;

	$proto.memWriteWord = function(address, value) {
		this.wasm.exports.memWriteWord(address, value);
	}

	$proto.memReadPalette = function(address0) {
		if (this.palette == null) {
			this.palette = [
				0xffffff, 0xff0000, 0x00ff00, 0x0000ff, 0xff00ff, 0xffff00, 0x00ffff, 0xaa0000,
				0x009a00, 0x00009a, 0x0acbf3, 0x008282, 0x8a8a8a, 0xbebebe, 0xdfdfdf, 0x000000
			];
		}
		return this.palette[address0 / 4 | 0];
	}

	$proto.memWritePalette = function(address0, val) {
		if (this.palette != null) {
			var col = address0 / 4 | 0;
			this.palette[col] = val & 0xFFFFFF;
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
			default: return 0;
		}
	}

	$proto.memWriteIO = function(address0, val) {
		switch (address0) {
			case  0: return void(this.wait(val));
			case  4: return void(emulator.registerLEDs(val));
			case  8: return void(emulator.link.setData(val));
			case 36: return void(emulator.storageRequest(val, new Int32Array(this.wasm.exports.memory.buffer, this.wasm.exports.getRAMBase(), this.wasm.exports.getRAMSize()/4)));
			case 40: return void(emulator.clipboard.expect(val));
			case 44: return void(emulator.clipboard.putData(val));
		}
	}

	$proto.wait = function(x) {
		if (this.wasm.exports.getWaitMillis() === -1) {
			this.wasm.exports.setWaitMillis(0);
		} else {
			this.wasm.exports.setWaitMillis(x);
		}
	}

	$proto.cpuReset = function(cold) {
		this.wasm.exports.cpuReset(cold);
	}

	$proto.cpuRun = function() {
		this.wasm.exports.cpuRun0(Date.now() - emulator.startMillis);
	}

	$proto.cpuSingleStep = function() {
		this.wasm.exports.cpuSingleStep();
	}

	$proto.getBootROM = function() {
		return this.bootROM;
	}

	$proto.getDisplayStart = function() {
		return this.wasm.exports.getDisplayStart();
	}

	$proto.getWaitMillis = function() {
		return this.wasm.exports.getWaitMillis() + emulator.startMillis;
	}

	$proto.resetWaitMillis = function() {
		this.wasm.exports.setWaitMillis(-1);
	}

	RISCMachine.Initialize = function(fetchCallback, finishCallback) {
		fetchCallback("risc-wasm.wasm", function(arrayBuffer) {
			WebAssembly.compile(arrayBuffer).then(module => {
				RISCMachine.wasm = module;
				finishCallback();
			});
		});
	}

	$proto.Initialize = function(callback) {
		var that = this;
		WebAssembly.instantiate(RISCMachine.wasm, {
		"risc-wasm": {
			memReadPalette(address) {
				return that.memReadPalette(address);
			},
			memWritePalette(address, val) {
				return that.memWritePalette(address, val);
			},
			memReadIO(address) {
				return that.memReadIO(address);
			},
			memWriteIO(address, val) {
				return that.memWriteIO(address, val);
			},
			registerVideoChange(offset, val) {
				return emulator.registerVideoChange(offset, val, that.palette);
			}
		},
		"env": {
			abort(msg, file, line, column) {
				console.error("abort called at risc-wasm.ts:" + line + ":" + column);
			}
		},
		}).then(instance => {
			this.wasm = instance;
			var romBase = this.wasm.exports.Initialize(this.megabytes);
			new Int32Array(this.wasm.exports.memory.buffer).set(this.bootROM, romBase/4);
			callback();
		});
	}
})();
