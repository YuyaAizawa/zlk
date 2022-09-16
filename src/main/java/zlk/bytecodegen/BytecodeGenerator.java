package zlk.bytecodegen;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import zlk.clcalc.CcDecl;
import zlk.clcalc.CcExp;
import zlk.clcalc.CcModule;
import zlk.common.cnst.ConstValue;
import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.common.type.TyArrow;
import zlk.common.type.Type;
import zlk.core.Builtin;
import zlk.util.Stack;

public final class BytecodeGenerator {

	private final CcModule module;
	private final IdMap<Builtin> builtins;
	private final IdMap<String> toplevelDescs;
	private ClassWriter cw;

	// for compileDecl
	private IdList locals;
	private Stack<Instructions> insnStack;
	private MethodVisitor mv;

	public BytecodeGenerator(CcModule module, IdMap<Builtin> builtins) {
		this.module = module;
		this.builtins = builtins;
		this.toplevelDescs = new IdMap<>();
	}

	public byte[] compile() {
		module.toplevels().forEach(decl -> {
			toplevelDescs.put(decl.id(), getDescription(decl));
		});

		cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

		cw.visit(
				Opcodes.V16,
				Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_SUPER,
				module.name(),
				null,
				"java/lang/Object",
				null);

		cw.visitSource(module.origin() + ".zlk", null);

		genConstructor();

		module.toplevels().forEach(decl -> compileDecl(decl));

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

	private void compileDecl(CcDecl decl) {
		insnStack = new Stack<>();
		locals = new IdList(decl.args());

		mv = cw.visitMethod(
				Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
				decl.id().name(),
				toplevelDescs.get(decl.id()),
				null,
				null);

		mv.visitCode();
		compile(decl.body());
		genReturn(decl.type().apply(decl.args().size()));
		mv.visitMaxs(-1, -1); // compute all frames and localautomatically
		mv.visitEnd();
	}

	private void compile(CcExp exp) {
		exp.match(
				cnst -> {
					loadCnst(cnst.value());
				},
				var -> {
					Id id = var.id();
					String descriptor = toplevelDescs.get(id);
					Builtin builtin = builtins.get(id);
					int localIdx = locals.indexOf(id);

					if(descriptor != null) {

						insnStack.push(mv ->
								mv.visitMethodInsn(
									Opcodes.INVOKESTATIC,
									module.name(),
									id.name(),
									descriptor,
									false));

					} else if(builtin != null) {
						insnStack.push(builtin);

					} else if(localIdx != -1){
						loadLocal(localIdx, id.type());

					} else {
						throw new Error("unkown id: "+id.toString());
					}
				},
				call  -> {
					// <funExpObject?, Args... funExpInvoke> を生成する
					compile(call.fun());
					call.args().forEach(arg -> compile(arg));
					insnStack.pop().insert(mv);

					// 呼ばれないかもしれないがFunctionが戻り値のときは詰んでおく
					TyArrow ty = call.returnType().asArrow();
					if(ty != null) {
						insnStack.push(invokeApplyWithBoxing(ty));
					}
				},
				mkCls -> {
					Id impl = mkCls.clsFunc();
					IdList caps = mkCls.caps();
					List<Type> indyArgTys = caps.stream().map(Id::type).toList();
					TyArrow indyReturnTy = impl.type().apply(indyArgTys.size()).asArrow(); // TODO ここFunctionを返すのかBiFunctionなのか

					if(indyReturnTy == null) {
						throw new Error(impl.toString());
					}

					caps.forEach(cap -> {
						loadLocal(locals.indexOf(cap), cap.type());
					});

					mv.visitInvokeDynamicInsn(
							"apply",
							toDesc(indyArgTys, indyReturnTy),
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
							toMethodType(toFunctionApplyDescErased(indyReturnTy)),
							new Handle(
									Opcodes.H_INVOKESTATIC,
									module.name(),
									impl.name(),
									toplevelDescs.get(impl),
									false),
							toMethodType(toFunctionApplyDesc(indyReturnTy)));

					insnStack.push(invokeApplyWithBoxing(indyReturnTy));
				},
				ifExp -> {
					Label l1 = new Label();
					Label l2 = new Label();
					compile(ifExp.cond());
					mv.visitJumpInsn(
							Opcodes.IFEQ, // = 0; false
							l1);
					compile(ifExp.thenExp());
					mv.visitJumpInsn(Opcodes.GOTO, l2);
					mv.visitLabel(l1);
					compile(ifExp.elseExp());
					mv.visitLabel(l2);
				},
				let -> {
					compile(let.boundExp());
					Id var = let.boundVar();
					locals.add(var);
					storeLocal(locals.size()-1, var.type());
					compile(let.mainExp());
				});
	}

	private void loadCnst(ConstValue cnst) {
		cnst.match(
				bool -> { mv.visitLdcInsn(bool.value() ? 1 : 0); },
				i32  -> { mv.visitLdcInsn(i32.value());});
	}

	private void loadLocal(int idx, Type ty) {
		ty.match(
				unit -> { new Error("no local var for type Unit"); },
				bool -> { mv.visitVarInsn(Opcodes.ILOAD, idx); },
				i32  -> { mv.visitVarInsn(Opcodes.ILOAD, idx); },
				fun  -> { mv.visitVarInsn(Opcodes.ALOAD, idx); });
	}

	private void storeLocal(int idx, Type ty) {
		ty.match(
				unit -> { new Error("no local var for type Unit"); },
				bool -> { mv.visitVarInsn(Opcodes.ISTORE, idx); },
				i32  -> { mv.visitVarInsn(Opcodes.ISTORE, idx); },
				fun  -> { mv.visitVarInsn(Opcodes.ASTORE, idx); });
	}

	private void genReturn(Type type) {
		type.match(
				unit -> mv.visitInsn(Opcodes.RETURN),
				bool -> mv.visitInsn(Opcodes.IRETURN),
				i32  -> mv.visitInsn(Opcodes.IRETURN),
				fun  -> mv.visitInsn(Opcodes.ARETURN));
	}

	// TODO 2個以上の引数への対応
	private static Instructions invokeApplyWithBoxing(TyArrow ty) {
		return mv -> {
			Type argTy = ty.arg();
			if(!argTy.isArrow()) {
				Boxing boxing = Boxing.of(argTy);
				mv.visitMethodInsn(
						Opcodes.INVOKESTATIC,
						boxing.boxedClassName,
						"valueOf",
						boxing.boxMethodDesc,
						false);
			}

			// カリー化されている前提
			mv.visitMethodInsn(
					Opcodes.INVOKEINTERFACE,
					"java/util/function/Function",
					"apply",
					"(Ljava/lang/Object;)Ljava/lang/Object;",
					true);

			Type retTy = ty.ret();
			if(retTy.isArrow()) {
				mv.visitTypeInsn(Opcodes.CHECKCAST, toFunctionClassName(retTy.asArrow()));
			} else {
				Boxing boxing = Boxing.of(retTy);
				mv.visitTypeInsn(Opcodes.CHECKCAST, boxing.boxedClassName);

				mv.visitMethodInsn(
						Opcodes.INVOKEVIRTUAL,
						boxing.boxedClassName,
						boxing.unboxMethodName,
						boxing.unboxMethodDesc,
						false);
			};
		};
	}

	private static String toFunctionApplyDescErased(TyArrow ty) {
		return toDesc(ty.arg(), ty.ret(), any -> objectDesc);
	}

	/**
	 * （カリー化された）関数オブジェクトに適用する際のディスクリプションを返す．
	 * @param ty
	 * @return ディスクリプション
	 */
	private static String toFunctionApplyDesc(TyArrow ty) {
		return toDesc(ty.arg(), ty.ret(), ty_ -> ty_.isArrow() ? "Ljava/util/function/Function;" : Boxing.of(ty_).boxedClassDesc);
	}

	private static String getDescription(CcDecl decl) {
		IdList args = decl.args();
		List<Type> argTys = args.stream().map(Id::type).toList();
		Type retTy = decl.type().apply(args.size());
		return toDesc(argTys, retTy);
	}

	private static String toDesc(Type argTy, Type retTy, Function<Type, String> mapper) {
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		sb.append(mapper.apply(argTy));
		sb.append(")");
		sb.append(mapper.apply(retTy));
		return sb.toString();
	}

	private static String toDesc(List<Type> argTys, Type retTy) {
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		argTys.forEach(ty -> sb.append(toBinary(ty)));
		sb.append(")");
		if(retTy.isArrow()) {
			sb.append(toFunctionClassDesc(retTy.asArrow()));
		} else {
			sb.append(toBinary(retTy));
		}
		return sb.toString();
	}

	static final String objectDesc = "Ljava/lang/Object;";

	private static String toBinary(Type ty) {
		return ty.fold(
				unit -> "V",
				bool -> "Z",
				i32  -> "I",
				fun  -> { throw new Error(ty.toString()); }
		);
	}

	private static String toFunctionClassDesc(TyArrow ty) {
		return "L"+toFunctionClassName(ty)+";";
	}

	private static String toFunctionClassName(TyArrow fun) {
		Objects.requireNonNull(fun);
		return "java/util/function/Function";
	}

	private static org.objectweb.asm.Type toMethodType(String descriptor) {
		return org.objectweb.asm.Type.getMethodType(descriptor);
	}
}

enum Boxing {
	BOOL ("java/lang/Boolean", "booleanValue", "()Z", "(Z)Ljava/lang/Boolean;"),
	INT  ("java/lang/Integer",     "intValue", "()I", "(I)Ljava/lang/Integer;");

	public final String boxedClassName;
	public final String boxedClassDesc;
	public final String unboxMethodName;
	public final String unboxMethodDesc;
	public final String boxMethodDesc;

	private Boxing(
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

	public static Boxing of(Type ty) {
		return ty.fold(
				unit -> { throw new Error(ty.toString()); },
				bool -> BOOL,
				i32  -> INT,
				fun  -> { throw new Error(ty.toString()); }
		);
	}
}
