package zlk.bytecodegen;

import static zlk.util.ErrorUtils.neverHappen;
import static zlk.util.ErrorUtils.todo;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import zlk.clcalc.CcApp;
import zlk.clcalc.CcDecl;
import zlk.clcalc.CcExp;
import zlk.clcalc.CcModule;
import zlk.clcalc.CcVar;
import zlk.common.cnst.ConstValue;
import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.common.type.TyArrow;
import zlk.common.type.TyBase;
import zlk.common.type.Type;
import zlk.core.Builtin;
import zlk.util.Stack;

public final class BytecodeGenerator {

	private final CcModule module;
	private final IdMap<Type> types;
	private final IdMap<Builtin> builtins;
	private final IdMap<String> toplevelDescs;
	private final IdMap<CcDecl> toplevelDecls;
	private ClassWriter cw;

	// for compileDecl
	private IdList locals;
	private MethodVisitor mv;
	private Stack<Runnable> pendings;

	public BytecodeGenerator(CcModule module, IdMap<Type> types, List<Builtin> builtins) {
		this.module = module;
		this.types = types;
		this.builtins = builtins.stream().collect(IdMap.collector(b -> b.id(), b -> b));
		this.toplevelDescs = new IdMap<>();
		this.toplevelDecls = new IdMap<>();
		this.pendings = new Stack<>();
	}

	public byte[] compile() {
		module.toplevels().forEach(decl -> {
			toplevelDescs.put(decl.id(), getDescription(decl, types));
			toplevelDecls.put(decl.id(), decl);
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

	private void compileDecl(CcDecl decl) { // TODO トップレベルは全て非カリー化する
		try {
			locals = new IdList(decl.args());

			mv = cw.visitMethod(
					Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
					javaMethodName(decl.id()),
					toplevelDescs.get(decl.id()),
					null,
					null);

			mv.visitCode();
			compile(decl.body());
			genReturn(types.get(decl.id()).apply(decl.arity()));
			mv.visitMaxs(-1, -1); // compute all frames and local automatically
			mv.visitEnd();

			while(!pendings.isEmpty()) {
				pendings.pop().run();
			}
		} catch(RuntimeException e) {
			throw new RuntimeException("on method "+decl.id(), e);
		}
	}

	private void compile(CcExp exp) {
		exp.match(
				cnst -> {
					loadCnst(cnst.value());
				},
				var -> {
					// note: It cannot decide with only appearance of 'var' whether the invocation
					//       static or not, so invokestatic places in app case.

					Id id = var.id();
					switchByDecl(id,
							descriptor -> {
								Type funType = types.get(id);
								if(funType instanceof TyArrow arrow) {
									List<Type> type = arrow.flatten();
									Id nextId = null;
									for (int i = type.size()-1; i >= 0; i--) {
										List<Type> args = type.subList(0, i);
										List<Type> ret  = type.subList(i, type.size());

										Id lambdaId = Id.fromParentAndSimpleName(Id.fromParentAndSimpleName(id, ""), i + "");

										if(args.size() == type.size()-1) {
											genBoxedMethod(lambdaId, args, id, ret.get(0));
										} else {
											genCurryingStep(lambdaId, args, nextId, Type.arrow(ret));
										}
										nextId = lambdaId;
									}

									mv.visitMethodInsn(
											Opcodes.INVOKESTATIC,
											module.name(),
											javaMethodName(nextId),
											"()"+functionDesc,
											false);
								} else {
									mv.visitMethodInsn(
											Opcodes.INVOKESTATIC,
											module.name(),
											javaMethodName(id),
											descriptor,
											false);
								}
							},
							builtin -> {
								todo("make method and currying");
							},
							localIdx -> {
								loadLocal(localIdx, types.get(id));
							});
//							descriptor -> {
//
//
//								insnStack.push(mv ->
//										mv.visitMethodInsn(
//											Opcodes.INVOKESTATIC,
//											module.name(),
//											javaMethodName(id),
//											descriptor,
//											false));
//							},
//							builtin -> {
//								insnStack.push(builtin);
//							},
//							localIdx -> {
//								loadLocal(localIdx, types.get(id));
//							});
				},
				app  -> {
					List<CcExp> args = app.args();
					Type funType = getType(app.fun());
					if(app.fun() instanceof CcVar var) {
						Id funId = var.id();

						switchByDecl(funId,
								descriptor -> {
									int arity = toplevelDecls.get(funId).arity();
									if(args.size() >= arity) {
										for (int i = 0; i < arity; i++) {
											compile(args.get(i));
										}

										mv.visitMethodInsn(
												Opcodes.INVOKESTATIC,
												module.name(),
												javaMethodName(funId),
												descriptor,
												false);

										for (int i = arity; i < args.size(); i++) {
											compile(args.get(i));
											invokeApplyWithBoxing(funType.apply(i).asArrow());
										}
									} else {
										// TODO opt by indy
										compile(var);
										for (int i = 0; i < args.size(); i++) {
											compile(args.get(i));
											invokeApplyWithBoxing(funType.apply(i).asArrow());
										}

										todo("partial application");
									}
								},
								builtin -> {
									int arity = types.get(funId).flatten().size() - 1;
									if(args.size() == arity) {
										for (int i = 0; i < arity; i++) {
											compile(args.get(i));
										}

										builtin.accept(mv);
									} else if (args.size() < arity) {
										todo("partial application");
									} else {
										neverHappen("builtins never return function object", app.loc());
									}
								},
								localIndex -> {
									compile(var);
									for (int i = 0; i < args.size(); i++) {
										compile(args.get(i));
										invokeApplyWithBoxing(funType.apply(i).asArrow());
									}
								});
					} else {
						compile(app.fun());

						// function object remains on stack top
						for (int i = 0; i < args.size(); i++) {
							compile(args.get(i));
							invokeApplyWithBoxing(funType.apply(i).asArrow());
						}
					}
				},
				mkCls -> {
					Id impl = mkCls.clsFunc();
					IdList caps = mkCls.caps();
					List<Type> indyArgTys = caps.stream().map(types::get).toList();
					TyArrow indyReturnTy = types.get(impl).apply(indyArgTys.size()).asArrow();

					if(indyReturnTy == null) {
						throw new Error(impl.toString());
					}

					caps.forEach(cap -> {
						loadLocal(locals.indexOf(cap), types.get(cap));
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
									javaMethodName(impl),
									toplevelDescs.get(impl),
									false),
							toMethodType(toBoxedDesc(indyReturnTy)));
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
					storeLocal(locals.size()-1, types.get(var));
					compile(let.mainExp());
				});
	}

	private void switchByDecl(Id id,
			Consumer<String> forTopLevelDescriptor,
			Consumer<Builtin> forBuiltin,
			Consumer<Integer> forLocalIdx) {

		String descriptor = toplevelDescs.getOrNull(id);
		if(descriptor != null) {
			forTopLevelDescriptor.accept(descriptor);
			return;
		}

		Builtin builtin = builtins.getOrNull(id);
		if(builtin != null) {
			forBuiltin.accept(builtin);
			return;
		}

		int localIdx = locals.indexOf(id);
		if(localIdx != -1) {
			forLocalIdx.accept(localIdx);
			return;
		}

		neverHappen("id must be found:"+id);
	}

	// TODO 名前に「計画に追加する」ニュアンスを
	// TODO retTyからSignatureTypeを書く
	private void genBoxedMethod(Id name, List<Type> argTys, Id original, Type retTy) {
		String desc = toBoxedDesc(argTys, retTy);
		toplevelDescs.put(name, desc);
		pendings.push(() -> {
			mv = cw.visitMethod(
					Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC + Opcodes.ACC_SYNTHETIC,
					javaMethodName(name),
					desc,
					null,
					null);

			mv.visitCode();
			// unboxing
			for (int i = 0; i < argTys.size(); i++) {
				mv.visitIntInsn(Opcodes.ALOAD, i);
				genUnboxing(argTys.get(i).asBase());
			}

			// call original
			mv.visitMethodInsn(
					Opcodes.INVOKESTATIC,
					module.name(),
					javaMethodName(original),
					toplevelDescs.get(original),
					false);

			// boxing
			genBoxing(retTy.asBase());

			// return
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(-1, -1); // compute all frames and local automatically
			mv.visitEnd();
		});
	}

	private void genCurryingStep(Id name, List<Type> argTys, Id target, Type retTy) {
		String desc = toBoxedDesc(argTys, retTy);
		String sign = toBoxedSignature(argTys, retTy);
		toplevelDescs.put(name, desc);
		pendings.push(() -> {
			mv = cw.visitMethod(
					Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC + Opcodes.ACC_SYNTHETIC,
					javaMethodName(name),
					desc,
					sign,
					null);

			mv.visitCode();

			for (int i = 0; i < argTys.size(); i++) {
				mv.visitIntInsn(Opcodes.ALOAD, i);
			}

			mv.visitInvokeDynamicInsn(
					"apply",
					desc,
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
					"("+objectDesc+")"+objectDesc,
					new Handle(
							Opcodes.H_INVOKESTATIC,
							module.name(),
							javaMethodName(target),
							toplevelDescs.get(target),
							false),
					toMethodType(toBoxedDesc(retTy.asArrow())));

			mv.visitInsn(Opcodes.ARETURN);

			mv.visitMaxs(-1, -1); // compute all frames and local automatically
			mv.visitEnd();
		});
	}

	private void loadCnst(ConstValue cnst) {
		cnst.match(
				bool -> { mv.visitLdcInsn(bool.value() ? 1 : 0); },
				i32  -> { mv.visitLdcInsn(i32.value());});
	}

	private void loadLocal(int idx, Type ty) {
		ty.match(
				base       -> { mv.visitVarInsn(Opcodes.ILOAD, idx); },
				(arg, ret) -> { mv.visitVarInsn(Opcodes.ALOAD, idx); },
				varId      -> { new Error("typevar"); });
	}

	private void storeLocal(int idx, Type ty) {
		ty.match(
				base       -> { mv.visitVarInsn(Opcodes.ISTORE, idx); },
				(arg, ret) -> { mv.visitVarInsn(Opcodes.ASTORE, idx); },
				varId      -> { new Error("typevar"); });
	}

	private void genBoxing(TyBase type) {
		Boxing boxing = Boxing.of(type);
		mv.visitMethodInsn(
				Opcodes.INVOKESTATIC,
				boxing.boxedClassName,
				"valueOf",
				boxing.boxMethodDesc,
				false);
	}

	private void genUnboxing(TyBase type) {
		Boxing boxing = Boxing.of(type);
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				boxing.boxedClassName,
				boxing.unboxMethodName,
				boxing.unboxMethodDesc,
				false);
	}

	private void genReturn(Type type) {
		type.match(
				base       -> mv.visitInsn(Opcodes.IRETURN),
				(arg, ret) -> mv.visitInsn(Opcodes.ARETURN),
				varId      -> { new Error("typevar"); });
	}

	private void invokeApplyWithBoxing(TyArrow ty) {
		Type argTy = ty.arg();
		if(!argTy.isArrow()) {
			Boxing boxing = Boxing.of(argTy.asBase());
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
			Boxing boxing = Boxing.of(retTy.asBase());
			mv.visitTypeInsn(Opcodes.CHECKCAST, boxing.boxedClassName);

			mv.visitMethodInsn(
					Opcodes.INVOKEVIRTUAL,
					boxing.boxedClassName,
					boxing.unboxMethodName,
					boxing.unboxMethodDesc,
					false);
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
	private static String toBoxedDesc(TyArrow ty) {
		return toDesc(ty.arg(), ty.ret(), ty_ -> ty_.isArrow() ? functionDesc : Boxing.of(ty_.asBase()).boxedClassDesc);
	}

	private static String getDescription(CcDecl decl, IdMap<Type> types) {
		IdList args = decl.args();
		List<Type> argTys = args.stream().map(types::get).toList();
		Type retTy = types.get(decl.id()).apply(args.size());
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
		argTys.forEach(ty -> sb.append(toBinary(ty.asBase())));
		sb.append(")");
		if(retTy.isArrow()) {
			sb.append(toFunctionClassDesc(retTy.asArrow()));
		} else {
			sb.append(toBinary(retTy.asBase()));
		}
		return sb.toString();
	}

	private static String toBoxedDesc(List<Type> argTys, Type retTy) {
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		argTys.forEach(ty -> sb.append(toBoxed(ty)));
		sb.append(")");
		sb.append(toBoxed(retTy));
		return sb.toString();
	}

	private static String toBoxedSignature(List<Type> argTys, Type retTy) {
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		argTys.forEach(ty -> sb.append(toBoxedSignature(ty)));
		sb.append(")");
		sb.append(toBoxedSignature(retTy));
		return sb.toString();
	}

	private static final String objectDesc = "Ljava/lang/Object;";
	private static final String functionDesc = "Ljava/util/function/Function;";

	private static String toBinary(TyBase ty) {
		return switch (ty) {
		case BOOL: { yield "Z"; }
		case I32:  { yield "I"; }
		default:
			throw new IllegalArgumentException("Unexpected value: " + ty);
		};
	}

	private static String toBoxed(Type ty) {
		return ty.fold(
				base       -> Boxing.of(base).boxedClassDesc,
				(arg, ret) -> functionDesc,
				varId      -> { throw new Error("typevar"); }
		);
	}

	private static String toBoxedSignature(Type ty) {
		return ty.fold(
				base       -> Boxing.of(base).boxedClassDesc,
				(arg, ret) -> "Ljava/util/function/Function<"+toBoxedSignature(arg)+toBoxedSignature(ret)+">;",
				varId      -> { throw new Error("typevar"); }
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

	private Type returnType(CcApp call) {
		Type funTy = getType(call.fun());
		return funTy.apply(call.args().size());
	}

	private Type getType(CcExp exp) {
		return exp.fold(
				cnst  -> cnst.type(),
				var   -> types.get(var.id()),
				call  -> returnType(call),
				mkCls -> types.get(mkCls.id()),
				if_   -> getType(if_.thenExp()),
				let   -> getType(let.mainExp()));
	}

	private static Pattern separatorReplacer = Pattern.compile(Id.SEPARATOR, Pattern.LITERAL);
	private String javaMethodName(Id id) {
		String canonical = id.canonicalName();
		String moduleName = module.name();
		if(!canonical.startsWith(moduleName)) {
			throw new IllegalArgumentException("illegal name: "+canonical+", module: "+moduleName);
		}

		return separatorReplacer.matcher(
				canonical.subSequence(moduleName.length()+1, canonical.length())).replaceAll("\\$");
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

	public static Boxing of(TyBase ty) {
		return switch (ty) {
		case BOOL: { yield BOOL; }
		case I32:  { yield INT; }
		default:
			throw new IllegalArgumentException("Unexpected value: " + ty);
		};
	}
}
