function RISCMachine() {
	this.registers = new Int32Array(
		this.GPRegisterCount + -this.RegisterBounds
	);
	this.mainMemory = new Int32Array(this.MemWords);
	this.flag_Z = false, this.flag_N = false;
	this.flag_C = false, this.flag_V = false;
}

{
	let $proto = RISCMachine.prototype;

	RISCMachine.RegisterBounds = $proto.RegisterBounds =
	RISCMachine.HID = $proto.HID = -2;
	RISCMachine.PCID = $proto.PCID = -1;
	RISCMachine.GPRegisterCount = $proto.GPRegisterCount = 16;

	RISCMachine.DisplayStart = $proto.DisplayStart = 0x0E7F00;
	RISCMachine.ROMStart = $proto.ROMStart = 0x0FE000;
	RISCMachine.IOStart = $proto.IOStart = 0x0FFFC0;
	RISCMachine.MemSize = $proto.MemSize = 0x100000;
	RISCMachine.MemWords = $proto.MemWords = (RISCMachine.MemSize / 4);

	$proto.cpuRegisterSlot = function(id) {
		if (this.RegisterBounds <= id && id < 0) {
			return id + this.registers.length;
		}
		if (0 <= id && id < this.GPRegisterCount) {
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

	$proto.memReadWord = function(wordIndex, mapROM) {
		if (mapROM && wordIndex >= this.ROMStart / 4) {
			return emulator.disk[0][wordIndex - this.ROMStart / 4];
		} else if (wordIndex >= this.IOStart / 4) {
			return this.memReadIO(wordIndex);
		} else {
			return this.mainMemory[wordIndex];
		}
	}

	$proto.memWriteWord = function(wordIndex, value) {
		if (wordIndex >= this.IOStart / 4) {
			this.memWriteIO(wordIndex, value);
		} else if (wordIndex >= this.DisplayStart / 4) {
			this.memWriteVideo(wordIndex, value);
		} else {
			this.mainMemory[wordIndex] = value;
		}
	}

	$proto.memReadIO = function(wordIndex) {
		switch (wordIndex * 4 - this.IOStart) {
			case  0: return emulator.tickCount | 0;
			case 24: return emulator.getInputStatus();
			case 28: return emulator.getKeyCode();
			case 40: return emulator.clipboard.size;
			case 44: return emulator.clipboard.get();
			default: return 0;
		}
	}

	$proto.memWriteIO = function(wordIndex, val) {
		switch (wordIndex * 4 - this.IOStart) {
			case  0: return void(emulator.wait(val));
			case  4: return void(emulator.registerLEDs(val));
			case 36: return void(emulator.storageRequest(val, this.mainMemory));
			case 40: return void(emulator.clipboard.expect(val));
			case 44: return void(emulator.clipboard.put(val));
		}
	}

	$proto.memWriteVideo = function(wordIndex, word) {
		this.mainMemory[wordIndex] = word;
		let offset = wordIndex - this.DisplayStart / 4;
		emulator.registerVideoChange(offset, word);
	}

	$proto.cpuReset = function(cold) {
		this.cpuPutRegister(this.PCID, this.ROMStart / 4);
		if (cold) this.cpuPutRegister(15, 0);
	}

	$proto.cpuSingleStep = function() {
		var pbit = 0x80000000;
		var qbit = 0x40000000;
		var ubit = 0x20000000;
		var vbit = 0x10000000;

		var pc = this.cpuGetRegister(this.PCID);
		var ir = this.memReadWord(this.cpuGetRegister(this.PCID), true);
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
			var off = ir & 0x000FFFFF;

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
			case 0:
				t = this.flag_N;
				break;
			case 1:
				t = this.flag_Z;
				break;
			case 2:
				t = this.flag_C;
				break;
			case 3:
				t = this.flag_V;
				break;
			case 4:
				t = this.flag_C || this.flag_Z;
				break;
			case 5:
				t = this.flag_N != this.flag_V;
				break;
			case 6:
				t = (this.flag_N != this.flag_V) || this.flag_Z;
				break;
			case 7:
				t = true;
				break;
			}
			if (t ^ (((ir >>> 24) & 8) != 0)) {
				if ((ir & vbit) != 0) {
					this.cpuPutRegister(
						15, (this.cpuGetRegister(this.PCID) * 4) | 0
					);
				}
				if ((ir & ubit) == 0) {
					var pos = (this.cpuGetRegister(ir & 0x0000000F) >>> 0) / 4
					this.cpuPutRegister(this.PCID, pos % this.MemWords);
				} else {
					var pos = this.cpuGetRegister(this.PCID) +
						(ir & 0x00FFFFFF);
					this.cpuPutRegister(this.PCID, pos % this.MemWords);
				}
			}
		}
	}

	$proto.cpuLoadWord = function(address) {
		return this.memReadWord(address / 4, false);
	}

	$proto.cpuLoadByte = function(address) {
		var w = this.cpuLoadWord((address / 4|0) * 4);
		return (w >>> ((address % 4) * 8)) & 0xff;
	}

	$proto.cpuStoreWord = function(address, value) {
		this.memWriteWord(address / 4|0, value);
	}

	$proto.cpuStoreByte = function(address, value) {
		if (address < this.IOStart) {
			var w = this.memReadWord(address / 4|0, false);
			var shift = (address & 3) * 8;
			w &= ~(0xFF << shift);
			w |= (value & 0xFF) << shift;
			this.memWriteWord(address / 4|0, w);
		} else {
			this.memWriteWord(address / 4|0, value & 0xFF);
		}
	}
}

var _ieeeBuffer = new ArrayBuffer(4);
var _floatBuffer = new Float32Array(_ieeeBuffer);
var _intBuffer = new Int32Array(_ieeeBuffer);

function _float2Int(bits) {
	return _floatBuffer[0] = bits, _intBuffer[0];
}

function _int2Float(bits) {
	return _intBuffer[0] = bits, _floatBuffer[0];
}
