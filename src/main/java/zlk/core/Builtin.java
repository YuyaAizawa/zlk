package zlk.core;

import java.util.List;
import java.util.function.Consumer;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import zlk.common.Type;

public record Builtin(
		String name,
		Type type,
		Consumer<MethodVisitor> action) {

	public static List<Builtin> builtins(){
		Type i32i32i32 = Type.arrow(Type.i32, Type.arrow(Type.i32, Type.i32));
		Type i32bool = Type.arrow(Type.i32, Type.bool);

		return List.of(
				new Builtin("isZero", i32bool, mv -> {
					mv.visitInsn(Opcodes.I2L);
					mv.visitInsn(Opcodes.LCONST_0);
					mv.visitInsn(Opcodes.LCMP);
					mv.visitInsn(Opcodes.ICONST_1);
					mv.visitInsn(Opcodes.IAND);
					mv.visitInsn(Opcodes.ICONST_1);
					mv.visitInsn(Opcodes.IXOR);
				}),
				new Builtin("add", i32i32i32, mv -> mv.visitInsn(Opcodes.IADD)),
				new Builtin("sub", i32i32i32, mv -> mv.visitInsn(Opcodes.ISUB)),
				new Builtin("mul", i32i32i32, mv -> mv.visitInsn(Opcodes.IMUL)),
				new Builtin("div", i32i32i32, mv -> mv.visitInsn(Opcodes.IDIV)));
	}
}
