package oberonemulator;

public class CPU extends Thread {

	public static boolean nativeFloatingPoint = false;

	private boolean running = true;
	protected Memory mem;

	protected int regPC;
	protected int regH;
	protected int[] regR = new int[16];
	protected boolean flagZ, flagN, flagC, flagV;
	protected boolean largeAddressSpace;

	protected int irqPC, irqStatus = IRQ_STATUS_DISABLED;
	protected boolean irqFZ, irqFN, irqFC, irqFV;
	protected long irqNanos;

	protected static int IRQ_STATUS_DISABLED = 0, IRQ_STATUS_ENABLED = 1, IRQ_STATUS_HANDLER = 2;

	protected static enum INS {
		MOV, LSL, ASR, ROR,
		AND, ANN, IOR, XOR,
		ADD, SUB, MUL, DIV,
		FAD, FSB, FML, FDV,
	};

	public CPU(Memory memory, boolean largeAddressSpace) {
		mem = memory;
		this.largeAddressSpace = largeAddressSpace;
		reset(true);
	}

	public void dispose() {
		running = false;
		mem.unblock();
		try {
			join();
		} catch (InterruptedException ex) {
		}
		mem.dispose();
	}

	public synchronized void reset(boolean cold) {
		if (cold) {
			regR[15] = 0;
			irqStatus = IRQ_STATUS_DISABLED;
			mem.reset();
		}
		regPC = mem.getROMStart() >>> 2;
	}

	public synchronized void copyRegisters(CPU otherCPU) {
		if (otherCPU.isAlive())
			throw new IllegalStateException();
		for (int i = 0; i < regR.length; i++) {
			regR[i] = otherCPU.regR[i];
		}
	}

	public void run() {
		try {
			while (running) {
				singleStep();
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

	protected void handleIRQ() {
		if (irqStatus == IRQ_STATUS_ENABLED && System.nanoTime() - irqNanos > 1000000) {
			irqNanos = System.nanoTime();
			irqFC = flagC; irqFN=flagN; irqFV = flagV; irqFZ = flagZ;
			irqPC = regPC;
			irqStatus = IRQ_STATUS_HANDLER;
			regPC = 1;
		}
	}

	public synchronized void singleStep() {
		handleIRQ();

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
					int[] q = illegal_div(b_val, c_val, (ir & ubit) != 0);
					a_val = q[0];
					regH = q[1];
				} else {
					if ((ir & ubit) == 0) {
						a_val = b_val / c_val;
						regH = b_val % c_val;
						if (regH < 0) {
							a_val--;
							regH += c_val;
						}
					} else {
						a_val = (int) ((b_val & 0xFFFFFFFFL) / (c_val & 0xFFFFFFFFL));
						regH = (int) ((b_val & 0xFFFFFFFFL) % (c_val & 0xFFFFFFFFL));
					}
				}
				break;
			}
			case FAD: {
				Feature.FLOATING_POINT.use();
				a_val = fp_add(b_val, c_val, (ir & ubit) != 0, (ir & vbit) != 0);
				break;
			}
			case FSB: {
				Feature.FLOATING_POINT.use();
				a_val = fp_add(b_val, c_val ^ 0x80000000, (ir & ubit) != 0, (ir & vbit) != 0);
				break;
			}
			case FML: {
				Feature.FLOATING_POINT.use();
				a_val = fp_mul(b_val, c_val);
				break;
			}
			case FDV: {
				Feature.FLOATING_POINT.use();
				a_val = fp_div(b_val, c_val);
				break;
			}
			default:
				throw new IllegalStateException();
			}
			setRegister(a, a_val);
		} else if ((ir & qbit) == 0) {
			// Memory instructions
			int a = (ir & 0x0F000000) >> 24;
			int b = (ir & 0x00F00000) >> 20;
			int off = ir & 0x000FFFFF;
			if (largeAddressSpace)
				off = (off << 12) >> 12;

			final int address = (int) (((regR[b] & 0xFFFFFFFFL) + (off & 0xFFFFFFFFL)) % (largeAddressSpace ? Memory.LargeMemSize : Memory.MemSize));
			if ((ir & ubit) == 0) {
				int a_val;
				if ((ir & vbit) == 0) {
					if (address % 4 != 0)
						throw new IllegalArgumentException("ERROR: Unaligned IO read: " + address);
					a_val = mem.readWord(address >>> 2, false);
				} else {
					a_val = (mem.readWord(address >>> 2, false) >>> ((address % 4) * 8)) & 0xff;
				}
				setRegister(a, a_val);
			} else {
				if ((ir & vbit) == 0) {
					if (address % 4 != 0)
						throw new IllegalArgumentException("ERROR: Unaligned IO write");
					mem.writeWord(address >>> 2, regR[a]);
				} else {
					storeByte(address, (byte) regR[a]);
				}
			}
		} else {
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
					regPC = (int) (((regR[c] & 0xFFFFFFFFL) / 4) % (largeAddressSpace ? Memory.LargeMemWords : Memory.MemWords));
				} else {
					int off = ir & 0x00FFFFFF;
					if (largeAddressSpace)
						off = (off << 8) >> 8;
					regPC = (regPC + off) % (largeAddressSpace ? Memory.LargeMemWords : Memory.MemWords);
				}
			}
			if ((ir & ubit) == 0 && (ir & vbit) == 0 && (ir & 0x30) != 0) {
				int c = ir & 0x0000003F;
				if (c == 0x20 && irqStatus == IRQ_STATUS_ENABLED) {
					irqStatus = IRQ_STATUS_DISABLED;
					mem.setIRQEnabled(false);
				} else if (c == 0x21 && irqStatus == IRQ_STATUS_DISABLED) {
					irqStatus = IRQ_STATUS_ENABLED;
					irqNanos = System.nanoTime();
					mem.setIRQEnabled(true);
				} else if (c == 0x10 && irqStatus == IRQ_STATUS_HANDLER) {
					irqStatus = IRQ_STATUS_ENABLED;
					flagC = irqFC; flagN = irqFN; flagV = irqFV; flagZ = irqFZ;
					regPC = irqPC;
				}
			}
		}
	}

	protected static final int pbit = 0x80000000;
	protected static final int qbit = 0x40000000;
	protected static final int ubit = 0x20000000;
	protected static final int vbit = 0x10000000;

	private void setRegister(int reg, int value) {
		regR[reg] = value;
		flagZ = value == 0;
		flagN = value < 0;
	}

	protected void storeByte(int address, byte value) {
		if ((address & 0xFFFFFFFFL) < (largeAddressSpace ? Memory.LargeIOStart : Memory.IOStart)) {
			int w = mem.readWord(address >>> 2, false);
			int shift = (address & 3) * 8;
			w &= ~(0xFF << shift);
			w |= (value & 0xFF) << shift;
			mem.writeWord(address >>> 2, w);
		} else {
			if (address % 4 != 0)
				throw new IllegalStateException("Unaligned IO write");
			mem.writeWord(address >>> 2, value & 0xFF);
		}
	}

	protected static int fp_add(int x, int y, boolean u, boolean v) {
		if (nativeFloatingPoint)
			return native_fp_add(x, y, u, v);

		boolean xs = (x & 0x80000000) != 0;
		int xe;
		int x0;
		if (!u) {
			xe = (x >>> 23) & 0xFF;
			int xm = ((x & 0x7FFFFF) << 1) | 0x1000000;
			x0 = (xs ? -xm : xm);
		} else {
			xe = 150;
			x0 = ((x & 0x00FFFFFF) << 8) >> 7;
		}

		boolean ys = (y & 0x80000000) != 0;
		int ye = (y >>> 23) & 0xFF;
		int ym = ((y & 0x7FFFFF) << 1);
		if (!u && !v)
			ym |= 0x1000000;
		int y0 = (ys ? -ym : ym);

		int e0;
		int x3, y3;
		if (ye > xe) {
			int shift = ye - xe;
			e0 = ye;
			x3 = shift > 31 ? x0 >> 31 : x0 >> shift;
			y3 = y0;
		} else {
			int shift = xe - ye;
			e0 = xe;
			x3 = x0;
			y3 = shift > 31 ? y0 >> 31 : y0 >> shift;
		}

		int sum = (((xs ? 1 : 0) << 26) | ((xs ? 1 : 0) << 25) | (x3 & 0x01FFFFFF))
				+ (((ys ? 1 : 0) << 26) | ((ys ? 1 : 0) << 25) | (y3 & 0x01FFFFFF));

		int s = (((sum & (1 << 26)) != 0 ? -sum : sum) + 1) & 0x07FFFFFF;

		int e1 = e0 + 1;
		int t3 = s >>> 1;

		if ((s & 0x3FFFFFC) != 0) {
			while ((t3 & (1 << 24)) == 0) {
				t3 <<= 1;
				e1--;
			}
		} else {
			t3 <<= 24;
			e1 -= 24;
		}

		boolean xn = (x & 0x7FFFFFFF) == 0;
		boolean yn = (y & 0x7FFFFFFF) == 0;

		if (v) {
			return (sum << 5) >> 6;
		} else if (xn) {
			return (u | yn) ? 0 : y;
		} else if (yn) {
			return x;
		} else if ((t3 & 0x01FFFFFF) == 0 || (e1 & 0x100) != 0) {
			return 0;
		} else {
			return ((sum & 0x04000000) << 5) | (e1 << 23) | ((t3 >> 1) & 0x7FFFFF);
		}
	}

	protected static int fp_mul(int x, int y) {
		if (nativeFloatingPoint)
			return native_fp_mul(x, y);

		int sign = (x ^ y) & 0x80000000;
		int xe = (x >> 23) & 0xFF;
		int ye = (y >> 23) & 0xFF;

		int xm = (x & 0x7FFFFF) | 0x800000;
		int ym = (y & 0x7FFFFF) | 0x800000;
		long m = (long) xm * ym;

		int e1 = (xe + ye) - 127;
		int z0;
		if ((m & (1L << 47)) != 0) {
			e1++;
			z0 = ((int) (m >> 23) + 1) & 0xFFFFFF;
		} else {
			z0 = ((int) (m >> 22) + 1) & 0xFFFFFF;
		}

		if (xe == 0 || ye == 0) {
			return 0;
		} else if ((e1 & 0x100) == 0) {
			return sign | ((e1 & 0xFF) << 23) | (z0 >> 1);
		} else if ((e1 & 0x80) == 0) {
			return sign | (0xFF << 23) | (z0 >> 1);
		} else {
			return 0;
		}
	}

	protected static int fp_div(int x, int y) {
		if (nativeFloatingPoint)
			return native_fp_div(x, y);

		int sign = (x ^ y) & 0x80000000;
		int xe = (x >> 23) & 0xFF;
		int ye = (y >> 23) & 0xFF;

		int xm = (x & 0x7FFFFF) | 0x800000;
		int ym = (y & 0x7FFFFF) | 0x800000;
		int q1 = (int) (xm * (1L << 25) / ym);

		int e1 = (xe - ye) + 126;
		int q2;
		if ((q1 & (1 << 25)) != 0) {
			e1++;
			q2 = (q1 >> 1) & 0xFFFFFF;
		} else {
			q2 = q1 & 0xFFFFFF;
		}

		if (xe == 0) {
			return 0;
		} else if (ye == 0) {
			return sign | (0xFF << 23);
		} else if ((e1 & 0x100) == 0) {
			return sign | ((e1 & 0xFF) << 23) | ((q2 + 1) >> 1);
		} else if ((e1 & 0x80) == 0) {
			return sign | (0xFF << 23) | (q2 >> 1);
		} else {
			return 0;
		}
	}

	protected static int[] illegal_div(int x_s, int y_s, boolean signed_div) {
		long x = x_s & 0xFFFFFFFFL, y = y_s & 0xFFFFFFFFL;
		boolean sign = (x_s < 0) & signed_div;
		long RQ = sign ? -x : x;
		for (int S = 0; S < 32; S++) {
			long w0 = (RQ >> 31) & 0xFFFFFFFFL;
			long w1 = (w0 - y) & 0xFFFFFFFFL;
			if ((int) w1 < 0) {
				RQ = (w0 << 32) | ((RQ & 0x7FFFFFFFL) << 1);
			} else {
				RQ = (w1 << 32) | ((RQ & 0x7FFFFFFFL) << 1) | 1;
			}
		}
		int[] d = { (int) RQ, (int) (RQ >> 32) };
		if (sign) {
			d[0] = -d[0];
			if (d[1] != 0) {
				d[0] -= 1;
				d[1] = (int) (y - d[1]);
			}
		}
		return d;
	}

	private static strictfp int native_fp_add(int x, int y, boolean u, boolean v) {
		if (!u && !v)
			return Float.floatToRawIntBits(Float.intBitsToFloat(x) + Float.intBitsToFloat(y));
		if (u && !v && y == 0x4B000000)
			return Float.floatToRawIntBits(x);
		if (!u && v && y == 0x4B000000)
			return (int) Math.floor(Float.intBitsToFloat(x));
		throw new IllegalStateException("Unsupported FP_ADD parameters");
	}

	private static strictfp int native_fp_mul(int x, int y) {
		return Float.floatToRawIntBits(Float.intBitsToFloat(x) * Float.intBitsToFloat(y));
	}

	private static strictfp int native_fp_div(int x, int y) {
		return Float.floatToRawIntBits(Float.intBitsToFloat(x) / Float.intBitsToFloat(y));
	}
}
