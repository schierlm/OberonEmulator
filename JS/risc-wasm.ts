// This is AssemblyScript; you can compile it to WebAssembly on https://webassembly.studio/

declare function memReadPalette(address0: i32): i32;
declare function memWritePalette(address0: i32, val: i32): void;
declare function memReadIO(address0: i32): i32;
declare function memWriteIO(address0: i32, val: i32): void;
declare function registerVideoChange(offset: i32, val: i32): void;

const REGISTER_BASE: usize = HEAP_BASE;
const ROM_BASE: usize = REGISTER_BASE + 16 * 4;
const RAM_BASE: usize = ROM_BASE + 1024;

var flag_Z: bool = false;
var flag_N: bool = false;
var flag_C: bool = false;
var flag_V: bool = false;
var regPC: i32 = 0;
var regH: i32 = 0;

var ROMStart: i32 = 0x0FE000;
var DisplayStart: i32 = 0x0E7F00;
var PaletteStart: i32 = 0x0FFF80;
var IOStart: i32 = 0x0FFFC0;
var MemSize: i32 = 0x100000;
var waitMillis: i32 = 0;
var lastLoadRegister: i32 = 0;

export function Initialize(mb: i32): i32 {
	if (mb != MemSize / 0x100000) {
		var offset: i32 = (mb - MemSize / 0x100000) * 0x100000;
		MemSize = 0x100000 * mb;
		DisplayStart += offset;
		ROMStart += offset;
		PaletteStart += offset;
		IOStart += offset;
	}
	memory.grow((RAM_BASE + MemSize + 65535) / 65536 - memory.size());
	return ROM_BASE;
}

function cpuGetRegister(id: i32): i32 {
	return load<i32>(REGISTER_BASE + id * 4);
}

function cpuPutRegister(id: i32, value: i32): void {
	store<i32>(REGISTER_BASE + id * 4, value);
	flag_Z = value == 0;
	flag_N = value < 0;
}

export function cpuReset(cold: bool): void {
	regPC = ROMStart / 4;
	if (cold) cpuPutRegister(15, 0);
}

function memReadWord(address: i32, mapROM: bool): i32 {
	if (mapROM && address >= ROMStart) {
		return load<i32>(ROM_BASE + (address - ROMStart));
	} else if (address >= IOStart) {
		return memReadIO(address - IOStart);
	} else if (address >= PaletteStart) {
		DisplayStart = 0x09FF00 + MemSize - 0x100000;
		return memReadPalette(address - PaletteStart);
	} else {
		return load<i32>(RAM_BASE + address);
	}
}

export function memWriteWord(address: i32, value: i32): void {
	if (address >= IOStart) {
		memWriteIO(address - IOStart, value);
	} else if (address >= PaletteStart) {
		memWritePalette(address - PaletteStart, value);
		var max: i32 = (PaletteStart - DisplayStart) / 4;
		for (var i: i32 = 0; i < max; i++) {
			registerVideoChange(i, load<i32>(RAM_BASE + DisplayStart + i * 4));
		}
	} else {
		store<i32>(RAM_BASE + address, value);
		if (address >= DisplayStart) {
			registerVideoChange((address - DisplayStart) / 4, value);
		}
	}
}

export function cpuRun0(now: i32): void {
	for (var i: i32 = 0; i < 200000 && waitMillis < now; ++i) {
		cpuSingleStep();
	}
}

export function cpuSingleStep(): void {
	const pbit: i32 = 0x80000000;
	const qbit: i32 = 0x40000000;
	const ubit: i32 = 0x20000000;
	const vbit: i32 = 0x10000000;

	var ir: i32 = memReadWord(regPC * 4, true);
	var a_val: i32, a: i32, b: i32, pos: i32;
	regPC++;

	if ((ir & pbit) == 0) {
		a = (ir & 0x0F000000) >> 24;
		b = (ir & 0x00F00000) >> 20;
		var op: i32 = (ir & 0x000F0000) >> 16;
		var im: i32 = ir & 0x0000FFFF;
		var c: i32 = ir & 0x0000000F;

		var b_val: i32, c_val: i32;
		b_val = cpuGetRegister(b);
		if ((ir & qbit) == 0) {
			c_val = cpuGetRegister(c);
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
					a_val = regH;
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
				flag_C = (<u32>a_val) < (<u32>b_val);
				flag_V = ((~(b_val ^ c_val) & (a_val ^ b_val)) >>> 31) != 0;
				break;
			}
			case 9: {
				a_val = (b_val - c_val) | 0;
				if ((ir & ubit) != 0 && flag_C) {
					a_val = (a_val - 1) | 0;
				}
				flag_C = (<u32>a_val) > (<u32>b_val);
				flag_V = (((b_val ^ c_val) & (a_val ^ b_val)) >>> 31) != 0;
				break;
			}
			case 10: {
				var tmp: i64;
				if ((ir & ubit) == 0) {
					tmp = b_val * c_val;
				} else {
					tmp = <i64>((<u32>b_val) * (<u32>c_val));
				}
				a_val = <i32>tmp;
				regH = <i32>((<u64>tmp) >>> 32);
				break;
			}
			case 11: {
				if ((ir & ubit) == 0) {
					var h_val: i32 = (b_val % c_val);
					a_val = b_val / c_val;
					if (h_val < 0) {
						h_val += c_val;
						a_val--;
					}
					regH = h_val;
				} else {
					a_val = (<u32>b_val) / (<u32>c_val);
					regH = (<u32>b_val) % (<u32>c_val);
				}
				break;
			}
			case 13:
				c_val ^= 0x80000000;
			// fall through
			case 12:
				if ((ir & ubit) == 0 && (ir & vbit) == 0)
					a_val = reinterpret<i32>(reinterpret<f32>(b_val) + reinterpret<f32>(c_val));
				if ((ir & ubit) != 0 && (ir & vbit) == 0 && c_val == 0x4B000000)
					a_val = reinterpret<i32>(<f32>b_val);
				if ((ir & ubit) == 0 && (ir & vbit) != 0 && c_val == 0x4B000000)
					a_val = <i32>reinterpret<f32>(b_val);
				break;
			case 14:
				a_val = reinterpret<i32>(reinterpret<f32>(b_val) * reinterpret<f32>(c_val));
				break;
			case 15:
				a_val = reinterpret<i32>(reinterpret<f32>(b_val) / reinterpret<f32>(c_val));
				break;
		}
		cpuPutRegister(a, a_val);
	} else if ((ir & qbit) == 0) {
		a = (ir & 0x0F000000) >> 24;
		b = (ir & 0x00F00000) >> 20;
		var off: i32 = (ir & 0x000FFFFF) << 12 >> 12;

		var address: i32 = <i32>(((<u32>cpuGetRegister(b)) + (<u32>off)) % MemSize);
		if ((ir & ubit) == 0) {
			if ((ir & vbit) == 0) {
				a_val = memReadWord(address, false);
			} else {
				a_val = (memReadWord(address & ~3, false) >>> ((address % 4) * 8)) & 0xff;
			}
			lastLoadRegister = a;
			cpuPutRegister(a, a_val);
		} else {
			if ((ir & vbit) == 0) {
				memWriteWord(address, cpuGetRegister(a));
			} else if (address < IOStart) {
				var w: i32 = memReadWord(address & ~3, false);
				var shift: i32 = (address & 3) * 8;
				w &= ~(0xFF << shift);
				w |= (cpuGetRegister(a) & 0xFF) << shift;
				memWriteWord(address & ~3, w);
			} else {
				memWriteWord(address & ~3, cpuGetRegister(a) & 0xFF);
			}
		}
	} else {
		var t: bool;
		switch ((ir >>> 24) & 7) {
			case 0: t = flag_N; break;
			case 1: t = flag_Z; break;
			case 2: t = flag_C; break;
			case 3: t = flag_V; break;
			case 4: t = flag_C || flag_Z; break;
			case 5: t = flag_N != flag_V; break;
			case 6: t = flag_N != flag_V || flag_Z; break;
			case 7: t = true; break;
		}
		if (t != (((ir >>> 24) & 8) != 0)) {
			if ((ir & vbit) != 0) {
				cpuPutRegister(15, regPC * 4);
			}
			if ((ir & ubit) == 0) {
				pos = (cpuGetRegister(ir & 0x0000000F) >>> 0) / 4;
				regPC = pos % (MemSize / 4);
			} else {
				pos = regPC + (ir & 0x00FFFFFF);
				regPC = pos % (MemSize / 4);
			}
		}
	}
}

export function repeatLastLoad(value: i32): void {
	this.cpuPutRegister(lastLoadRegister, value);
}

export function getRAMBase(): i32 {
	return RAM_BASE;
}

export function getRAMSize(): i32 {
	return MemSize;
}

export function setWaitMillis(val: i32): void {
	waitMillis = val;
}

export function getDisplayStart(): i32 {
	return DisplayStart;
}

export function getWaitMillis(): i32 {
	return waitMillis;
}
