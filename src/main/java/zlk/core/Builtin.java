package zlk.core;

import java.util.List;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import zlk.bytecodegen.Instructions;
import zlk.common.Type;
import zlk.common.id.Id;

public record Builtin(
		Id id,
		Type type,
		Instructions insn)
implements Instructions
{
	public static List<Builtin> builtins(){
		Type i32i32i32 = Type.arrow(Type.I32, Type.arrow(Type.I32, Type.I32));
		Type i32bool = Type.arrow(Type.I32, Type.BOOL);

		return List.of(
				new Builtin("Basic.isZero", i32bool, mv -> {
					mv.visitInsn(Opcodes.I2L);
					mv.visitInsn(Opcodes.LCONST_0);
					mv.visitInsn(Opcodes.LCMP);
					mv.visitInsn(Opcodes.ICONST_1);
					mv.visitInsn(Opcodes.IAND);
					mv.visitInsn(Opcodes.ICONST_1);
					mv.visitInsn(Opcodes.IXOR);
				}),
				new Builtin("Basic.add", i32i32i32, mv -> mv.visitInsn(Opcodes.IADD)),
				new Builtin("Basic.sub", i32i32i32, mv -> mv.visitInsn(Opcodes.ISUB)),
				new Builtin("Basic.mul", i32i32i32, mv -> mv.visitInsn(Opcodes.IMUL)),
				new Builtin("Basic.div", i32i32i32, mv -> mv.visitInsn(Opcodes.IDIV)));
	}

	public Builtin(String canonical, Type type, Instructions insn) {
		this(Id.fromCanonicalName(canonical), type, insn);
	}

	@Override
	public void accept(MethodVisitor mv) {
		insn.accept(mv);
	}
}
