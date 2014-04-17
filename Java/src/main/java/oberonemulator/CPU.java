package oberonemulator;

public class CPU extends Thread {

	private boolean running = true;
	private Memory mem;

	private int regPC;
	private int regH;
	private int[] regR = new int[16];
	private boolean flagZ, flagN, flagC, flagV;

	private static enum INS {
		MOV, LSL, ASR, ROR,
		AND, ANN, IOR, XOR,
		ADD, SUB, MUL, DIV,
		FAD, FSB, FML, FDV,
	};

	public CPU(Memory memory) {
		mem = memory;
		reset(true);
	}

	public void dispose() {
		running = false;
		try {
			join();
		} catch (InterruptedException ex) {
		}
		mem.dispose();
	}

	public void reset(boolean cold) {
		if (cold) {
			regR[15] = 0;
			mem.reset();
		}
		regPC = Memory.ROMStart / 4;
	}

	public void run() {
		try {
			while (running) {
				int ir = mem.readWord(regPC, true);
				regPC++;

				if ((ir & pbit) == 0) {
					// Register instructions
					int a = (ir & 0x0F000000) >> 24;
					int b = (ir & 0x00F00000) >> 20;
					int op = (ir & 0x000F0000) >> 16;
					int im = ir & 0x0000FFFF;
					int c = ir & 0x0000000F;

					int a_val, b_val, c_val;
					b_val = regR[b];
					if ((ir & qbit) == 0) {
						c_val = regR[c];
					} else if ((ir & vbit) == 0) {
						c_val = im;
					} else {
						c_val = 0xFFFF0000 | im;
					}

					switch (INS.values()[op]) {
					case MOV: {
						if ((ir & ubit) == 0) {
							a_val = c_val;
						} else if ((ir & qbit) != 0) {
							a_val = c_val << 16;
						} else if ((ir & vbit) != 0) {
							a_val = 0xD0 | // FIXME ???
									(flagN ? 0x80000000 : 0) |
									(flagZ ? 0x40000000 : 0) |
									(flagC ? 0x20000000 : 0) |
									(flagV ? 0x10000000 : 0);
						} else {
							a_val = regH;
						}
						break;
					}
					case LSL: {
						a_val = b_val << (c_val & 31);
						break;
					}
					case ASR: {
						a_val = (b_val) >> (c_val & 31);
						break;
					}
					case ROR: {
						a_val = (b_val >>> (c_val & 31)) | (b_val << (-c_val & 31));
						break;
					}
					case AND: {
						a_val = b_val & c_val;
						break;
					}
					case ANN: {
						a_val = b_val & ~c_val;
						break;
					}
					case IOR: {
						a_val = b_val | c_val;
						break;
					}
					case XOR: {
						a_val = b_val ^ c_val;
						break;
					}
					case ADD: {
						a_val = b_val + c_val;
						if ((ir & ubit) != 0 && flagC) {
							a_val++;
						}
						flagC = (a_val & 0xFFFFFFFFL) < (b_val & 0xFFFFFFFFL);
						flagV = ((~(b_val ^ c_val) & (a_val ^ b_val)) >>> 31) != 0;
						break;
					}
					case SUB: {
						a_val = b_val - c_val;
						if ((ir & ubit) != 0 && flagC) {
							a_val--;
						}
						flagC = (a_val & 0xFFFFFFFFL) > (b_val & 0xFFFFFFFFL);
						flagV = (((b_val ^ c_val) & (a_val ^ b_val)) >>> 31) != 0;
						break;
					}
					case MUL: {
						long tmp;
						if ((ir & ubit) == 0) {
							tmp = ((long) b_val) * ((long) c_val);
						} else {
							tmp = (b_val & 0xFFFFFFFFL) * (c_val & 0xFFFFFFFFL);
						}
						a_val = (int) tmp;
						regH = (int) (tmp >>> 32);
						break;
					}
					case DIV: {
						if (c_val <= 0) {
							throw new IllegalStateException("ERROR: PC 0x" + Integer.toHexString(regPC * 4 - 4) + ": divisor " + c_val + " is not positive");
						} else {
							a_val = b_val / c_val;
							regH = b_val % c_val;
							if (regH < 0) {
								a_val--;
								regH += c_val;
							}
						}
						break;
					}
					case FAD: {
						throw new IllegalStateException("FP not implemented!");
						// a_val = fp_add(b_val, c_val, ir & ubit, ir & vbit);
						// break;
					}
					case FSB: {
						throw new IllegalStateException("FP not implemented!");
						// a_val = fp_add(b_val, c_val ^ 0x80000000, ir & ubit,
						// ir & vbit);
						// break;
					}
					case FML: {
						throw new IllegalStateException("FP not implemented!");
						// a_val = fp_mul(b_val, c_val);
						// break;
					}
					case FDV: {
						throw new IllegalStateException("FP not implemented!");
						// a_val = fp_div(b_val, c_val);
						// break;
					}
					default:
						throw new IllegalStateException();
					}
					setRegister(a, a_val);
				}
				else if ((ir & qbit) == 0) {
					// Memory instructions
					int a = (ir & 0x0F000000) >> 24;
					int b = (ir & 0x00F00000) >> 20;
					int off = ir & 0x000FFFFF;

					final int address = (int) (((regR[b] & 0xFFFFFFFFL) + (off & 0xFFFFFFFFL)) % Memory.MemSize);
					if ((ir & ubit) == 0) {
						int a_val;
						if ((ir & vbit) == 0) {
							if (address % 4 != 0)
								throw new IllegalArgumentException("ERROR: Unaligned IO read: " + address);
							a_val = mem.readWord(address / 4, false);
						} else {
							a_val = (mem.readWord(address / 4, false) >>> ((address % 4) * 8)) & 0xff;
						}
						setRegister(a, a_val);
					} else {
						if ((ir & vbit) == 0) {
							if (address % 4 != 0)
								throw new IllegalArgumentException("ERROR: Unaligned IO write");
							mem.writeWord(address / 4, regR[a]);
						} else {
							storeByte(address, (byte) regR[a]);
						}
					}
				}
				else {
					// Branch instructions
					boolean t;
					switch ((ir >>> 24) & 7) {
					case 0:
						t = flagN;
						break;
					case 1:
						t = flagZ;
						break;
					case 2:
						t = flagC;
						break;
					case 3:
						t = flagV;
						break;
					case 4:
						t = flagC || flagZ;
						break;
					case 5:
						t = flagN != flagV;
						break;
					case 6:
						t = (flagN != flagV) || flagZ;
						break;
					case 7:
						t = true;
						break;
					default:
						throw new IllegalStateException();
					}
					if (t ^ (((ir >>> 24) & 8) != 0)) {
						if ((ir & vbit) != 0) {
							setRegister(15, regPC * 4);
						}
						if ((ir & ubit) == 0) {
							int c = ir & 0x0000000F;
							regPC = (int) (((regR[c] & 0xFFFFFFFFL) / 4) % Memory.MemWords);
						} else {
							int off = ir & 0x00FFFFFF;
							regPC = (regPC + off) % Memory.MemWords;
						}
					}
				}
			}
		} finally {
			try {
				new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							Thread.sleep(100);
						} catch (InterruptedException ex) {
						}
						mem.triggerRepaint();
					}
				}).start();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	private static final int pbit = 0x80000000;
	private static final int qbit = 0x40000000;
	private static final int ubit = 0x20000000;
	private static final int vbit = 0x10000000;

	private void setRegister(int reg, int value) {
		regR[reg] = value;
		flagZ = value == 0;
		flagN = value < 0;
	}

	private void storeByte(int address, byte value) {
		if (address < Memory.IOStart) {
			int w = mem.readWord(address / 4, false);
			int shift = (address & 3) * 8;
			w &= ~(0xFF << shift);
			w |= (value & 0xFF) << shift;
			mem.writeWord(address / 4, w);
		} else {
			if (address % 4 != 0)
				throw new IllegalStateException("Unaligned IO write");
			mem.writeWord(address / 4, value & 0xFF);
		}
	}
}
