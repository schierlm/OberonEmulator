package oberonemulator;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.SortedMap;
import java.util.TreeMap;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class JitCPU extends CPU {

	private final SortedMap<Integer, JitSegment> jitSegments = new TreeMap<>();
	private final MethodHandle defineClassHandle;
	private long counter = 0;

	public JitCPU(Memory memory, boolean largeAddressSpace) throws NoSuchMethodException, SecurityException, IllegalAccessException {
		super(memory, largeAddressSpace);
		memory.setJITCpu(this);
		Method defineMethod = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
		defineMethod.setAccessible(true);
		defineClassHandle = MethodHandles.publicLookup().unreflect(defineMethod).bindTo(JitCPU.class.getClassLoader());
	}

	protected void clearCache(int fromAddress) {
		jitSegments.tailMap(fromAddress).clear();
	}

	protected int getFlags() {
		return 0xD0 | (flagN ? 0x80000000 : 0) | (flagZ ? 0x40000000 : 0) | (flagC ? 0x20000000 : 0) | (flagV ? 0x10000000 : 0);
	}

	protected static int doROR(int b_val, int c_val) {
		return (b_val >>> (c_val & 31)) | (b_val << (-c_val & 31));
	}

	protected int doAdd(int b_val, int c_val, boolean carry) {
		int a_val = b_val + c_val;
		if (carry && flagC) {
			a_val++;
		}
		flagC = (a_val & 0xFFFFFFFFL) < (b_val & 0xFFFFFFFFL);
		flagV = ((~(b_val ^ c_val) & (a_val ^ b_val)) >>> 31) != 0;
		return a_val;
	}

	protected int doSub(int b_val, int c_val, boolean carry) {
		int a_val = b_val - c_val;
		if (carry && flagC) {
			a_val--;
		}
		flagC = (a_val & 0xFFFFFFFFL) > (b_val & 0xFFFFFFFFL);
		flagV = (((b_val ^ c_val) & (a_val ^ b_val)) >>> 31) != 0;
		return a_val;
	}

	protected int doMul(int b_val, int c_val, boolean unsigned) {
		long tmp;
		if (!unsigned) {
			tmp = ((long) b_val) * ((long) c_val);
		} else {
			tmp = (b_val & 0xFFFFFFFFL) * (c_val & 0xFFFFFFFFL);
		}
		int a_val = (int) tmp;
		regH = (int) (tmp >>> 32);
		return a_val;
	}

	protected int doDiv(int b_val, int c_val, boolean unsigned) {
		int a_val;
		if (c_val <= 0) {
			int[] q = illegal_div(b_val, c_val, unsigned);
			a_val = q[0];
			regH = q[1];
		} else {
			if (!unsigned) {
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
		return a_val;
	}

	protected void setReg(int a, int a_val) {
		regR[a] = a_val;
		flagZ = a_val == 0;
		flagN = a_val < 0;
	}

	protected int ioRead(int b, int off, long limit, boolean byteAccess) {
		final int address = (int) (((regR[b] & 0xFFFFFFFFL) + (off & 0xFFFFFFFFL)) % limit);
		int a_val;
		if (!byteAccess) {
			if (address % 4 != 0)
				throw new IllegalArgumentException("ERROR: Unaligned IO read: " + address);
			a_val = mem.readWord(address >>> 2, false);
		} else {
			a_val = (mem.readWord(address >>> 2, false) >>> ((address % 4) * 8)) & 0xff;
		}
		return a_val;
	}

	protected void ioWrite(int b, int off, long limit, boolean byteAccess, int value) {
		final int address = (int) (((regR[b] & 0xFFFFFFFFL) + (off & 0xFFFFFFFFL)) % limit);
		if (!byteAccess) {
			if (address % 4 != 0)
				throw new IllegalArgumentException("ERROR: Unaligned IO write");
			mem.writeWord(address >>> 2, value);
		} else {
			storeByte(address, (byte) value);
		}
	}

	protected void handleIRQBranch(int c, int nextPC) {
		regPC = nextPC;
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

	@Override
	public synchronized void singleStep() {
		handleIRQ();
		if (!jitSegments.containsKey(regPC)) {
			counter++;
			try {
				String name = "oberonemulator/JitSegment$" + regPC + "$" + counter;
				ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
				cw.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER, name, null, "java/lang/Object", new String[] { "oberonemulator/JitCPU$JitSegment" });
				cw.visitInnerClass("oberonemulator/JitCPU$JitSegment", "oberonemulator/JitCPU", "JitSegment", Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_ABSTRACT + Opcodes.ACC_INTERFACE);
				MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
				mv.visitCode();
				mv.visitVarInsn(Opcodes.ALOAD, 0);
				mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
				mv.visitInsn(Opcodes.RETURN);
				mv.visitMaxs(1, 1);
				mv.visitEnd();
				mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "execute", "(Loberonemulator/JitCPU;)V", null, null);
				mv.visitCode();
				boolean running = true;
				for (int i = 0; i < 512; i++) {
					int pc = regPC + i;
					if (!compile(mv, name, pc, mem.readWord(pc, true), largeAddressSpace)) {
						running = false;
						break;
					}
				}
				if (running) {
					mv.visitVarInsn(Opcodes.ALOAD, 1);
					mv.visitLdcInsn(regPC + 512);
					mv.visitFieldInsn(Opcodes.PUTFIELD, "oberonemulator/JitCPU", "regPC", "I");
					mv.visitInsn(Opcodes.RETURN);
				}
				mv.visitMaxs(0, 0);
				mv.visitEnd();
				cw.visitEnd();
				byte[] bytecode = cw.toByteArray();
				Class<? extends JitSegment> clazz = (Class<? extends JitSegment>) defineClassHandle.invokeExact(name.replace('/', '.'), bytecode, 0, bytecode.length);
				jitSegments.put(regPC, clazz.newInstance());
			} catch (Throwable t) {
				throw new RuntimeException("JIT compilation failed", t);
			}
		}
		jitSegments.get(regPC).execute(this);
	}

	private static boolean compile(MethodVisitor mv, String className, int pc, int ir, boolean largeAddressSpace) {
		boolean compileOn = true;
		/// // Variable slots: this = 0, cpu = 1, a_val = 2
		if ((ir & pbit) == 0) {
			// Register instructions
			int a = (ir & 0x0F000000) >> 24;
			int b = (ir & 0x00F00000) >> 20;
			int op = (ir & 0x000F0000) >> 16;
			int im = ir & 0x0000FFFF;
			int c = ir & 0x0000000F;

			/// inlined: b_val = regR[b];

			/// cpu.setReg(a, ...);
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitIntInsn(Opcodes.BIPUSH, a);

			if ((ir & qbit) == 0) {
				/// c_val = regR[c];
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "regR", "[I");
				mv.visitIntInsn(Opcodes.BIPUSH, c);
				mv.visitInsn(Opcodes.IALOAD);
			} else if ((ir & vbit) == 0) {
				/// c_val = im;
				mv.visitLdcInsn(im);
			} else {
				/// c_val = 0xFFFF0000 | im;
				mv.visitLdcInsn(0xFFFF0000 | im);
			}

			switch (INS.values()[op]) {
			case MOV: {
				if ((ir & ubit) == 0) {
					/// a_val = c_val;
				} else if ((ir & qbit) != 0) {
					/// a_val = c_val << 16;
					mv.visitIntInsn(Opcodes.BIPUSH, 16);
					mv.visitInsn(Opcodes.ISHL);
				} else if ((ir & vbit) != 0) {
					/// a_val = cpu.getFlags();
					mv.visitInsn(Opcodes.POP);
					mv.visitVarInsn(Opcodes.ALOAD, 1);
					mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "oberonemulator/JitCPU", "getFlags", "()I", false);
				} else {
					/// a_val = cpu.regH;
					mv.visitInsn(Opcodes.POP);
					mv.visitVarInsn(Opcodes.ALOAD, 1);
					mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "regH", "I");
				}
				break;
			}

			case LSL: {
				/// a_val = b_val << (c_val & 31);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "regR", "[I");
				mv.visitIntInsn(Opcodes.BIPUSH, b);
				mv.visitInsn(Opcodes.IALOAD);
				mv.visitInsn(Opcodes.SWAP);
				mv.visitIntInsn(Opcodes.BIPUSH, 31);
				mv.visitInsn(Opcodes.IAND);
				mv.visitInsn(Opcodes.ISHL);
				break;
			}
			case ASR: {
				/// a_val = (b_val) >> (c_val & 31);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "regR", "[I");
				mv.visitIntInsn(Opcodes.BIPUSH, b);
				mv.visitInsn(Opcodes.IALOAD);
				mv.visitInsn(Opcodes.SWAP);
				mv.visitIntInsn(Opcodes.BIPUSH, 31);
				mv.visitInsn(Opcodes.IAND);
				mv.visitInsn(Opcodes.ISHR);
				break;
			}
			case ROR: {
				/// a_val = cpu.doROR(b_val, c_val);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "regR", "[I");
				mv.visitIntInsn(Opcodes.BIPUSH, b);
				mv.visitInsn(Opcodes.IALOAD);
				mv.visitInsn(Opcodes.SWAP);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, "oberonemulator/JitCPU", "doROR", "(II)I", false);
				break;
			}
			case AND: {
				/// a_val = b_val & c_val;
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "regR", "[I");
				mv.visitIntInsn(Opcodes.BIPUSH, b);
				mv.visitInsn(Opcodes.IALOAD);
				mv.visitInsn(Opcodes.SWAP);
				mv.visitInsn(Opcodes.IAND);
				break;
			}
			case ANN: {
				/// a_val = b_val & ~c_val;
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "regR", "[I");
				mv.visitIntInsn(Opcodes.BIPUSH, b);
				mv.visitInsn(Opcodes.IALOAD);
				mv.visitInsn(Opcodes.SWAP);
				mv.visitInsn(Opcodes.ICONST_M1);
				mv.visitInsn(Opcodes.IXOR);
				mv.visitInsn(Opcodes.IAND);
				break;
			}
			case IOR: {
				// a_val = b_val | c_val;
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "regR", "[I");
				mv.visitIntInsn(Opcodes.BIPUSH, b);
				mv.visitInsn(Opcodes.IALOAD);
				mv.visitInsn(Opcodes.SWAP);
				mv.visitInsn(Opcodes.IOR);
				break;
			}
			case XOR: {
				// a_val = b_val ^ c_val;
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "regR", "[I");
				mv.visitIntInsn(Opcodes.BIPUSH, b);
				mv.visitInsn(Opcodes.IALOAD);
				mv.visitInsn(Opcodes.SWAP);
				mv.visitInsn(Opcodes.IXOR);
				break;
			}
			case ADD: {
				/// a_val = cpu.doAdd(b_val, c_val, (ir & ubit) != 0);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitInsn(Opcodes.DUP_X1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "regR", "[I");
				mv.visitIntInsn(Opcodes.BIPUSH, b);
				mv.visitInsn(Opcodes.IALOAD);
				mv.visitInsn(Opcodes.SWAP);
				mv.visitInsn((ir & ubit) != 0 ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "oberonemulator/JitCPU", "doAdd", "(IIZ)I", false);
				break;
			}
			case SUB: {
				/// a_val = cpu.doSub(b_val, c_val, (ir & ubit) != 0);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitInsn(Opcodes.DUP_X1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "regR", "[I");
				mv.visitIntInsn(Opcodes.BIPUSH, b);
				mv.visitInsn(Opcodes.IALOAD);
				mv.visitInsn(Opcodes.SWAP);
				mv.visitInsn((ir & ubit) != 0 ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "oberonemulator/JitCPU", "doSub", "(IIZ)I", false);
				break;
			}
			case MUL: {
				/// a_val = cpu.doMul(b_val, c_val, (ir & ubit) != 0);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitInsn(Opcodes.DUP_X1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "regR", "[I");
				mv.visitIntInsn(Opcodes.BIPUSH, b);
				mv.visitInsn(Opcodes.IALOAD);
				mv.visitInsn(Opcodes.SWAP);
				mv.visitInsn((ir & ubit) != 0 ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "oberonemulator/JitCPU", "doMul", "(IIZ)I", false);
				break;
			}
			case DIV: {
				/// a_val = cpu.doDiv(b_val, c_val, (ir & ubit) != 0);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitInsn(Opcodes.DUP_X1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "regR", "[I");
				mv.visitIntInsn(Opcodes.BIPUSH, b);
				mv.visitInsn(Opcodes.IALOAD);
				mv.visitInsn(Opcodes.SWAP);
				mv.visitInsn((ir & ubit) != 0 ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "oberonemulator/JitCPU", "doDiv", "(IIZ)I", false);
				break;
			}
			case FAD: {
				Feature.FLOATING_POINT.use();
				/// a_val = cpu.fp_add(b_val, c_val, (ir & ubit) != 0, (ir & vbit) != 0);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "regR", "[I");
				mv.visitIntInsn(Opcodes.BIPUSH, b);
				mv.visitInsn(Opcodes.IALOAD);
				mv.visitInsn(Opcodes.SWAP);
				mv.visitInsn((ir & ubit) != 0 ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
				mv.visitInsn((ir & vbit) != 0 ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, "oberonemulator/JitCPU", "fp_add", "(IIZZ)I", false);
				break;
			}
			case FSB: {
				Feature.FLOATING_POINT.use();
				/// a_val = cpu.fp_add(b_val, c_val ^ 0x80000000, (ir & ubit) != 0, (ir & vbit) != 0);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "regR", "[I");
				mv.visitIntInsn(Opcodes.BIPUSH, b);
				mv.visitInsn(Opcodes.IALOAD);
				mv.visitInsn(Opcodes.SWAP);
				mv.visitLdcInsn(new Integer(0x80000000));
				mv.visitInsn(Opcodes.IXOR);
				mv.visitInsn((ir & ubit) != 0 ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
				mv.visitInsn((ir & vbit) != 0 ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, "oberonemulator/JitCPU", "fp_add", "(IIZZ)I", false);
				break;
			}
			case FML: {
				Feature.FLOATING_POINT.use();
				/// a_val = cpu.fp_mul(b_val, c_val);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "regR", "[I");
				mv.visitIntInsn(Opcodes.BIPUSH, b);
				mv.visitInsn(Opcodes.IALOAD);
				mv.visitInsn(Opcodes.SWAP);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, "oberonemulator/JitCPU", "fp_mul", "(II)I", false);
				break;
			}
			case FDV: {
				Feature.FLOATING_POINT.use();
				/// a_val = fp_div(b_val, c_val);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "regR", "[I");
				mv.visitIntInsn(Opcodes.BIPUSH, b);
				mv.visitInsn(Opcodes.IALOAD);
				mv.visitInsn(Opcodes.SWAP);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, "oberonemulator/JitCPU", "fp_div", "(II)I", false);
				break;
			}
			default:
				throw new IllegalStateException();
			}
			/// cpu.setReg(a, a_val);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "oberonemulator/JitCPU", "setReg", "(II)V", false);
		} else if ((ir & qbit) == 0) {
			// Memory instructions
			int a = (ir & 0x0F000000) >> 24;
			int b = (ir & 0x00F00000) >> 20;
			int off = ir & 0x000FFFFF;
			if (largeAddressSpace)
				off = (off << 12) >> 12;
			long limit = largeAddressSpace ? Memory.LargeMemSize : Memory.MemSize;

			if ((ir & ubit) == 0) {
				/// cpu.setReg(a, cpu.ioRead(b, off, limit, (ir & vbit) != 0));
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitIntInsn(Opcodes.BIPUSH, a);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitIntInsn(Opcodes.BIPUSH, b);
				mv.visitLdcInsn(off);
				mv.visitLdcInsn(limit);
				mv.visitInsn((ir & vbit) != 0 ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "oberonemulator/JitCPU", "ioRead", "(IIJZ)I", false);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "oberonemulator/JitCPU", "setReg", "(II)V", false);
			} else {
				/// cpu.ioWrite(b, off, limit, (ir & vbit) != 0, cpu.regR[a]);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitIntInsn(Opcodes.BIPUSH, b);
				mv.visitLdcInsn(off);
				mv.visitLdcInsn(limit);
				mv.visitInsn((ir & vbit) != 0 ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "regR", "[I");
				mv.visitIntInsn(Opcodes.BIPUSH, a);
				mv.visitInsn(Opcodes.IALOAD);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "oberonemulator/JitCPU", "ioWrite", "(IIJZI)V", false);
			}
		} else {
			// Branch instructions
			switch ((ir >>> 24) & 7) {
			case 0:
				/// t = cpu.flagN;
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "flagN", "Z");
				break;
			case 1:
				/// t = cpu.flagZ;
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "flagZ", "Z");
				break;
			case 2:
				/// t = cpu.flagC;
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "flagC", "Z");
				break;
			case 3:
				/// t = cpu.flagV;
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "flagV", "Z");
				break;
			case 4:
				/// t = cpu.flagC | cpu.flagZ;
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "flagC", "Z");
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "flagZ", "Z");
				mv.visitInsn(Opcodes.IOR);
				break;
			case 5:
				/// t = cpu.flagN != cpu.flagV;
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "flagN", "Z");
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "flagV", "Z");
				mv.visitInsn(Opcodes.IXOR);
				break;
			case 6:
				/// t = (cpu.flagN != cpu.flagV) | cpu.flagZ;
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "flagN", "Z");
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "flagV", "Z");
				mv.visitInsn(Opcodes.IXOR);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "flagZ", "Z");
				mv.visitInsn(Opcodes.IOR);
				break;
			case 7:
				compileOn = false;
				break;
			default:
				throw new IllegalStateException();
			}
			boolean generateCode = true;
			if (((ir >>> 24) & 8) != 0) {
				if (!compileOn) {
					generateCode = false;
				} else {
					/// t = t ^ true;
					mv.visitInsn(Opcodes.ICONST_1);
					mv.visitInsn(Opcodes.IXOR);
				}
			}

			Label l = new Label();
			if (compileOn) {
				/// skip if not t:
				mv.visitJumpInsn(Opcodes.IFEQ, l);
				mv.visitIntInsn(Opcodes.BIPUSH, 9);
				mv.visitVarInsn(Opcodes.ISTORE, 2);
			}

			if (generateCode) {
				if ((ir & vbit) != 0) {
					int value = (pc + 1) * 4;
					/// cpu.setReg(15, value);
					mv.visitVarInsn(Opcodes.ALOAD, 1);
					mv.visitIntInsn(Opcodes.BIPUSH, 15);
					mv.visitLdcInsn(value);
					mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "oberonemulator/JitCPU", "setReg", "(II)V", false);
				}
				if ((ir & ubit) == 0) {
					int c = ir & 0x0000000F;
					long limit = (largeAddressSpace ? Memory.LargeMemWords : Memory.MemWords);
					/// cpu.regPC = (int) (((cpu.regR[c] & 0xFFFFFFFFL) / 4) % limit);
					mv.visitVarInsn(Opcodes.ALOAD, 1);
					mv.visitInsn(Opcodes.DUP);
					mv.visitFieldInsn(Opcodes.GETFIELD, "oberonemulator/JitCPU", "regR", "[I");
					mv.visitIntInsn(Opcodes.BIPUSH, c);
					mv.visitInsn(Opcodes.IALOAD);
					mv.visitInsn(Opcodes.I2L);
					mv.visitLdcInsn(0xFFFFFFFFL);
					mv.visitInsn(Opcodes.LAND);
					mv.visitLdcInsn(4L);
					mv.visitInsn(Opcodes.LDIV);
					mv.visitLdcInsn(limit);
					mv.visitInsn(Opcodes.LREM);
					mv.visitInsn(Opcodes.L2I);
					if ((ir & ubit) == 0 && (ir & vbit) == 0 && (ir & 0x30) != 0) {
						int cc = ir & 0x0000003F;
						/// cpu.handleIRQBranch(c, nextPC);
						mv.visitIntInsn(Opcodes.BIPUSH, cc);
						mv.visitInsn(Opcodes.SWAP);
						mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "oberonemulator/JitCPU", "handleIRQBranch", "(II)V", false);
					} else {
						mv.visitFieldInsn(Opcodes.PUTFIELD, "oberonemulator/JitCPU", "regPC", "I");
					}
					mv.visitInsn(Opcodes.RETURN);
				} else {
					int off = ir & 0x00FFFFFF;
					if (largeAddressSpace)
						off = (off << 8) >> 8;
					/// cpu.regPC = (pc + 1 + off) % (largeAddressSpace ? Memory.LargeMemWords : Memory.MemWords);
					mv.visitVarInsn(Opcodes.ALOAD, 1);
					mv.visitLdcInsn((pc + 1 + off) % (largeAddressSpace ? Memory.LargeMemWords : Memory.MemWords));
					mv.visitFieldInsn(Opcodes.PUTFIELD, "oberonemulator/JitCPU", "regPC", "I");
					mv.visitInsn(Opcodes.RETURN);
				}
			}

			if (compileOn) {
				/// skip to here
				mv.visitLabel(l);
				mv.visitFrame(Opcodes.F_FULL, 5, new Object[] { className, "oberonemulator/JitCPU", Opcodes.TOP, Opcodes.TOP, Opcodes.TOP }, 0, new Object[0]);
			}

			if (!generateCode) {
				compileOn = true;
			}

			if ((ir & ubit) == 0 && (ir & vbit) == 0 && (ir & 0x30) != 0 && compileOn) {
				int c = ir & 0x0000003F;
				/// cpu.handleIRQBranch(c, pc + 1);
				mv.visitVarInsn(Opcodes.ALOAD, 1);
				mv.visitIntInsn(Opcodes.BIPUSH, c);
				mv.visitLdcInsn(pc + 1);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "oberonemulator/JitCPU", "handleIRQBranch", "(II)V", false);
				mv.visitInsn(Opcodes.RETURN);
				compileOn = false;
			}
		}
		return compileOn;
	}

	public static interface JitSegment {
		public void execute(JitCPU cpu);
	}
}
