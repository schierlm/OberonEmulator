function RISCMachine(romWords) {
	this.bootROM = romWords;
	this.megabytes = 1;
}

(function($proto) {
	var $proto = RISCMachine.prototype;


	$proto.memWriteWord = function(address, value) {
		this.wasm.exports.memWriteWord(address, value);
	}

	$proto.memReadPalette = function(address0) {
		if (!this.colorSupported)
			return 0;
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

	$proto.cpuReset = function(cold, memSize, dispMemSize) {
		if (cold) {
			var magic = this.bootROM[255];
			if (this.megabytes != memSize) {
				this.megabytes = memSize;
				var romBase = this.wasm.exports.Initialize(this.megabytes);
				new Int32Array(this.wasm.exports.memory.buffer).set(this.bootROM, romBase/4);
			}
			this.wasm.exports.setDisplayStart(this.CalculateDisplayStart(memSize, dispMemSize));
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

	$proto.getRAMSize = function() {
		return this.wasm.exports.getRAMSize();
	}

	$proto.getWaitMillis = function() {
		return this.wasm.exports.getWaitMillis() + emulator.startMillis;
	}

	$proto.resetWaitMillis = function() {
		if (this.wasm.exports.getWaitMillis() != 0x7ffffffe)
			this.wasm.exports.setWaitMillis(-1);
	}

	$proto.setStall = function(stalling) {
		if (stalling) {
			this.wasm.exports.setWaitMillis(0x7ffffffe);
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
