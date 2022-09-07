package zlk.bytecodegen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import zlk.common.TyArrow;
import zlk.common.Type;
import zlk.core.Builtin;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcModule;
import zlk.idcalc.IdInfo;

public final class BytecodeGenerator {

	private String moduleName;
	private Set<IdInfo> functionsInModule;
	private Map<IdInfo, Builtin> builtins;
	private ClassWriter cw;
	private MethodVisitor mv;

	public BytecodeGenerator(Map<IdInfo, Builtin> builtins) {
		this.builtins = builtins;
	}

	public byte[] compile(IcModule module) {
		moduleName = module.name();

		cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

		cw.visit(
				Opcodes.V16,
				Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_SUPER,
				moduleName,
				null,
				"java/lang/Object",
				null);

		cw.visitSource(module.origin() + ".zlk", null);

		genConstructor();

		functionsInModule = module.decls().stream().map(decl -> decl.id()).collect(Collectors.toSet());
		module.decls().forEach(decl -> genExposedFunction(decl));

		/////////////////
		genLambdaExpTest();
		/////////////////

		cw.visitEnd();
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

	private void genExposedFunction(IcDecl decl) {

		MethodStyle methodType = MethodStyle.of(decl.type());

		mv = cw.visitMethod(
				Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
				decl.id().name(),
				methodType.toDescription(),
				null,
				null);
		mv.visitCode();

		LocalEnv locals = new LocalEnv();
		decl.args().forEach(arg -> locals.bind(arg));
		genCode(decl.body(), locals);

		genReturn(methodType.ret());

		mv.visitMaxs(-1, -1); // compute all frames and localautomatically
		mv.visitEnd();
	}

	private void genCode(IcExp exp, LocalEnv env) {
		exp.match(
				cnst ->
				mv.visitLdcInsn(cnst.cnst().fold(
						bool -> bool.value() ? 1 : 0,
								i32  -> i32.value())),
				var -> {
					IdInfo idInfo = var.idInfo();
					if(functionsInModule.contains(idInfo)) {
						mv.visitMethodInsn(
								Opcodes.INVOKESTATIC,
								moduleName,
								idInfo.name(),
								MethodStyle.of(idInfo.type()).toDescription(),
								false);
					} else {
						Builtin builtin = builtins.get(idInfo);
						if(builtin == null) {
							load(env.find(idInfo));
						} else {
							builtin.action().accept(mv);
						}
					}
				},
				app  -> {
					app.args().forEach(arg -> genCode(arg, env));
					genCode(app.fun(), env);
				},
				ifExp -> {
					Label l1 = new Label();
					Label l2 = new Label();
					genCode(ifExp.cond(), env);
					mv.visitJumpInsn(
							Opcodes.IFEQ, // = 0; false
							l1);
					genCode(ifExp.exp1(), env);
					mv.visitJumpInsn(Opcodes.GOTO, l2);
					mv.visitLabel(l1);
					genCode(ifExp.exp2(), env);
					mv.visitLabel(l2);
				},
				let -> {
					genCode(let.decl().body(), env);
					store(env.bind(let.decl().id()));
					genCode(let.body(), env);
				});
	}

	private void load(LocalVar local) {
		if(local.type() == Type.i32) {
			mv.visitVarInsn(Opcodes.ILOAD, local.idx());
		} else {
			throw new Error(String.format("invalid type local variable. type: %s", local.type().toString()));
		}
	}

	private void store(LocalVar local) {
		if(local.type() == Type.i32) {
			mv.visitVarInsn(Opcodes.ISTORE, local.idx());
		} else {
			throw new Error(String.format("invalid type local variable. type: %s", local.type().toString()));
		}
	}

	private void genReturn(Type type) {
		type.match(
				unit -> mv.visitInsn(Opcodes.RETURN),
				bool -> mv.visitInsn(Opcodes.IRETURN),
				i32  -> mv.visitInsn(Opcodes.IRETURN),
				fun  -> {throw new IllegalArgumentException(type.toString());});
	}

	private void genLambdaExpTest() {

		String implMehtod = "implMethod";

		/*
		 * private static Long implMethod(float arg0, Integer arg1) {
		 *   return Long.valueOf(((long)(arg1.intValue())) - ((long)((int)(arg0))));
		 * }
		 */
		mv = cw.visitMethod(
				Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC + Opcodes.ACC_SYNTHETIC,
				implMehtod,
				"(FLjava/lang/Integer;)Ljava/lang/Long;",
				null,
				null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				"java/lang/Integer",
				"intValue",
				"()I",
				false);
		mv.visitInsn(Opcodes.I2L);
		mv.visitVarInsn(Opcodes.FLOAD, 0);
		mv.visitInsn(Opcodes.F2I);
		mv.visitInsn(Opcodes.I2L);
		mv.visitInsn(Opcodes.LSUB);
		mv.visitMethodInsn(
				Opcodes.INVOKESTATIC,
				"java/lang/Long",
				"valueOf",
				"(J)Ljava/lang/Long;",
				false);
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(4, 2);
		mv.visitEnd();

		/*
		 * public static main(String[] args) {
		 *   float x = 2;
		 *   Function<Integer, Long> f = i -> (long)i - (int)x;
		 *   long l = f.apply(3);
		 *   System.out.println(l);
		 * }
		 *
		 * LocalVariableTable:
		 *   0 args [Ljava/lang/String;
		 *   1    x F
		 *   2    f Ljava/util/function/Function;
		 *   3    l J
		 *
		 * LocalVariableTypeTable:
		 *   2    f Ljava/util/function/Function<Ljava/lang/Integer;Ljava/lang/Long;>;
		 */
		mv = cw.visitMethod(
				Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
				"main",
				"([Ljava/lang/String;)V",
				null,
				null);
		mv.visitCode();
		mv.visitInsn(Opcodes.FCONST_2);
		mv.visitVarInsn(Opcodes.FSTORE, 1);
		mv.visitVarInsn(Opcodes.FLOAD, 1);
		mv.visitInvokeDynamicInsn(
				"apply",
				"(F)Ljava/util/function/Function;",
				new Handle(
						Opcodes.H_INVOKESTATIC,
						"java/lang/invoke/LambdaMetafactory",
						"metafactory",
						"(Ljava/lang/invoke/MethodHandles$Lookup;"
						+ "Ljava/lang/String;"
						+ "Ljava/lang/invoke/MethodType;"
						+ "Ljava/lang/invoke/MethodType;"
						+ "Ljava/lang/invoke/MethodHandle;"
						+ "Ljava/lang/invoke/MethodType;"
						+ ")Ljava/lang/invoke/CallSite;",
						false),
				getMethodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
				new Handle(
						Opcodes.H_INVOKESTATIC,
						moduleName,
						implMehtod,
						"(FLjava/lang/Integer;)Ljava/lang/Long;",
						false),
				getMethodType("(Ljava/lang/Integer;)Ljava/lang/Long;"));
		mv.visitVarInsn(Opcodes.ASTORE, 2);
		mv.visitVarInsn(Opcodes.ALOAD, 2);
		mv.visitInsn(Opcodes.ICONST_3);
		mv.visitMethodInsn(
				Opcodes.INVOKESTATIC,
				"java/lang/Integer",
				"valueOf",
				"(I)Ljava/lang/Integer;",
				false);
		mv.visitMethodInsn(
				Opcodes.INVOKEINTERFACE,
				"java/util/function/Function",
				"apply",
				"(Ljava/lang/Object;)Ljava/lang/Object;",
				true);
		mv.visitTypeInsn(
				Opcodes.CHECKCAST,
				"java/lang/Long");
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				"java/lang/Long",
				"longValue",
				"()J",
				false);
		mv.visitVarInsn(Opcodes.LSTORE, 3);
		mv.visitFieldInsn(
				Opcodes.GETSTATIC,
				"java/lang/System",
				"out",
				"Ljava/io/PrintStream;");
		mv.visitVarInsn(Opcodes.LLOAD, 3);
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				"java/io/PrintStream",
				"println",
				"(J)V",
				false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(3, 5);
		mv.visitEnd();
	}

	private static org.objectweb.asm.Type getMethodType(String descriptor) {
		return org.objectweb.asm.Type.getMethodType(descriptor);
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
		return type.fold(
				unit -> "V",
				bool -> "Z",
				i32  -> "I",
				fun  -> { throw new IllegalArgumentException(type.toString()); });
	}
}