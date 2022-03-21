function RISCMachine(romWords) {
	this.megabytes = this.ParseROM(romWords);
}

(function($proto) {
	var $proto = RISCMachine.prototype;


	$proto.memWriteWord = function(address, value) {
		this.wasm.exports.memWriteWord(address, value);
	}

	$proto.memReadPalette = function(address0) {
		if (!this.colorSupported)
			return 0;
		if (this.palette == null) {
			this.InitPalette();
		}
		return this.palette[address0 / 4 | 0];
	}

	$proto.memWritePalette = function(address0, val) {
		if (this.palette != null) {
			var col = address0 / 4 | 0;
			this.palette[col] = val & 0xFFFFFF;
		}
	}

	$proto.getMainMemory = function() {
		return new Int32Array(this.wasm.exports.memory.buffer, this.wasm.exports.getRAMBase(), this.wasm.exports.getRAMSize()/4);
	}

	$proto.wait = function(x) {
		if (this.wasm.exports.getWaitMillis() === -1) {
			this.wasm.exports.setWaitMillis(0);
		} else {
			this.wasm.exports.setWaitMillis(x);
		}
	}

	$proto.cpuReset = function(cold, ramhint, colorhint) {
		if (cold) {
			var magic = this.bootROM[255];
			if ((magic == 0x3F3D424D || magic == 0x3F3D4243 || magic == 0x3F3D423F) && this.megabytes != ramhint) {
				this.megabytes = ramhint;
				var romBase = this.wasm.exports.Initialize(this.megabytes);
				new Int32Array(this.wasm.exports.memory.buffer).set(this.bootROM, romBase/4);
			}
			if ((magic & 0xFFFFFF) == 0x3D423F) {
				this.colorSupported = colorhint;
			}
		}
		this.wasm.exports.cpuReset(cold);
	}

	$proto.cpuRun = function() {
		this.wasm.exports.cpuRun0(Date.now() - emulator.startMillis);
	}

	$proto.cpuSingleStep = function() {
		this.wasm.exports.cpuSingleStep();
	}

	$proto.getDisplayStart = function() {
		return this.wasm.exports.getDisplayStart();
	}

	$proto.getWaitMillis = function() {
		return this.wasm.exports.getWaitMillis() + emulator.startMillis;
	}

	$proto.resetWaitMillis = function() {
		if (this.wasm.exports.getWaitMillis() != 0x7fffffff)
			this.wasm.exports.setWaitMillis(-1);
	}

	$proto.setStall = function(stalling) {
		if (stalling) {
			this.wasm.exports.setWaitMillis(0x7fffffff);
		} else {
			this.wasm.exports.setWaitMillis(-1);
		}
	}

	$proto.repeatLastLoad = function(value) {
		this.wasm.exports.repeatLastLoad(value);
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
