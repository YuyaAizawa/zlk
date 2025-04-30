package zlk.bytecodegen;

import static zlk.util.ErrorUtils.neverHappen;
import static zlk.util.ErrorUtils.todo;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import zlk.clcalc.CcCaseBranch;
import zlk.clcalc.CcCtor;
import zlk.clcalc.CcExp;
import zlk.clcalc.CcExp.CcApp;
import zlk.clcalc.CcExp.CcCase;
import zlk.clcalc.CcExp.CcCnst;
import zlk.clcalc.CcExp.CcIf;
import zlk.clcalc.CcExp.CcLet;
import zlk.clcalc.CcExp.CcMkCls;
import zlk.clcalc.CcExp.CcVar;
import zlk.clcalc.CcFunDecl;
import zlk.clcalc.CcModule;
import zlk.clcalc.CcTypeDecl;
import zlk.common.ConstValue;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.core.Builtin;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcPattern;
import zlk.util.Location;
import zlk.util.Stack;

public final class BytecodeGenerator {

	private final CcModule module;
	private final IdMap<String> classNames;
	private final IdMap<Type> types;
	private final IdMap<Builtin> builtins;
	private final IdMap<String> toplevelDescs;
	private final IdMap<CcFunDecl> toplevelDecls;
	private final IdMap<CcCtor> ctors;
	private ClassWriter cw;

	// for compileDecl
	private IdList locals;
	private static final Id LOCAL_DUMMY_ID = Id.fromCanonicalName("..DUMMY..");
	private MethodVisitor mv;
	private Stack<Runnable> pendings;

	public BytecodeGenerator(CcModule module, IdMap<Type> types, List<Builtin> builtins) {
		this.module = module;
		this.classNames = new IdMap<>();
		this.types = types;
		this.builtins = builtins.stream().collect(IdMap.collector(b -> b.id(), b -> b));
		this.toplevelDescs = new IdMap<>();
		this.toplevelDecls = new IdMap<>();
		this.ctors = new IdMap<>();
		this.pendings = new Stack<>();
	}

	/**
	 * Output the compilation result to the specified BiConsumer as pairs of
	 * class name and byte sequence.
	 *
	 * @param fileWriter
	 */
	public void compile(BiConsumer<String, byte[]> fileWriter) {
		module.types().forEach(union -> {
			classNames.put(union.id(), unionSuperName(union));
			union.ctors().forEach(ctor -> {
				classNames.put(ctor.id(), unionSubName(union, ctor));
				ctors.put(ctor.id(), ctor);
			});
		});
		module.funcs().forEach(decl -> {
			toplevelDescs.put(decl.id(), getDescription(decl, types));
			toplevelDecls.put(decl.id(), decl);
		});

		module.types().forEach(union -> genUnionClass(union, fileWriter));
		genMainClass(fileWriter);
	}

	private String unionSuperName(CcTypeDecl union) {
		return module.name().replace('.', '/')+"$"+union.id().simpleName();
	}

	private String unionSubName(CcTypeDecl union, CcCtor ctor) {
		return module.name().replace('.', '/')+"$"+union.id().simpleName()+"$"+ctor.id().simpleName();
	}

	private void genUnionClass(CcTypeDecl union, BiConsumer<String, byte[]> fileWriter) {
		// super class
		genUnionSuperClass(union);
		fileWriter.accept(classNames.get(union.id()), cw.toByteArray());

		// sub classes
		for(CcCtor ctor : union.ctors()) {
			genUnionSubClass(ctor, union);
			fileWriter.accept(classNames.get(ctor.id()), cw.toByteArray());
		}
	}

	private void genUnionSuperClass(CcTypeDecl union) {
		cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		cw.visit(
				Opcodes.V16,
				Opcodes.ACC_PUBLIC + Opcodes.ACC_INTERFACE + Opcodes.ACC_ABSTRACT,
				classNames.get(union.id()),
				null,
				"java/lang/Object",
				null);
		cw.visitSource(module.origin() + ".zlk", null);
		cw.visitNestHost(module.name().replace(".", "/"));
		cw.visitEnd();
	}

	private void genUnionSubClass(CcCtor ctor, CcTypeDecl union) {
		cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		cw.visit(
				Opcodes.V16,
				Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_SUPER,
				classNames.get(ctor.id()),
				null,
				"java/lang/Object",
				new String[] {classNames.get(union.id())});
		cw.visitSource(module.origin() + ".zlk", null);

		for(int i = 0; i < ctor.args().size(); i++) {
			Type type = ctor.args().get(i);
			cw.visitField(
					Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL,
					"val"+i,
					toDesc(type),
					null,
					null);
		}

		genUnionSubClassConstructor(ctor, union);

		cw.visitNestHost(module.name().replace(".", "/"));

		cw.visitEnd();
	}

	private void genUnionSubClassConstructor(CcCtor ctor, CcTypeDecl union) {
		String argsStr = ctor.args().stream()
				.map(ty -> toDesc(ty))
				.collect(Collectors.joining(""));
		mv = cw.visitMethod(
				Opcodes.ACC_PUBLIC,
				"<init>",
				"("+argsStr+")V",
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

		for(int i = 0; i < ctor.args().size(); i++) {
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			Type ty = ctor.args().get(i);
			loadLocal(i+1, ty);
			mv.visitFieldInsn(
					Opcodes.PUTFIELD,
					classNames.get(ctor.id()),
					"val"+i,
					toDesc(ty));
		}

		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	private static String toDesc(Type type) {
		return switch(type) {
		case Type.Atom atom -> toBinary(atom);
		case Type.Arrow _ -> todo();
		case Type.Var(String name) -> { throw new Error(name); }
		};
	}

	private void genMainClass(BiConsumer<String, byte[]> fileWriter) {
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

		module.funcs().forEach(decl -> compileDecl(decl));

		module.types().forEach(union -> {
			cw.visitNestMember(toClassName(union.id()));
			union.ctors().forEach(ctor -> {
				cw.visitNestMember(toClassName(ctor.id()));
			});
		});

		cw.visitEnd();

		fileWriter.accept(module.origin(), cw.toByteArray());
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

	private void compileDecl(CcFunDecl decl) { // TODO トップレベルは全て非カリー化する
		try {
			locals = new IdList();

			mv = cw.visitMethod(
					Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
					javaMethodName(decl.id()),
					toplevelDescs.get(decl.id()),
					null,
					null);

			mv.visitCode();
			registerArgs(decl.args());
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

	/**
	 * 関数の引数をlocalsに登録する
	 * @param args 引数
	 */
	private void registerArgs(List<IcPattern> args) {
		args.forEach(arg -> {
			if(arg instanceof IcPattern.Var var) {
				locals.add(var.headId());
			} else {
				locals.add(LOCAL_DUMMY_ID);
			}
		});

		for (int i = 0; i < args.size(); i++) {
			if(args.get(i) instanceof IcPattern.Ctor ctor) {
				Type subClassTy = types.get(ctor.headId());
				loadLocal(i, subClassTy);
				registerArgRec(ctor);
			}
		}
	}
	private void registerArgRec(IcPattern pat) {
		// 事前条件：パターンに対応する値がstackのトップに乗っている
		// 事後条件：パターンに対応する値をstackから消費
		switch(pat) {
		case IcPattern.Var(Id id, Location _) -> {
			locals.add(id);
			storeLocal(locals.size()-1, types.get(id));
		}
		case IcPattern.Ctor(IcExp.IcVarCtor ctor, List<IcPattern.Arg> args, Location _) -> {
			String subClassName = classNames.get(ctor.id());

			// stackの数を調整
			if(args.size() == 0) { mv.visitInsn(Opcodes.POP); }
			for (int i = 0; i < args.size()-1; i++) {
				mv.visitInsn(Opcodes.DUP);
			}

			for(int fieldIdx = 0; fieldIdx < args.size(); fieldIdx++) {
				IcPattern.Arg ctorArg = args.get(fieldIdx);
				mv.visitFieldInsn(
						Opcodes.GETFIELD,
						subClassName,
						"val"+fieldIdx,
						toDesc(ctorArg.type()));
				registerArgRec(ctorArg.pattern());
			}
		}
		}
	}

	private void compile(CcExp exp) {
		switch (exp) {
		case CcCnst(ConstValue value, Location _) -> {
			loadCnst(value);
		}
		case CcVar(Id id, Location _) -> {
			switchByDecl(id, descriptor -> {
				Type funType = types.get(id);
				if (funType instanceof Type.Arrow arrow) {
					List<Type> type = arrow.flatten();
					Id nextId = null;
					for (int i = type.size() - 1; i >= 0; i--) {
						List<Type> args = type.subList(0, i);
						List<Type> ret = type.subList(i, type.size());

						Id lambdaId = Id.fromParentAndSimpleName(Id.fromParentAndSimpleName(id, ""),
								i + "");

						if (args.size() == type.size() - 1) {
							genBoxedMethod(lambdaId, args, id, ret.get(0));
						} else {
							genCurryingStep(lambdaId, args, nextId, Type.arrow(ret));
						}
						nextId = lambdaId;
					}

					mv.visitMethodInsn(Opcodes.INVOKESTATIC, module.name(), javaMethodName(nextId),
							"()" + functionDesc, false);
				} else {
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, module.name(), javaMethodName(id),
							descriptor, false);
				}
			}, _ -> {
				todo("make method and currying");
			}, localIdx -> {
				loadLocal(localIdx, types.get(id));
			}, ctor -> {
				if (ctor.args().size() != 0) {
					todo("currying");
				}
				String subclassName = classNames.get(ctor.id());
				mv.visitTypeInsn(Opcodes.NEW, subclassName);
				mv.visitInsn(Opcodes.DUP);
				mv.visitMethodInsn(Opcodes.INVOKESPECIAL, subclassName, "<init>", "()V", false);
			});
		}
		case CcApp(CcExp fun, List<CcExp> args, Location loc) -> {
			Type funType = getType(fun);
			if (fun instanceof CcVar var) {
				Id funId = var.id();

				switchByDecl(funId, descriptor -> {
					int arity = toplevelDecls.get(funId).arity();
					if (args.size() >= arity) {
						for (int i = 0; i < arity; i++) {
							compile(args.get(i));
						}

						mv.visitMethodInsn(Opcodes.INVOKESTATIC, module.name(),
								javaMethodName(funId), descriptor, false);

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
				}, builtin -> {
					int arity = types.get(funId).flatten().size() - 1;
					if (args.size() == arity) {
						for (int i = 0; i < arity; i++) {
							compile(args.get(i));
						}

						builtin.accept(mv);
					} else if (args.size() < arity) {
						todo("partial application");
					} else {
						neverHappen("builtins never return function object", loc);
					}
				}, _ -> {
					compile(var);
					for (int i = 0; i < args.size(); i++) {
						compile(args.get(i));
						invokeApplyWithBoxing(funType.apply(i).asArrow());
					}
				}, ctor -> {
					if (args.size() != ctor.args().size()) {
						todo("currying");
					}
					String subclassName = classNames.get(ctor.id());
					mv.visitTypeInsn(Opcodes.NEW, subclassName);
					mv.visitInsn(Opcodes.DUP);
					args.forEach(arg -> compile(arg));
					mv.visitMethodInsn(Opcodes.INVOKESPECIAL, subclassName, "<init>",
							toDesc(ctor.args(), Type.UNIT), false);
				});
			} else {
				compile(fun);

				// function object remains on stack top
				for (int i = 0; i < args.size(); i++) {
					compile(args.get(i));
					invokeApplyWithBoxing(funType.apply(i).asArrow());
				}
			}
		}
		case CcMkCls(Id clsFunc, IdList caps, Location _) -> {
			Id impl = clsFunc;
			List<Type> indyArgTys = caps.stream().map(types::get).toList();
			Type.Arrow indyReturnTy = types.get(impl).apply(indyArgTys.size()).asArrow();

			if (indyReturnTy == null) {
				throw new Error(impl.toString());
			}

			caps.forEach(cap -> {
				loadLocal(locals.indexOf(cap), types.get(cap));
			});

			mv.visitInvokeDynamicInsn("apply", toDesc(indyArgTys, indyReturnTy), new Handle(
					Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
					"(Ljava/lang/invoke/MethodHandles$Lookup;" + "Ljava/lang/String;"
							+ "Ljava/lang/invoke/MethodType;" + "Ljava/lang/invoke/MethodType;"
							+ "Ljava/lang/invoke/MethodHandle;" + "Ljava/lang/invoke/MethodType;"
							+ ")Ljava/lang/invoke/CallSite;",
					false), toMethodType(toFunctionApplyDescErased(indyReturnTy)),
					new Handle(Opcodes.H_INVOKESTATIC, module.name(), javaMethodName(impl),
							toplevelDescs.get(impl), false),
					toMethodType(toBoxedDesc(indyReturnTy)));
		}
		case CcIf(CcExp cond, CcExp thenExp, CcExp elseExp, Location _) -> {
			Label l1 = new Label();
			Label l2 = new Label();
			compile(cond);
			mv.visitJumpInsn(Opcodes.IFEQ, // = 0; false
					l1);
			compile(thenExp);
			mv.visitJumpInsn(Opcodes.GOTO, l2);
			mv.visitLabel(l1);
			compile(elseExp);
			mv.visitLabel(l2);
		}
		case CcLet(Id varName, CcExp boundExp, CcExp body, Location _) -> {
			compile(boundExp);
			locals.add(varName);
			storeLocal(locals.size() - 1, types.get(varName));
			compile(body);
		}
		case CcCase(CcExp target, List<CcCaseBranch> branches, Location _) -> {
			// TODO マッチしないときの例外処理
			// TODO tableswitchに置き換え（以下のようにしてできるはず）
			// invokedynamic #0:typeSwitch, 0 2つ目の引数は型リストの前半を無視するとき使う
			// tableswitch { // -1 to 0
			// 0: l1
			// 1: l2
			// 2: l3
			// default: lerr
			// }
			// BootstrapMethods:
			// 0: REF_invokeStatic
			// java/lang/runtime/SwitchBootstraps.typeSwitch:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;
			// Method arguments:
			// Type1
			// Type2
			// Type3

			/*
			 * case e of pat1 -> exp1 pat2 -> exp2 pat3 -> exp3
			 *
			 * を
			 *
			 * e not match pat1 goto l2 exp1 goto neck :l2 e not match pat2 goto l3 exp2 goto neck
			 * :l3 exp3 goto neck // stack framesのため :neck
			 *
			 * にする．
			 */
			Type targetTy = getType(target);
			locals.add(LOCAL_DUMMY_ID);
			int targetLocalIdx = locals.size() - 1;
			compile(target);
			storeLocal(targetLocalIdx, targetTy);
			Label neck = new Label();

			// マッチしないパターンに遭遇したら次の選択肢に進む
			IdList localsBeforeBranch = new IdList(locals);
			for (int branchIdx = 0; branchIdx < branches.size(); branchIdx++) {
				Label nextBranchLabel = (branchIdx < branches.size() - 1) ? new Label() : null;
				CcCaseBranch branch = branches.get(branchIdx);

				checkMatchAndStoreLocals(targetLocalIdx, branch.pattern(), nextBranchLabel);
				compile(branch.body());
				mv.visitJumpInsn(Opcodes.GOTO, neck);

				if (nextBranchLabel != null) {
					mv.visitLabel(nextBranchLabel);
				}
				locals = localsBeforeBranch;
			}
			mv.visitLabel(neck);
		}
		}
	}
	/**
	 * パターンのマッチをチェックする．
	 * マッチした場合，値をローカル変数に格納する．
	 * マッチしなかった場合，次のLabelにjumpする．
	 * 次のLabelがnullであるとき，チェックは省略される．
	 * @param targetIdx チェックする値の局所変数番号
	 * @param pat
	 * @param next 次のラベル
	 */
	private void checkMatchAndStoreLocals(int targetIdx, IcPattern pat, Label next) {
		switch(pat) {
		case IcPattern.Var(Id id, Location _) -> {
			locals.set(targetIdx, id);
		}
		case IcPattern.Ctor(IcExp.IcVarCtor ctor, List<IcPattern.Arg> args, Location _) -> {
			Type targetTy = ctor.type();
			String subClassName = classNames.get(ctor.id());
			if(next != null) {
				loadLocal(targetIdx, targetTy);
				mv.visitTypeInsn(Opcodes.INSTANCEOF, subClassName);
				mv.visitJumpInsn(Opcodes.IFEQ, next);
			}
			if(args.size() == 0) {
				return;
			}
			loadLocal(targetIdx, targetTy);
			mv.visitTypeInsn(Opcodes.CHECKCAST, subClassName);
			for(int i = 1; i < args.size(); i++) {
				mv.visitInsn(Opcodes.DUP);
			}
			int argBase = locals.size();
			for(int i = 0; i < args.size(); i++) {
				Type fieldTy = args.get(i).type();
				mv.visitFieldInsn(
						Opcodes.GETFIELD,
						subClassName,
						"val"+i,
						toDesc(fieldTy));
				locals.add(LOCAL_DUMMY_ID);
				storeLocal(locals.size()-1, fieldTy);
			}
			for(int i = 0; i < args.size(); i++) {
				checkMatchAndStoreLocals(argBase+i, args.get(i).pattern(), next);
			}
		}
		};
	}

	private void switchByDecl(Id id,
			Consumer<String> forTopLevelDescriptor,
			Consumer<Builtin> forBuiltin,
			Consumer<Integer> forLocalIdx,
			Consumer<CcCtor> forCtor) {

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

		CcCtor ctor = ctors.getOrNull(id);
		if(ctor != null) {
			forCtor.accept(ctor);
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
				genUnboxing(argTys.get(i).asAtom());
			}

			// call original
			mv.visitMethodInsn(
					Opcodes.INVOKESTATIC,
					module.name(),
					javaMethodName(original),
					toplevelDescs.get(original),
					false);

			// boxing
			genBoxing(retTy.asAtom());

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
		switch(cnst) {
		case ConstValue.Bool(boolean value) -> {
			if(value) {
				mv.visitInsn(Opcodes.ICONST_1);
			} else {
				mv.visitInsn(Opcodes.ICONST_0);
			}
		}
		case ConstValue.I32(int value) -> {
			switch(value) {
			case 0 -> mv.visitInsn(Opcodes.ICONST_0);
			case 1 -> mv.visitInsn(Opcodes.ICONST_1);
			case 2 -> mv.visitInsn(Opcodes.ICONST_2);
			case 3 -> mv.visitInsn(Opcodes.ICONST_3);
			case 4 -> mv.visitInsn(Opcodes.ICONST_4);
			case 5 -> mv.visitInsn(Opcodes.ICONST_5);
			default -> 	mv.visitLdcInsn(value);
			}
		}
		}
	}

	private void loadLocal(int idx, Type ty) {
		switch(ty) {
		case Type.Atom _ -> {
			if(ty == Type.I32 || ty == Type.BOOL) {
				mv.visitVarInsn(Opcodes.ILOAD, idx);
			} else if (ty == Type.UNIT) {
				throw new Error("cannot load Unit type variable");
			} else {
				mv.visitVarInsn(Opcodes.ALOAD, idx);
			}
		}
		case Type.Arrow _ ->
			mv.visitVarInsn(Opcodes.ALOAD, idx);
		case Type.Var _ ->
			throw new Error("typevar");
		}
	}

	private void storeLocal(int idx, Type ty) {
		switch(ty) {
		case Type.Atom _ -> {
			if(ty == Type.I32 || ty == Type.BOOL) {
				mv.visitVarInsn(Opcodes.ISTORE, idx);
			} else if (ty == Type.UNIT) {
				throw new Error("cannot store Unit type variable");
			} else {
				mv.visitVarInsn(Opcodes.ASTORE, idx);
			}
		}
		case Type.Arrow _ ->
			mv.visitVarInsn(Opcodes.ASTORE, idx);
		case Type.Var _ ->
			throw new Error("typevar");
		}
	}

	private void genBoxing(Type.Atom type) {
		Boxing boxing = Boxing.of(type);
		mv.visitMethodInsn(
				Opcodes.INVOKESTATIC,
				boxing.boxedClassName,
				"valueOf",
				boxing.boxMethodDesc,
				false);
	}

	private void genUnboxing(Type.Atom type) {
		Boxing boxing = Boxing.of(type);
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				boxing.boxedClassName,
				boxing.unboxMethodName,
				boxing.unboxMethodDesc,
				false);
	}

	private void genReturn(Type type) {
		switch(type) {
		case Type.Atom _ -> {
			if(type == Type.BOOL || type == Type.I32) {
				mv.visitInsn(Opcodes.IRETURN);
			} else if(type == Type.UNIT) {
				mv.visitInsn(Opcodes.RETURN);
			} else {
				mv.visitInsn(Opcodes.ARETURN);
			}
		}
		case Type.Arrow _ ->
			mv.visitInsn(Opcodes.ARETURN);
		case Type.Var _ ->
			throw new Error("typevar");
		}
	}

	private void invokeApplyWithBoxing(Type.Arrow ty) {
		Type argTy = ty.arg();
		if(!argTy.isArrow()) {
			Boxing boxing = Boxing.of(argTy.asAtom());
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
			Boxing boxing = Boxing.of(retTy.asAtom());
			mv.visitTypeInsn(Opcodes.CHECKCAST, boxing.boxedClassName);

			mv.visitMethodInsn(
					Opcodes.INVOKEVIRTUAL,
					boxing.boxedClassName,
					boxing.unboxMethodName,
					boxing.unboxMethodDesc,
					false);
		};
	}

	private static String toFunctionApplyDescErased(Type.Arrow ty) {
		return toDesc(ty.arg(), ty.ret(), _ -> objectDesc);
	}

	/**
	 * （カリー化された）関数オブジェクトに適用する際のディスクリプションを返す．
	 * @param ty
	 * @return ディスクリプション
	 */
	private static String toBoxedDesc(Type.Arrow ty) {
		return toDesc(ty.arg(), ty.ret(), ty_ -> ty_.isArrow() ? functionDesc : Boxing.of(ty_.asAtom()).boxedClassDesc);
	}

	private static String getDescription(CcFunDecl decl, IdMap<Type> types) {

		List<Type> argTys = decl.args().stream()
				.map(pat -> types.get(pat.headId()))
				.toList();
		Type retTy = types.get(decl.id()).apply(argTys.size());
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
		argTys.forEach(ty -> sb.append(toBinary(ty.asAtom())));
		sb.append(")");
		if(retTy.isArrow()) {
			sb.append(toFunctionClassDesc(retTy.asArrow()));
		} else {
			sb.append(toBinary(retTy.asAtom()));
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

	private static String toBinary(Type.Atom ty) {
		if(ty == Type.BOOL) { return "Z";}
		if(ty == Type.I32)  { return "I";}
		if(ty == Type.UNIT) { return "V";}
		return "L"+toClassName(ty.ctor())+";";
	}

	private static String toClassName(Id id) {
		Id parent = id.parent();
		if(parent == null) {
			return id.simpleName();
		}
		if(Character.isUpperCase(parent.simpleName().charAt(0))) {
			return toClassName(parent)+"$"+id.simpleName();
		}
		return id.canonicalName().replace(".", "/");
	}

	private static String toBoxed(Type ty) {
		return switch(ty) {
		case Type.Atom atom ->
			Boxing.of(atom).boxedClassDesc;
		case Type.Arrow _ ->
			functionDesc;
		case Type.Var _ -> { throw new Error("typevar"); }
		};
	}

	private static String toBoxedSignature(Type ty) {
		return switch(ty) {
		case Type.Atom atom ->
			Boxing.of(atom).boxedClassDesc;
		case Type.Arrow(Type arg, Type ret) ->
			"Ljava/util/function/Function<"+toBoxedSignature(arg)+toBoxedSignature(ret)+">;";
		case Type.Var _ -> { throw new Error("typevar"); }
		};
	}

	private static String toFunctionClassDesc(Type.Arrow ty) {
		return "L"+toFunctionClassName(ty)+";";
	}

	private static String toFunctionClassName(Type.Arrow fun) {
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
		return switch (exp) {
        case CcCnst(ConstValue value, Location _) -> value.type();
        case CcVar(Id id, Location _) -> types.get(id);
        case CcApp call -> returnType(call);
        case CcMkCls(Id id, IdList _, Location _) -> types.get(id);
        case CcIf(CcExp _, CcExp thenExp, CcExp _, Location _) -> getType(thenExp);
        case CcLet(Id _, CcExp _, CcExp body, Location _) -> getType(body);
        case CcCase(CcExp _, List<CcCaseBranch> branches, Location _) -> {
        	yield getType(branches.get(0).body());
        }
		};
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

	public static Boxing of(Type.Atom ty) {
		if(ty == Type.BOOL) { return BOOL;}
		if(ty == Type.I32)  { return INT;}
		throw new IllegalArgumentException("Unexpected value: " + ty);
	}
}
