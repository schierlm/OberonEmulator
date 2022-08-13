function RISCMachine(romWords) {
	this.registers = new Int32Array(
		this.GeneralRegisterCount + this.SpecialRegisterCount
	);
	this.bootROM = romWords;
	this.mainMemory = new Int32Array(this.MemWords);
	this.jitCache = {};
	this.flag_Z = false, this.flag_N = false;
	this.flag_C = false, this.flag_V = false;
	this.lastLoadRegister = 0;
}

(function($proto) {
	var $proto = RISCMachine.prototype;

	RISCMachine.GeneralRegisterCount = $proto.GeneralRegisterCount = 16;
	RISCMachine.SpecialRegisterCount = $proto.SpecialRegisterCount =  2;
	RISCMachine.PCID = $proto.PCID = -1;
	RISCMachine.HID = $proto.HID =   -2;

	RISCMachine.ROMStart = $proto.ROMStart = 0x0FE000;
	RISCMachine.PaletteStart = $proto.PaletteStart = 0x0FFF80;
	RISCMachine.IOStart = $proto.IOStart = 0x0FFFC0;
	RISCMachine.MemSize = $proto.MemSize = 0x100000;
	RISCMachine.MemWords = $proto.MemWords = (RISCMachine.MemSize / 4);
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
			return this.memReadIO(address - this.IOStart);
		} else if (address >= this.PaletteStart) {
			return this.memReadPalette(address);
		} else {
			return this.mainMemory[address / 4 | 0];
		}
	}

	$proto.memWriteWord = function(address, value) {
		if (address >= this.IOStart) {
			this.memWriteIO(address - this.IOStart, value);
		} else if (address >= this.PaletteStart) {
			this.memWritePalette(address, value);
		} else if (address >= this.DisplayStart) {
			this.memWriteVideo(address, value);
		} else {
			this.mainMemory[address / 4 | 0] = value;
		}
	}

	$proto.memReadPalette = function(address) {
		if (!this.colorSupported)
			return 0;
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

	$proto.getMainMemory = function() {
		return this.mainMemory;
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

	$proto.cpuReset = function(cold, memSize, dispMemSize) {
		this.cpuPutRegister(this.PCID, this.ROMStart / 4);
		if (cold) {
			this.cpuPutRegister(15, 0);
			if (this.MemSize != 0x100000 * memSize) {
				var offset = (memSize - this.MemSize / 0x100000) * 0x100000;
				this.MemSize = 0x100000 * memSize;
				this.MemWords = (this.MemSize / 4);
				this.ROMStart += offset;
				this.PaletteStart += offset;
				this.IOStart += offset;
				this.mainMemory = new Int32Array(this.MemWords);
			}
			if (this.palette != null) {
				this.palette = null;
			}
			this.DisplayStart = this.CalculateDisplayStart(memSize, dispMemSize);
		}
	}

	$proto.cpuRun = function() {
		var now = Date.now();
		for (var i = 0; i < 200000 && this.waitMillis < now;) {
			i += this.cpuSingleSteps();
		}
	}

	$proto.cpuSingleSteps = function() {
		var pc = this.cpuGetRegister(this.PCID);
		var func = this.jitCache['@'+pc];
		if (func === undefined) {
			var steps = '', i = pc;
			for(;i < pc + 1024; i++) {
				var jitstep = this.jitStep(i);
				steps += jitstep[1];
				if (jitstep[0]) break;
			}
			if (i == pc + 1024) {
				steps += this.jitExit(i, ''+i);
			}
			func = eval("(function(t) { "+steps+" })");
			this.jitCache['@'+pc] = func;
		}
		return func(this);
	}

	$proto.jitExit = function(atPC, jumpPC) {
		var realPC = this.cpuGetRegister(this.PCID);
		return 't.cpuPutRegister(t.PCID, ('+jumpPC+') % t.MemWords); return '+(atPC-realPC)+';';
	};

	$proto.jitStep = function(pc) {
		var pbit = 0x80000000;
		var qbit = 0x40000000;
		var ubit = 0x20000000;
		var vbit = 0x10000000;
		var ir = this.memReadWord(pc * 4, true);
		var step = '';
		var stop = false;

		if ((ir & pbit) == 0) {
			var a = (ir & 0x0F000000) >> 24;
			var b = (ir & 0x00F00000) >> 20;
			var op = (ir & 0x000F0000) >> 16;
			var im = ir & 0x0000FFFF;
			var c = ir & 0x0000000F;

			var a_val, b_val, c_val;
			b_val = 't.cpuGetRegister('+b+')';
			if ((ir & qbit) == 0) {
				c_val = 't.cpuGetRegister('+c+')';
			} else if ((ir & vbit) == 0) {
				c_val = ''+im;
			} else {
				c_val = '' + (0xFFFF0000 | im);
			}
			switch (op) {
			case 0: {
				if ((ir & ubit) == 0) {
					a_val = c_val;
				} else if ((ir & qbit) != 0) {
					a_val = '('+c_val+') << 16';
				} else if ((ir & vbit) != 0) {
					a_val = '0xD0 |	(t.flag_N ? 0x80000000 : 0) | (t.flag_Z ? 0x40000000 : 0) | (t.flag_C ? 0x20000000 : 0) | (t.flag_V ? 0x10000000 : 0)';
				} else {
					a_val = 't.cpuGetRegister(t.HID)';
				}
				break;
			}
			case 1: {
				a_val = '('+b_val+') << ((' + c_val+ ') & 31)';
				break;
			}
			case 2: {
				a_val = '('+b_val+') >> (('+c_val+') & 31)';
				break;
			}
			case 3: {
				a_val = '(('+b_val+') >>> (('+c_val+') & 31)) | (('+b_val+') << (-('+c_val+') & 31))';
				break;
			}
			case 4: {
				a_val = '('+b_val+') & ('+c_val+')';
				break;
			}
			case 5: {
				a_val = '('+b_val+') & ~('+c_val+')';
				break;
			}
			case 6: {
				a_val = '('+b_val+') | ('+c_val+')';
				break;
			}
			case 7: {
				a_val = '('+b_val+') ^ ('+c_val+')';
				break;
			}
			case 8: {
				step += 'var b = ('+b_val+'), c = ('+c_val+'), a = (b + c) | 0;';
				if ((ir & ubit) != 0) {
					step += 'if (t.flag_C) { a = (a + 1) | 0; }';
				}
				step += 't.flag_C = (a >>>0) < (b >>>0); t.flag_V = ((~(b ^ c) & (a ^ b)) >>> 31) != 0;';
				a_val = 'a';
				break;
			}
			case 9: {
				step += 'var b = ('+b_val+'), c = ('+c_val+'), a = (b - c) | 0;';
				if ((ir & ubit) != 0) {
					step += 'if (t.flag_C) { a = (a - 1) | 0; }';
				}
				step += 't.flag_C = (a >>>0) > (b >>>0); t.flag_V = (((b ^ c) & (a ^ b)) >>> 31) != 0;';
				a_val = 'a';
				break;
			}
			case 10: {
				var tmp;
				if ((ir & ubit) == 0) {
					tmp = '('+b_val+') * ('+c_val+')';
				} else {
					tmp = '(('+b_val+') >>>0) * (('+c_val+') >>>0)';
				}
				step += 'var v = ('+tmp+'), a = v | 0;';
				step += 't.cpuPutRegister(t.HID, (v / ((-1>>>0) + 1)) | 0);';
				a_val = 'a';
				break;
			}
			case 11: {
				if ((ir & ubit) == 0) {
					step += 'var b = ('+b_val+'), c = ('+c_val+'), h = (b % c) | 0, a = (b / c) | 0;';
					step += 'if (h < 0) { h += c; a = (a - 1) | 0 }';
					step += 't.cpuPutRegister(t.HID, h);';
					a_val = 'a';
				} else {
					step += 'var b = ('+b_val+'), c = ('+c_val+'), a = ((b>>>0) / (c>>>0)) | 0;';
					step += 't.cpuPutRegister(t.HID, ((b>>>0) % (c>>>0)) | 0);';
					a_val = 'a';
				}
				break;
			}
			case 13:
				c_val = '(' + c_val + ') ^ 0x80000000';
				// fall through
			case 12:
				if ((ir & ubit) == 0 && (ir & vbit) == 0)
					a_val = '_float2Int(_int2Float('+b_val+') + _int2Float('+c_val+'))';
				if ((ir & ubit) != 0 && (ir & vbit) == 0 && c_val == 0x4B000000)
					a_val = '_float2Int('+b_val+')';
				if ((ir & ubit) == 0 && (ir & vbit) != 0 && c_val == 0x4B000000)
					a_val = 'Math.floor(_int2Float('+b_val+')) | 0';
				break;
			case 14:
				a_val = '_float2Int(_int2Float('+b_val+') * _int2Float('+c_val+'))';
				break;
			case 15:
				a_val = '_float2Int(_int2Float('+b_val+') / _int2Float('+c_val+'))';
				break;
			}
			step += 't.cpuPutRegister('+a+', ('+a_val+'));';
		}
		else if ((ir & qbit) == 0) {
			var a = (ir & 0x0F000000) >> 24;
			var b = (ir & 0x00F00000) >> 20;
			var off = (ir & 0x000FFFFF) << 12 >> 12;
			step += 'var ad = (((t.cpuGetRegister('+b+') >>>0) + (('+off+') >>>0)) % t.MemSize) | 0;';
			var address = 'ad';
			if ((ir & ubit) == 0) {
				var a_val;
				if ((ir & vbit) == 0) {
					a_val = 't.cpuLoadWord('+address+') | 0';
				} else {
					a_val = 't.cpuLoadByte('+address+') & 0xff';
				}
				step += 't.lastLoadRegister = ('+a+'); t.cpuPutRegister(('+a+'), ('+a_val+'));';
			} else {
				if ((ir & vbit) == 0) {
					step += 't.cpuStoreWord(('+address+'), t.cpuGetRegister('+a+'));';
				} else {
					step += 't.cpuStoreByte(('+address+'), t.cpuGetRegister('+a+') & 0xff);';
				}
			}
			step += 'if (ad >= t.IOStart) { ' + this.jitExit(pc+1, ''+(pc+1)) + '}';
		} else {
			var t;
			switch ((ir >>> 24) & 7) {
				case 0: t = 't.flag_N'; break;
				case 1: t = 't.flag_Z'; break;
				case 2: t = 't.flag_C'; break;
				case 3: t = 't.flag_V'; break;
				case 4: t = 't.flag_C || t.flag_Z'; break;
				case 5: t = 't.flag_N != t.flag_V'; break;
				case 6: t = 't.flag_N != t.flag_V || t.flag_Z'; break;
				case 7: t = 'true'; break;
			}
			if (((ir >>> 24) & 8) != 0) { t = '!('+t+')'};
			step += 'if (' + t + ') {';
			pc++;
			if ((ir & vbit) != 0) {
				step += 't.cpuPutRegister(15, ('+(pc * 4 | 0)+'));';
			}
			if ((ir & ubit) == 0) {
				step += this.jitExit(pc, '(t.cpuGetRegister('+(ir & 0x0000000F)+') >>> 0) / 4')
			} else {
				step += this.jitExit(pc, ''+ (pc + (ir & 0x00FFFFFF)));
			}
			step += "}";
			stop = (t == 'true');
		}
		return [stop, "{"+step+"}"];
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

	$proto.getDisplayStart = function() {
		return this.DisplayStart;
	}

	$proto.getRAMSize = function() {
		return this.MemSize;
	}

	$proto.getWaitMillis = function() {
		return this.waitMillis;
	}

	$proto.resetWaitMillis = function() {
		if (this.waitMillis != emulator.startMillis + 0x7ffffffe)
			this.waitMillis = -1;
	}

	$proto.setStall = function(stalling) {
		if (stalling) {
			this.waitMillis = emulator.startMillis + 0x7ffffffe;
		} else {
			this.waitMillis = -1;
		}
	}

	$proto.repeatLastLoad = function(value) {
		this.cpuPutRegister(this.lastLoadRegister, value);
	}

	RISCMachine.Initialize = function(fetchCallback, finishCallback) {
		finishCallback();
	}

	$proto.Initialize = function(callback) {
		callback();
	}

	$proto.InvalidateCodeCache = function(start) {
		this.jitCache = {};
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
