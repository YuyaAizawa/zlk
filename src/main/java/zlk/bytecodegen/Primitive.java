package zlk.bytecodegen;

import java.util.Optional;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import zlk.common.Type;

enum Primitive {
	BOOL ("java/lang/Boolean", "booleanValue", "()Z", "(Z)Ljava/lang/Boolean;"),
	INT  ("java/lang/Integer",     "intValue", "()I", "(I)Ljava/lang/Integer;");

	public final String boxedClassName;
	public final String boxedClassDesc;
	public final String unboxMethodName;
	public final String unboxMethodDesc;
	public final String boxMethodDesc;

	private Primitive(
			String boxedClassName,
			String unboxMethodName,
			String unboxMethodDesc,
			String boxMethodDecs) {
		this.boxedClassName = boxedClassName;
		this.boxedClassDesc = "L"+boxedClassName+";";
		this.unboxMethodName = unboxMethodName;
		this.unboxMethodDesc = unboxMethodDesc;
		this.boxMethodDesc = boxMethodDecs;
	}

	static Primitive of(Type.CtorApp ty) {
		if(ty.equals(Type.BOOL)) { return BOOL; }
		if(ty.equals(Type.I32))  { return INT; }
		throw new IllegalArgumentException("Unexpected value: " + ty);
	}

	static Optional<Primitive> tryFrom(Type ty) {
		if(ty.equals(Type.BOOL)) { return Optional.of(BOOL); }
		if(ty.equals(Type.I32))  { return Optional.of(INT); }
		return Optional.empty();
	}

	void genBoxing(MethodVisitor mv) {
		mv.visitMethodInsn(
				Opcodes.INVOKESTATIC,
				this.boxedClassName,
				"valueOf",
				this.boxMethodDesc,
				false);
	}

	void genUnboxing(MethodVisitor mv) {
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				this.boxedClassName,
				this.unboxMethodName,
				this.unboxMethodDesc,
				false);
	}
}

