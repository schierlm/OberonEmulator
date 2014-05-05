
var reg_PC = new Int32Array(1);
var reg_H = new Int32Array(1);
var reg_R = new Int32Array(16);
var flag_Z = false, flag_N = false, flag_C = false, flag_V = false;

var cpuTimeout = null;
var running = false;

function cpuReset(cold) {
	running = true;
	if (cold) {
		reg_R[15] = 0;
		memWriteWord(DisplayStart/4, 0x53697A65); // magic value SIZE
		memWriteWord(DisplayStart/4+1, screen.width);
		memWriteWord(DisplayStart/4+2, screen.height);
	}
	reg_PC[0] = ROMStart / 4;
	if (cpuTimeout != null)
		clearTimeout(cpuTimeout);
	cpuTimeout = setTimeout(cpuRun, 1);
}

function cpuResume() {
	if (cpuTimeout != null)
		clearTimeout(cpuTimeout);
	cpuTimeout = setTimeout(cpuRun, 1);
}

function cpuRun() {
	if (!running) return;
	var now = Date.now();
	for(var i=0; i < 200000 && waitMillis < now; i++) {
		cpuSingleStep();
	}
	cpuTimeout = setTimeout(cpuRun, Math.max(waitMillis-Date.now(), 10));
}

function cpuSingleStep() {
	var pbit = 0x80000000;
	var qbit = 0x40000000;
	var ubit = 0x20000000;
	var vbit = 0x10000000;
	
	var ir = memReadWord(reg_PC[0], true);
	reg_PC[0]++;

	if ((ir & pbit) == 0) {
		var a = (ir & 0x0F000000) >> 24;
		var b = (ir & 0x00F00000) >> 20;
		var op = (ir & 0x000F0000) >> 16;
		var im = ir & 0x0000FFFF;
		var c = ir & 0x0000000F;

		var a_val, b_val, c_val;
		b_val = reg_R[b];
		if ((ir & qbit) == 0) {
			c_val = reg_R[c];
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
						(flag_N ? 0x80000000 : 0) |
						(flag_Z ? 0x40000000 : 0) |
						(flag_C ? 0x20000000 : 0) |
						(flag_V ? 0x10000000 : 0);
			} else {
				a_val = reg_H[0];
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
			if ((ir & ubit) != 0 && flag_C) {
				a_val = (a_val + 1) | 0
			}
			flag_C = (a_val >>>0) < (b_val >>>0);
			flag_V = ((~(b_val ^ c_val) & (a_val ^ b_val)) >>> 31) != 0;
			break;
		}
		case 9: {
			a_val = (b_val - c_val) | 0;
			if ((ir & ubit) != 0 && flag_C) {
				a_val = (a_val - 1 ) | 0;
			}
			flag_C = (a_val >>>0) > (b_val >>>0);
			flag_V = (((b_val ^ c_val) & (a_val ^ b_val)) >>> 31) != 0;
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
			reg_H[0] = (tmp / ((-1>>>0)+1)) | 0;
			break;
		}
		case 11: {
			a_val = (b_val / c_val) | 0;
			reg_H[0] = (b_val % c_val) | 0;
			if (reg_H[0] < 0) {
				a_val = (a_val - 1) | 0;
				reg_H[0] += c_val;
			}
			break;
		}
		}
		cpuSetRegister(a, a_val);
	}
	else if ((ir & qbit) == 0) {
		var a = (ir & 0x0F000000) >> 24;
		var b = (ir & 0x00F00000) >> 20;
		var off = ir & 0x000FFFFF;

		var address = (((reg_R[b] >>>0) + (off >>>0)) % MemSize) | 0;
		if ((ir & ubit) == 0) {
			var a_val;
			if ((ir & vbit) == 0) {
				a_val = cpuLoadWord(address) | 0;
			} else {
				a_val = cpuLoadByte(address) & 0xff;
			}
			cpuSetRegister(a, a_val);
		} else {
			if ((ir & vbit) == 0) {
				cpuStoreWord(address, reg_R[a]);
			} else {
				cpuStoreByte(address, reg_R[a] & 0xff);
			}
		}
	}
	else {
		var t;
		switch ((ir >>> 24) & 7) {
		case 0:
			t = flag_N;
			break;
		case 1:
			t = flag_Z;
			break;
		case 2:
			t = flag_C;
			break;
		case 3:
			t = flag_V;
			break;
		case 4:
			t = flag_C || flag_Z;
			break;
		case 5:
			t = flag_N != flag_V;
			break;
		case 6:
			t = (flag_N != flag_V) || flag_Z;
			break;
		case 7:
			t = true;
			break;
		}
		if (t ^ (((ir >>> 24) & 8) != 0)) {
			if ((ir & vbit) != 0) {
				cpuSetRegister(15, (reg_PC[0] * 4) | 0);
			}
			if ((ir & ubit) == 0) {
				var c = ir & 0x0000000F;
				reg_PC[0] = (((reg_R[c] >>> 0) / 4) % MemWords) | 0;
			} else {
				var off = ir & 0x00FFFFFF;
				reg_PC[0] = ((reg_PC[0] + off) % MemWords) | 0;
			}
		}
	}
}

function cpuSetRegister(reg, value) {
	value = value | 0;
	reg_R[reg] = value;
	flag_Z = value == 0;
	flag_N = value < 0;
}

function cpuLoadWord(address) {
	return memReadWord(address / 4, false);
}

function cpuLoadByte(address) {
	var w = cpuLoadWord((address / 4|0) * 4);
	return (w >>> ((address % 4) * 8)) & 0xff;
}

function cpuStoreWord(address, value) {
	memWriteWord(address / 4|0, value);
}

function cpuStoreByte(address, value) {
	if (address < IOStart) {
		var w = memReadWord(address / 4|0, false);
		var shift = (address & 3) * 8;
		w &= ~(0xFF << shift);
		w |= (value & 0xFF) << shift;
		memWriteWord(address / 4|0, w);
	} else {
		memWriteWord(address / 4|0, value & 0xFF);
	}
}