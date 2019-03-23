function RISCMachine(romWords, callback) {
	this.registers = new Int32Array(
		this.GeneralRegisterCount + this.SpecialRegisterCount
	);
	if ((romWords[255] & 0xFFFFFF) == 0x3D424D) {
		var mb = (romWords[255] >>> 24) & 0xF;
		if (mb == 0) mb = 16;
		this.MemSize = 0x100000 * mb;
		this.MemWords = (this.MemSize / 4);
		var offset = (mb - 1) * 0x100000;
		this.DisplayStart += offset;
		this.ROMStart += offset;
		this.PaletteStart += offset;
		this.IOStart += offset;
	}
	this.mainMemory = new Int32Array(this.MemWords);
	this.flag_Z = false, this.flag_N = false;
	this.flag_C = false, this.flag_V = false;
	this.bootROM = romWords;
}

(function($proto) {
	var $proto = RISCMachine.prototype;

	RISCMachine.GeneralRegisterCount = $proto.GeneralRegisterCount = 16;
	RISCMachine.SpecialRegisterCount = $proto.SpecialRegisterCount =  2;
	RISCMachine.PCID = $proto.PCID = -1;
	RISCMachine.HID = $proto.HID =   -2;

	RISCMachine.DisplayStart = $proto.DisplayStart = 0x0E7F00;
	RISCMachine.ROMStart = $proto.ROMStart = 0x0FE000;
	RISCMachine.PaletteStart = $proto.PaletteStart = 0x0FFF80;
	RISCMachine.IOStart = $proto.IOStart = 0x0FFFC0;
	RISCMachine.MemSize = $proto.MemSize = 0x100000;
	RISCMachine.MemWords = $proto.MemWords = (RISCMachine.MemSize / 4);
	$proto.palette = null;
	$proto.waitMillis = 0;

	$proto.cpuRegisterSlot = function(id) {
		if (id < 0 && -id <= this.SpecialRegisterCount) {
			return id + this.registers.length;
		}
		if (0 <= id && id < this.GeneralRegisterCount) {
			return id;
		}
		throw new Error("Bad register: " + id);
	}

	$proto.cpuGetRegister = function(id) {
		return this.registers[this.cpuRegisterSlot(id)];
	}

	$proto.cpuPutRegister = function(id, value) {
		value = value | 0;
		if (id >= 0) {
			this.flag_Z = value == 0;
			this.flag_N = value < 0;
		}
		this.registers[this.cpuRegisterSlot(id)] = value;
	}

	$proto.memReadWord = function(address, mapROM) {
		if (mapROM && address >= this.ROMStart) {
			return this.bootROM[(address - this.ROMStart) / 4 | 0];
		} else if (address >= this.IOStart) {
			return this.memReadIO(address);
		} else if (address >= this.PaletteStart) {
			return this.memReadPalette(address);
		} else {
			return this.mainMemory[address / 4 | 0];
		}
	}

	$proto.memWriteWord = function(address, value) {
		if (address >= this.IOStart) {
			this.memWriteIO(address, value);
		} else if (address >= this.PaletteStart) {
			this.memWritePalette(address, value);
		} else if (address >= this.DisplayStart) {
			this.memWriteVideo(address, value);
		} else {
			this.mainMemory[address / 4 | 0] = value;
		}
	}

	$proto.memReadPalette = function(address) {
		if (this.palette == null) {
			this.DisplayStart = 0x09FF00 + this.MemSize - 0x100000;
			this.palette = [
				0xffffff, 0xff0000, 0x00ff00, 0x0000ff, 0xff00ff, 0xffff00, 0x00ffff, 0xaa0000,
				0x009a00, 0x00009a, 0x0acbf3, 0x008282, 0x8a8a8a, 0xbebebe, 0xdfdfdf, 0x000000
			];
		}
		return this.palette[(address - this.PaletteStart) / 4 | 0];
	}

	$proto.memWritePalette = function(address, val) {
		if (this.palette != null) {
			var col = (address - this.PaletteStart) / 4 | 0;
			this.palette[col] = val & 0xFFFFFF;
			for(var i = this.DisplayStart / 4 | 0; i < this.mainMemory.length; i++) {
				emulator.registerVideoChange((i * 4 - this.DisplayStart) / 4, this.mainMemory[i], this.palette);
			}
		}
	}

	$proto.memReadIO = function(address) {
		switch (address - this.IOStart) {
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

	$proto.memWriteIO = function(address, val) {
		switch (address - this.IOStart) {
			case  0: return void(this.wait(val));
			case  4: return void(emulator.registerLEDs(val));
			case  8: return void(emulator.link.setData(val));
			case 36: return void(emulator.storageRequest(val, this.mainMemory));
			case 40: return void(emulator.clipboard.expect(val));
			case 44: return void(emulator.clipboard.putData(val));
		}
	}

	$proto.wait = function(x) {
		if (this.waitMillis === -1) {
			this.waitMillis = 0;
		} else {
			this.waitMillis = emulator.startMillis + x;
		}
	}

	$proto.memWriteVideo = function(address, val) {
		this.mainMemory[address / 4 | 0] = val;
		var offset = (address - this.DisplayStart) / 4;
		emulator.registerVideoChange(offset, val, this.palette);
	}

	$proto.cpuReset = function(cold) {
		this.cpuPutRegister(this.PCID, this.ROMStart / 4);
		if (cold) this.cpuPutRegister(15, 0);
	}

	$proto.cpuRun = function() {
		var now = Date.now();
		for (var i = 0; i < 200000 && this.waitMillis < now; ++i) {
			this.cpuSingleStep();
		}
	}

	$proto.cpuSingleStep = function() {
		var pbit = 0x80000000;
		var qbit = 0x40000000;
		var ubit = 0x20000000;
		var vbit = 0x10000000;

		var pc = this.cpuGetRegister(this.PCID);
		var ir = this.memReadWord(this.cpuGetRegister(this.PCID) * 4, true);
		this.cpuPutRegister(this.PCID, pc + 1);

		if ((ir & pbit) == 0) {
			var a = (ir & 0x0F000000) >> 24;
			var b = (ir & 0x00F00000) >> 20;
			var op = (ir & 0x000F0000) >> 16;
			var im = ir & 0x0000FFFF;
			var c = ir & 0x0000000F;

			var a_val, b_val, c_val;
			b_val = this.cpuGetRegister(b);
			if ((ir & qbit) == 0) {
				c_val = this.cpuGetRegister(c);
			} else if ((ir & vbit) == 0) {
				c_val = im;
			} else {
				c_val = 0xFFFF0000 | im;
			}
			switch (op) {
			case 0: {
				if ((ir & ubit) == 0) {
					a_val = c_val;
				} else if ((ir & qbit) != 0) {
					a_val = c_val << 16;
				} else if ((ir & vbit) != 0) {
					a_val = 0xD0 |
							(this.flag_N ? 0x80000000 : 0) |
							(this.flag_Z ? 0x40000000 : 0) |
							(this.flag_C ? 0x20000000 : 0) |
							(this.flag_V ? 0x10000000 : 0);
				} else {
					a_val = this.cpuGetRegister(this.HID);
				}
				break;
			}
			case 1: {
				a_val = b_val << (c_val & 31);
				break;
			}
			case 2: {
				a_val = (b_val) >> (c_val & 31);
				break;
			}
			case 3: {
				a_val = (b_val >>> (c_val & 31)) | (b_val << (-c_val & 31));
				break;
			}
			case 4: {
				a_val = b_val & c_val;
				break;
			}
			case 5: {
				a_val = b_val & ~c_val;
				break;
			}
			case 6: {
				a_val = b_val | c_val;
				break;
			}
			case 7: {
				a_val = b_val ^ c_val;
				break;
			}
			case 8: {
				a_val = (b_val + c_val) | 0;
				if ((ir & ubit) != 0 && this.flag_C) {
					a_val = (a_val + 1) | 0
				}
				this.flag_C = (a_val >>>0) < (b_val >>>0);
				this.flag_V = ((~(b_val ^ c_val) & (a_val ^ b_val)) >>> 31) != 0;
				break;
			}
			case 9: {
				a_val = (b_val - c_val) | 0;
				if ((ir & ubit) != 0 && this.flag_C) {
					a_val = (a_val - 1 ) | 0;
				}
				this.flag_C = (a_val >>>0) > (b_val >>>0);
				this.flag_V = (((b_val ^ c_val) & (a_val ^ b_val)) >>> 31) != 0;
				break;
			}
			case 10: {
				var tmp;
				if ((ir & ubit) == 0) {
					tmp = b_val * c_val;
				} else {
					tmp = (b_val >>>0) * (c_val >>>0);
				}
				a_val = tmp | 0;
				this.cpuPutRegister(this.HID, (tmp / ((-1>>>0) + 1)) | 0);
				break;
			}
			case 11: {
				if ((ir & ubit) == 0) {
					var h_val = (b_val % c_val) | 0;
					a_val = (b_val / c_val) | 0;
					if (h_val < 0) {
						h_val += c_val;
						a_val = (a_val - 1) | 0;
					}
					this.cpuPutRegister(this.HID, h_val);
				} else {
					a_val = ((b_val>>>0) / (c_val>>>0)) | 0;
					this.cpuPutRegister(
						this.HID, ((b_val>>>0) % (c_val>>>0)) | 0
					);
				}
				break;
			}
			case 13:
				c_val ^= 0x80000000;
				// fall through
			case 12:
				if ((ir & ubit) == 0 && (ir & vbit) == 0)
					a_val = _float2Int(_int2Float(b_val) + _int2Float(c_val));
				if ((ir & ubit) != 0 && (ir & vbit) == 0 && c_val == 0x4B000000)
					a_val = _float2Int(b_val);
				if ((ir & ubit) == 0 && (ir & vbit) != 0 && c_val == 0x4B000000)
					a_val = Math.floor(_int2Float(b_val)) | 0;
				break;
			case 14:
				a_val = _float2Int(_int2Float(b_val) * _int2Float(c_val));
				break;
			case 15:
				a_val = _float2Int(_int2Float(b_val) / _int2Float(c_val));
				break;
			}
			this.cpuPutRegister(a, a_val);
		}
		else if ((ir & qbit) == 0) {
			var a = (ir & 0x0F000000) >> 24;
			var b = (ir & 0x00F00000) >> 20;
			var off = (ir & 0x000FFFFF) << 12 >> 12;

			var address =
				(((this.cpuGetRegister(b) >>>0) + (off >>>0)) % this.MemSize) |
				0;
			if ((ir & ubit) == 0) {
				var a_val;
				if ((ir & vbit) == 0) {
					a_val = this.cpuLoadWord(address) | 0;
				} else {
					a_val = this.cpuLoadByte(address) & 0xff;
				}
				this.cpuPutRegister(a, a_val);
			} else {
				if ((ir & vbit) == 0) {
					this.cpuStoreWord(address, this.cpuGetRegister(a));
				} else {
					this.cpuStoreByte(address, this.cpuGetRegister(a) & 0xff);
				}
			}
		}
		else {
			var t;
			switch ((ir >>> 24) & 7) {
                case 0: t = this.flag_N; break;
                case 1: t = this.flag_Z; break;
                case 2: t = this.flag_C; break;
                case 3: t = this.flag_V; break;
                case 4: t = this.flag_C || this.flag_Z; break;
                case 5: t = this.flag_N != this.flag_V; break;
                case 6: t = this.flag_N != this.flag_V || this.flag_Z; break;
                case 7: t = true; break;
			}
			if (t ^ (((ir >>> 24) & 8) != 0)) {
                var pc = this.cpuGetRegister(this.PCID);
				if ((ir & vbit) != 0) {
					this.cpuPutRegister(15, pc * 4 | 0);
				}
				if ((ir & ubit) == 0) {
					var pos = (this.cpuGetRegister(ir & 0x0000000F) >>> 0) / 4;
					this.cpuPutRegister(this.PCID, pos % this.MemWords);
				} else {
					var pos = pc + (ir & 0x00FFFFFF);
					this.cpuPutRegister(this.PCID, pos % this.MemWords);
				}
			}
		}
	}

	$proto.cpuLoadWord = function(address) {
		return this.memReadWord(address | 0, false);
	}

	$proto.cpuLoadByte = function(address) {
		var w = this.cpuLoadWord(address);
		return (w >>> ((address % 4) * 8)) & 0xff;
	}

	$proto.cpuStoreWord = function(address, value) {
		this.memWriteWord(address | 0, value);
	}

	$proto.cpuStoreByte = function(address, value) {
		if (address < this.IOStart) {
			var w = this.memReadWord(address | 0, false);
			var shift = (address & 3) * 8;
			w &= ~(0xFF << shift);
			w |= (value & 0xFF) << shift;
			this.memWriteWord(address | 0, w);
		} else {
			this.memWriteWord(address | 0, value & 0xFF);
		}
	}

	$proto.getBootROM = function() {
		return this.bootROM;
	}

	$proto.getDisplayStart = function() {
		return this.DisplayStart;
	}

	$proto.getWaitMillis = function() {
		return this.waitMillis;
	}

	$proto.resetWaitMillis = function() {
		this.waitMillis = -1;
	}

	RISCMachine.Initialize = function(fetchCallback, finishCallback) {
		finishCallback();
	}

	$proto.Initialize = function(callback) {
		callback();
	}
})();

var _ieeeDataView = new DataView(new ArrayBuffer(4));

function _float2Int(bits) {
	_ieeeDataView.setFloat32(0, bits);
	return _ieeeDataView.getInt32(0);
}

function _int2Float(bits) {
	_ieeeDataView.setInt32(0, bits);
	return _ieeeDataView.getFloat32(0);
}
