package zlk.bytecodegen;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import zlk.common.TyArrow;
import zlk.common.Type;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcModule;

public final class BytecodeGenerator {

	private static final int FLAG = ClassWriter.COMPUTE_MAXS;

	private ClassWriter cw;
	private MethodVisitor mv;

	public byte[] compile(IcModule module) {
		cw = new ClassWriter(FLAG);

		cw.visit(
				Opcodes.V16,
				Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_SUPER,
				module.name(),
				null,
				"java/lang/Object",
				null);

		cw.visitSource(module.origin() + ".zlk", null);

		genConstructor();

		module.decls().forEach(decl -> genCode(decl));

		genMain();

		return cw.toByteArray();
	}

	private void genConstructor() {
		mv = cw.visitMethod(
				Opcodes.ACC_PUBLIC,
				"<init>",
				"()V",
				null,
				null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(
				Opcodes.INVOKESPECIAL,
				"java/lang/Object",
				"<init>",
				"()V",
				false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	private void genMain() {
		mv = cw.visitMethod(
				Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
				"main",
				"([Ljava/lang/String;)V",
				null,
				null);
		mv.visitCode();
		mv.visitFieldInsn(
				Opcodes.GETSTATIC,
				"java/lang/System",
				"out",
				"Ljava/io/PrintStream;");
		mv.visitMethodInsn(Opcodes.INVOKESTATIC,
				"HelloMyLang",
				"ans",
				"()I",
				false);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
				"java/io/PrintStream",
				"println",
				"(I)V",
				false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	private void genCode(IcDecl decl) {

		MethodStyle methodType = MethodStyle.of(decl.type());

		mv = cw.visitMethod(
				Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
				decl.fun().name(),
				methodType.toDescription(),
				null,
				null);
		mv.visitCode();

		genCode(decl.body());

		genReturn(methodType.ret());

		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	private void genCode(IcExp exp) {
		exp.match(
			cnst ->
				mv.visitLdcInsn(cnst.cnst().map(
						bool -> bool.value() ? 1 : 0,
						i32  -> i32.value())),
			id ->
				id.idInfo().match(
					fun ->
						mv.visitMethodInsn(
								Opcodes.INVOKESTATIC,
								fun.module(),
								fun.name(),
								MethodStyle.of(fun.type()).toDescription(),
								false),
					arg -> mv.visitIntInsn(Opcodes.ILOAD, arg.idx()),
					builtin ->builtin.action().accept(mv)),
			app  -> {
				app.args().forEach(arg -> genCode(arg));
				genCode(app.fun());
			});
	}

	private void genReturn(Type type) {
		type.match(
				unit -> mv.visitInsn(Opcodes.RETURN),
				bool -> mv.visitInsn(Opcodes.IRETURN),
				i32  -> mv.visitInsn(Opcodes.IRETURN),
				fun  -> {throw new IllegalArgumentException(type.mkString());});
	}
}

record MethodStyle(
		List<Type> args,
		Type ret)
{
	public static MethodStyle of(Type zlkStyle) {
		List<Type> args = new ArrayList<>();

		TyArrow arrow = zlkStyle.asArrow();
		Type ret = zlkStyle;
		while(arrow != null) {
			args.add(arrow.arg());
			ret = arrow.ret();
			arrow = ret.asArrow();
		}

		return new MethodStyle(args, ret);
	}

	public String toDescription() {
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		args.forEach(arg -> sb.append(toBinary(arg)));
		sb.append(")");
		sb.append(toBinary(ret));
		return sb.toString();
	}

	private static String toBinary(Type type) {
		return type.map(
				unit -> "V",
				bool -> "Z",
				i32  -> "I",
				fun -> {throw new IllegalArgumentException(type.mkString());});
	}
}