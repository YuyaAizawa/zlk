package zlk.benchmark;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** 命令数とCode attributeのbyte長を区別する，対象method限定の集計． */
public record BytecodeMetrics(int codeLength, int aload, int astore, int maxLocals, int maxStack,
		int update, int inplaceUpdate) {
	public static BytecodeMetrics read(byte[] bytes, String method, String descriptor) {
		if(bytes.length < 10 || bytes[0] != (byte) 0xca || bytes[1] != (byte) 0xfe
				|| bytes[2] != (byte) 0xba || bytes[3] != (byte) 0xbe)
			throw new IllegalArgumentException("invalid class file magic");
		try {
			ClassReader reader = new ClassReader(bytes);
			int length = codeLength(reader, bytes.length, method, descriptor);
			int[] counts = new int[6];
			reader.accept(new ClassVisitor(Opcodes.ASM9) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
					if(!name.equals(method) || !desc.equals(descriptor)) return null;
					return new MethodVisitor(Opcodes.ASM9) {
						@Override public void visitVarInsn(int opcode, int slot) {
							if(opcode == Opcodes.ALOAD) counts[0]++;
							if(opcode == Opcodes.ASTORE) counts[1]++;
						}
						@Override public void visitMaxs(int stack, int locals) { counts[2] = locals; counts[3] = stack; }
						@Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
							if(opcode == Opcodes.INVOKEINTERFACE && itf && owner.equals("zlk/runtime/ZlkRecord")
									&& name.equals("update") && desc.equals("(Ljava/lang/String;Ljava/lang/Object;)Lzlk/runtime/ZlkRecord;")) counts[4]++;
							if(opcode == Opcodes.INVOKESTATIC && !itf && owner.equals("zlk/runtime/internal/RecordOps")
									&& name.equals("inplaceUpdate") && desc.equals("(Lzlk/runtime/ZlkRecord;Ljava/lang/String;Ljava/lang/Object;)Lzlk/runtime/ZlkRecord;")) counts[5]++;
						}
					};
				}
			}, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			return new BytecodeMetrics(length, counts[0], counts[1], counts[2], counts[3], counts[4], counts[5]);
		} catch(IndexOutOfBoundsException | NegativeArraySizeException e) {
			throw new IllegalArgumentException("malformed class file", e);
		}
	}

	/** Constant pool（可変長entryと2slotを含む）はASMに委ね，member attributesだけを走査する． */
	private static int codeLength(ClassReader reader, int size, String method, String descriptor) {
		char[] buffer = new char[reader.getMaxStringLength()];
		int cursor = reader.header + 8 + 2 * reader.readUnsignedShort(reader.header + 6);
		int fields = reader.readUnsignedShort(cursor); cursor += 2;
		for(int i = 0; i < fields; i++) cursor = memberEnd(reader, size, cursor);
		int methods = reader.readUnsignedShort(cursor); cursor += 2;
		for(int i = 0; i < methods; i++) {
			boolean target = method.equals(reader.readUTF8(cursor + 2, buffer))
					&& descriptor.equals(reader.readUTF8(cursor + 4, buffer));
			int attrs = reader.readUnsignedShort(cursor + 6);
			int attribute = cursor + 8;
			for(int j = 0; j < attrs; j++) {
				int end = attributeEnd(reader, size, attribute);
				if(target && "Code".equals(reader.readUTF8(attribute, buffer))) {
					if(end - attribute < 18) throw new IllegalArgumentException("truncated Code attribute");
					int length = reader.readInt(attribute + 10);
					if(length <= 0 || length > 65535 || (long) attribute + 14 + length > end)
						throw new IllegalArgumentException("invalid code_length");
					return length;
				}
				attribute = end;
			}
			if(target) throw new IllegalArgumentException("target method has no Code attribute");
			cursor = attribute;
		}
		throw new IllegalArgumentException("target method absent: " + method + descriptor);
	}
	private static int memberEnd(ClassReader reader, int size, int member) {
		int attrs = reader.readUnsignedShort(member + 6);
		int cursor = member + 8;
		for(int i = 0; i < attrs; i++) cursor = attributeEnd(reader, size, cursor);
		return cursor;
	}
	private static int attributeEnd(ClassReader reader, int size, int attribute) {
		long end = (long) attribute + 6 + Integer.toUnsignedLong(reader.readInt(attribute + 2));
		if(end > size) throw new IllegalArgumentException("truncated attribute");
		return (int) end;
	}
}
