package zlk.core;

import static zlk.common.Type.BOOL;
import static zlk.common.Type.I32;

import java.util.List;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import zlk.bytecodegen.Instructions;
import zlk.bytecodegen.Primitive;
import zlk.common.Type;
import zlk.common.id.Id;

public record Builtin(
		Id id,
		Type type,
		Instructions insn)
implements Instructions
{
	public static List<Builtin> functions(){
		Type i32i32i32 = Type.arrow(I32, I32, I32);
		Type i32bool = Type.arrow(I32, BOOL);

		return List.of(
				new Builtin("Basic.False", BOOL, mv -> {
					mv.visitInsn(Opcodes.ICONST_0);
					Primitive.BOOL.genBoxing(mv);
				}),
				new Builtin("Basic.True", BOOL, mv -> {
					mv.visitInsn(Opcodes.ICONST_1);
					Primitive.BOOL.genBoxing(mv);
				}),
				new Builtin("Basic.isZero", i32bool, mv -> {
					Label lTrue = new Label();
					Label lEnd = new Label();
					Primitive.INT.genUnboxing(mv);
					mv.visitJumpInsn(Opcodes.IFEQ, lTrue);
					mv.visitInsn(Opcodes.ICONST_0);
					mv.visitJumpInsn(Opcodes.GOTO, lEnd);
					mv.visitLabel(lTrue);
					mv.visitInsn(Opcodes.ICONST_1);
					mv.visitLabel(lEnd);
					Primitive.BOOL.genBoxing(mv);
				}),
				new Builtin("Basic.add", i32i32i32, mv -> {
					Primitive.INT.genUnboxing(mv);
					mv.visitInsn(Opcodes.SWAP);
					Primitive.INT.genUnboxing(mv);
					mv.visitInsn(Opcodes.IADD);
					Primitive.INT.genBoxing(mv);
				}),
				new Builtin("Basic.sub", i32i32i32, mv -> {
					Primitive.INT.genUnboxing(mv);
					mv.visitInsn(Opcodes.SWAP);
					Primitive.INT.genUnboxing(mv);
					mv.visitInsn(Opcodes.SWAP);
					mv.visitInsn(Opcodes.ISUB);
					Primitive.INT.genBoxing(mv);
				}),
				new Builtin("Basic.mul", i32i32i32, mv -> {
					Primitive.INT.genUnboxing(mv);
					mv.visitInsn(Opcodes.SWAP);
					Primitive.INT.genUnboxing(mv);
					mv.visitInsn(Opcodes.IMUL);
					Primitive.INT.genBoxing(mv);
				}),
				new Builtin("Basic.div", i32i32i32, mv -> {
					Primitive.INT.genUnboxing(mv);
					mv.visitInsn(Opcodes.SWAP);
					Primitive.INT.genUnboxing(mv);
					mv.visitInsn(Opcodes.SWAP);
					mv.visitInsn(Opcodes.IDIV);
					Primitive.INT.genBoxing(mv);
				}));
	}

	public Builtin(String canonical, Type type, Instructions insn) {
		this(Id.fromCanonicalName(canonical), type, insn);
	}

	@Override
	public void accept(MethodVisitor mv) {
		insn.accept(mv);
	}
}
