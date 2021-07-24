package zlk.bytecodegen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import zlk.ast.CompileUnit;
import zlk.ast.Decl;
import zlk.ast.Exp;
import zlk.common.Type;

public final class BytecodeGenerator {

	private static final int FLAG = ClassWriter.COMPUTE_MAXS;

	private static final Map<String, Consumer<MethodVisitor>> builtins = Map.of(
			"add", mv -> mv.visitInsn(Opcodes.IADD),
			"sub", mv -> mv.visitInsn(Opcodes.ISUB),
			"mul", mv -> mv.visitInsn(Opcodes.IMUL),
			"div", mv -> mv.visitInsn(Opcodes.IDIV));

	private ClassWriter cw;
	private MethodVisitor mv;
	private Map<String, MethodInfo> methodInfo;

	public byte[] compile(CompileUnit compileUnit) {

		List<Decl> decls = compileUnit.decls();

		registerSignatures(compileUnit);

		cw = new ClassWriter(FLAG);

		cw.visit(
				Opcodes.V16,
				Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER,
				compileUnit.name(),
				null,
				"java/lang/Object",
				null);

		cw.visitSource(compileUnit.name() + ".zlk", null);

		genConstructor();

		decls.forEach(decl -> genCode(decl));

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

	private void registerSignatures(CompileUnit cu) {
		methodInfo = new HashMap<>();
		cu.decls().forEach(
				decl -> methodInfo.put(decl.name(), new MethodInfo(
						cu.name(),
						decl.name(),
						toDescription(decl.type()))));
	}

	private void genCode(Decl decl) {

		MethodInfo info = methodInfo.get(decl.name());

		mv = cw.visitMethod(
				Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
				info.name(),
				info.desc(),
				null,
				null);
		mv.visitCode();

		genCode(decl.body());

		Type ret = decl.type().map(
				unit -> unit,
				bool -> bool,
				i32  -> i32,
				fun  -> fun.ret());

		genReturn(ret);

		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	private void genCode(Exp exp) {
		exp.match(
			cnst -> {
				mv.visitLdcInsn(cnst.map(
						bool -> bool.value() ? 1 : 0,
						i32  -> i32.value()));
			},
			id -> {
				Consumer<MethodVisitor> builtin = builtins.get(id.name());
				if(builtin != null) {
					builtin.accept(mv);
				} else {
					MethodInfo info = methodInfo.get(id.name());
					mv.visitMethodInsn(
							Opcodes.INVOKESTATIC,
							info.owner(),
							info.name(),
							info.desc(),
							false);
				}
			},
			app  -> {
				genCode(app.arg());
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

	private static String toDescription(Type type) {
		Type arg = type.map(
				unit -> Type.unit,
				bool -> Type.unit,
				i32  -> Type.unit,
				fun  -> fun.arg());

		Type ret = type.map(
				unit -> unit,
				bool -> bool,
				i32  -> i32,
				fun  -> fun.ret());

		return "("+toBinary(arg)+")"+toBinary(ret);
	}

	private static String toBinary(Type type) {
		return type.map(
				unit -> "",
				bool -> "Z",
				i32  -> "I",
				fun -> {throw new IllegalArgumentException(type.mkString());});
	}
}
