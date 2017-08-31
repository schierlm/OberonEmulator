function RISCMachine() {
	this.mainMemory = new Int32Array(this.MemSize/4);
	this.reg_PC = new Int32Array(1);
	this.reg_H = new Int32Array(1);
	this.reg_R = new Int32Array(16);
	this.flag_Z = false, this.flag_N = false;
	this.flag_C = false, this.flag_V = false;
}

{
	let $proto = RISCMachine.prototype;

	RISCMachine.ROMStart = $proto.ROMStart = 0x0FE000;
	RISCMachine.IOStart = $proto.IOStart = 0x0FFFC0;
	RISCMachine.MemSize = $proto.MemSize = 0x100000;
	RISCMachine.MemWords = $proto.MemWords = (RISCMachine.MemSize / 4);
	RISCMachine.DisplayStart = $proto.DisplayStart = 0x0E7F00;

	$proto.mainMemory = null;
	$proto.reg_PC = null;
	$proto.reg_H = null;
	$proto.reg_R = null;
	$proto.flag_Z = null;

	$proto.memReadWord = function(address, mapROM) {
		if (mapROM && address >= this.ROMStart / 4) {
			return emulator.disk[0][address - this.ROMStart / 4];
		} else if (address >= this.IOStart / 4) {
			return this.memReadIO(address);
		} else {
			return this.mainMemory[address];
		}
	}

	$proto.memWriteWord = function(address, value) {
		if (address >= this.IOStart / 4) {
			this.memWriteIO(address, value);
		} else if (address >= this.DisplayStart / 4) {
			this.memWriteVideo(address, value);
		} else {
			this.mainMemory[address] = value;
		}
	}

	$proto.memReadIO = function(address) {
		switch (address * 4 - this.IOStart) {
			case  0: return emulator.tickCount | 0;
			case 24: return emulator.getInputStatus();
			case 28: return emulator.getKeyCode();
			case 40: return emulator.clipboard.size;
			case 44: return emulator.clipboard.get();
			default: return 0;
		}
	}

	$proto.memWriteIO = function(address, word) {
		switch (address * 4 - this.IOStart) {
			// NB: The return statements are for control flow; none of these
			// methods should actually return anything.
			case  0: return emulator.wait(word);
			case  4: return emulator.registerLEDs(word);
			case 36: return emulator.storageRequest(word, this.mainMemory);
			case 40: return emulator.clipboard.expect(word);
			case 44: return emulator.clipboard.put(word);
		}
	}

	$proto.memWriteVideo = function(address, word) {
		this.mainMemory[address] = word;
		let offset = address - this.DisplayStart / 4;
		let x = (offset % 32) * 32;
		let y = emulator.screen.height - 1 - (offset / 32 | 0);
		emulator.registerVideo(x, y, word);
	}

	$proto.cpuReset = function(cold) {
		if (cold) {
			this.reg_R[15] = 0;
			// magic value Size
			this.memWriteWord(this.DisplayStart/4, 0x53697A65);
			this.memWriteWord(this.DisplayStart/4+1, emulator.screen.width);
			this.memWriteWord(this.DisplayStart/4+2, emulator.screen.height);
		}
		this.reg_PC[0] = this.ROMStart / 4;
	}

	$proto.cpuSingleStep = function() {
		var pbit = 0x80000000;
		var qbit = 0x40000000;
		var ubit = 0x20000000;
		var vbit = 0x10000000;

		var ir = this.memReadWord(this.reg_PC[0], true);
		this.reg_PC[0]++;

		if ((ir & pbit) == 0) {
			var a = (ir & 0x0F000000) >> 24;
			var b = (ir & 0x00F00000) >> 20;
			var op = (ir & 0x000F0000) >> 16;
			var im = ir & 0x0000FFFF;
			var c = ir & 0x0000000F;

			var a_val, b_val, c_val;
			b_val = this.reg_R[b];
			if ((ir & qbit) == 0) {
				c_val = this.reg_R[c];
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
					a_val = this.reg_H[0];
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
				this.reg_H[0] = (tmp / ((-1>>>0)+1)) | 0;
				break;
			}
			case 11: {
				if ((ir & ubit) == 0) {
					a_val = (b_val / c_val) | 0;
					this.reg_H[0] = (b_val % c_val) | 0;
					if (this.reg_H[0] < 0) {
						a_val = (a_val - 1) | 0;
						this.reg_H[0] += c_val;
					}
				} else {
					a_val = ((b_val>>>0) / (c_val>>>0)) | 0;
					this.reg_H[0] = ((b_val>>>0) % (c_val>>>0)) | 0;
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
			this.cpuSetRegister(a, a_val);
		}
		else if ((ir & qbit) == 0) {
			var a = (ir & 0x0F000000) >> 24;
			var b = (ir & 0x00F00000) >> 20;
			var off = ir & 0x000FFFFF;

			var address =
				(((this.reg_R[b] >>>0) + (off >>>0)) % this.MemSize) | 0;
			if ((ir & ubit) == 0) {
				var a_val;
				if ((ir & vbit) == 0) {
					a_val = this.cpuLoadWord(address) | 0;
				} else {
					a_val = this.cpuLoadByte(address) & 0xff;
				}
				this.cpuSetRegister(a, a_val);
			} else {
				if ((ir & vbit) == 0) {
					this.cpuStoreWord(address, this.reg_R[a]);
				} else {
					this.cpuStoreByte(address, this.reg_R[a] & 0xff);
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
					this.cpuSetRegister(15, (this.reg_PC[0] * 4) | 0);
				}
				if ((ir & ubit) == 0) {
					var c = ir & 0x0000000F;
					this.reg_PC[0] =
						(((this.reg_R[c] >>> 0) / 4) % this.MemWords) | 0;
				} else {
					var off = ir & 0x00FFFFFF;
					this.reg_PC[0] =
						((this.reg_PC[0] + off) % this.MemWords) | 0;
				}
			}
		}
	}

	$proto.cpuSetRegister = function(reg, value) {
		value = value | 0;
		this.reg_R[reg] = value;
		this.flag_Z = value == 0;
		this.flag_N = value < 0;
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
