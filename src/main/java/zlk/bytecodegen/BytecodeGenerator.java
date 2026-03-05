package zlk.bytecodegen;

import static zlk.util.ErrorUtils.neverHappen;
import static zlk.util.ErrorUtils.todo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import zlk.common.Location;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.core.Builtin;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcPattern;
import zlk.util.Stack;

/**
 * クロージャ解決後のトップレベルにフラットになったASTをJVMのバイトコードに変換する
 */

public final class BytecodeGenerator {

	private final CcModule module;
	private final String origin;
	private final IdMap<String> classNames;
	private final IdMap<Type> types;
	private final IdMap<Builtin> builtins;
	private final IdMap<String> toplevelDescs;
	private final IdMap<CcFunDecl> toplevelDecls;
	private final IdMap<CcCtor> ctors;
	private final Map<String, String> unionSuperClassNames;
	private ClassWriter cw;

	// for compileDecl
	private IdList locals;
	private static final Id LOCAL_DUMMY_ID = Id.fromCanonicalName("..DUMMY..");
	private MethodVisitor mv;
	private Stack<Runnable> pendings;

	public BytecodeGenerator(CcModule module, IdMap<Type> types, List<Builtin> builtins, String origin) {
		this.module = module;
		this.classNames = new IdMap<>();
		this.types = types;
		this.builtins = builtins.stream().collect(IdMap.collector(b -> b.id(), b -> b));
		this.origin = origin;
		this.toplevelDescs = new IdMap<>();
		this.toplevelDecls = new IdMap<>();
		this.ctors = new IdMap<>();
		this.unionSuperClassNames = new HashMap<>();
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
			String unionSuperName = unionSuperName(union);
			classNames.put(union.id(), unionSuperName);
			union.ctors().forEach(ctor -> {
				String unionSubName = unionSubName(union, ctor);
				classNames.put(ctor.id(), unionSubName);
				ctors.put(ctor.id(), ctor);
				unionSuperClassNames.put(unionSubName, unionSuperName);
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

	/**
	 * カスタムクラスのバイトコードを生成する．
	 *
	 * カスタムクラスは（今のところ）共通のinterfaceと各バリアントのfinal classで実装される．
	 * 命名はmoduleName$interfaceName$variantName
	 * nestのhostは（暫定的に）記述されたmoduleとなる
	 * @param union
	 * @param fileWriter
	 */
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
		cw.visitSource(origin, null);
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
		cw.visitSource(origin, null);

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

	private void genMainClass(BiConsumer<String, byte[]> fileWriter) {
		cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
			/*
			 * フロー合流箇所のframeの計算でCustomTypeNameを求める．
			 * CustomTypeのVariant以外の型はここに持ち込まなれない想定．
			 * FUTURE: Record型もここで合流するか？
			 */
			@Override
			protected String getCommonSuperClass(String type1, String type2) {
				String type1Super = unionSuperClassNames.get(type1);
				String type2Super = unionSuperClassNames.get(type2);
				if(type1Super != null && type1Super.equals(type2Super)) {
					return type1Super;
				}
				throw new IllegalArgumentException(
						"super type not match: arg1="+ type1 + ", arg2=" + type2);
			}
		};

		cw.visit(
				Opcodes.V16,
				Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_SUPER,
				module.name(),
				null,
				"java/lang/Object",
				null);

		cw.visitSource(origin, null);

		genConstructor();

		module.types().forEach(union -> {
			cw.visitNestMember(classNames.get(union.id()));
			union.ctors().forEach(ctor -> {
				cw.visitNestMember(classNames.get(ctor.id()));
			});
		});

		module.funcs().forEach(decl -> compileDecl(decl));

		cw.visitEnd();

		fileWriter.accept(origin.substring(0, origin.indexOf('.')), cw.toByteArray());
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
			genReturn(types.get(decl.id()).dropArgs(decl.arity()));
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
			if(args.get(i) instanceof IcPattern.Dector ctor) {
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
		case IcPattern.Dector(IcExp.IcVarCtor ctor, List<IcPattern.Arg> args, Location _) -> {
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

	/**
	 * 式に対応するバイトコードを生成する．
	 *
	 * Type.Arrowを持つ「値」は基本的に java.util.function.Function で扱い，
	 * トップレベル関数を直接invokeできる場合はそのように「最適化する」．
	 * @param exp
	 */
	private void compile(CcExp exp) {
		switch (exp) {
		case CcCnst(ConstValue value, Location _) -> {
			loadCnst(value);
		}
		case CcVar(Id id, Location _) -> {  // 関数呼び出しの関数でないところの変数
			getDecl(id, descriptor -> {
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
							"()" + FUNCTION_DESC, false);
				} else {
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, module.name(), javaMethodName(id),
							descriptor, false);
				}
			}, builtin -> {
				if(builtin.type() instanceof Type.CtorApp _) {  // Boolの定数などは出せる
					builtin.accept(mv);
				} else {
					todo("make method and currying");
				}
			}, localIdx -> {
				loadLocal(localIdx, types.get(id));
			}, ctor -> {
				if (ctor.args().size() == 0) {
					String subclassName = classNames.get(ctor.id());
					mv.visitTypeInsn(Opcodes.NEW, subclassName);
					mv.visitInsn(Opcodes.DUP);
					mv.visitMethodInsn(Opcodes.INVOKESPECIAL, subclassName, "<init>", "()V", false);
				} else {
					ensureCtorOriginal(ctor);
					List<Type> ty = types.get(ctor.id()).flatten();  // TODO: 通常の関数とカリー化のコードを統一
					Id nextId = null;
					for (int i = ty.size() - 1; i >= 0; i--) {
						List<Type> args = ty.subList(0, i);
						List<Type> ret = ty.subList(i, ty.size());

						Id lambdaId = Id.fromParentAndSimpleName(Id.fromParentAndSimpleName(id, ""),
								i + "");

						if (args.size() == ty.size() - 1) {
							genBoxedMethod(lambdaId, args, id, ret.get(0));
						} else {
							genCurryingStep(lambdaId, args, nextId, Type.arrow(ret));
						}
						nextId = lambdaId;
					}
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, module.name(), javaMethodName(nextId),
							"()" + FUNCTION_DESC, false);
				}

			});
		}
		case CcApp(CcExp fun, List<CcExp> args, Location loc) -> {
			Type funTy = getType(fun);
			if (fun instanceof CcVar var) {
				Id funId = var.id();
				List<Type> declFlat = types.get(funId).flatten();  // 宣言時の型（多相を含む）

				getDecl(funId, descriptor -> {
					int methodArity = toplevelDecls.get(funId).arity();
					if (args.size() >= methodArity) {  // invoke staticが使える

						// 全ての引数をstackに載せる
						Type actualRetTy = funTy;
						for (int i = 0; i < methodArity; i++) {
							CcExp arg = args.get(i);
							compile(arg);
							Type actualArgTy = getType(arg);
							boxGenericArgIfNeeded(declFlat.get(i), actualArgTy);
							actualRetTy = actualRetTy.apply(actualArgTy);
						}

						mv.visitMethodInsn(
								Opcodes.INVOKESTATIC,
								module.name(),
								javaMethodName(funId),
								descriptor,
								false);

						// 更に適用が必要なとき（戻り値はFunctionのはず）はapplyをチェーン
						for (int i = methodArity; i < args.size(); i++) {
							CcExp arg = args.get(i);
							compile(arg);
							Type actualArgTy = getType(arg);
							genInvokeApplyCurriedFunctionWithBoxing(funTy.dropArgs(i).asArrow(), actualArgTy);
							actualRetTy = actualRetTy.apply(actualArgTy);
						}
						if (methodArity < args.size()) {
							Primitive.tryFrom(actualRetTy).ifPresent(p -> {
								p.genCheckCast(mv);
								p.genUnboxing(mv);
							});
						}

						unboxGenericRetIfNeeded(declFlat.get(args.size()), actualRetTy);

					} else {  // 一旦Functionにしてapplyのチェーンで部分適用を実現
						// TODO: indyで最適化する余地がある
						compile(var);  // Functionオブジェクトをstackに載せる
						for (int i = 0; i < args.size(); i++) {
							compile(args.get(i));
							invokeApplyWithBoxing(funTy.dropArgs(i).asArrow());
						}
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
					compile(var);  // CcVarの分岐に委ねる
					for (int i = 0; i < args.size(); i++) {
						compile(args.get(i));
						invokeApplyWithBoxing(funTy.dropArgs(i).asArrow());
					}
				}, ctor -> {
					int ctorArity = ctor.args().size();
					if (args.size() == ctorArity) {  // <init>を呼べる
						String subclassName = classNames.get(ctor.id());
						mv.visitTypeInsn(Opcodes.NEW, subclassName);
						mv.visitInsn(Opcodes.DUP);  // 値を返さないのでポインタを複製しておく
						// 全ての引数をstackに載せる
						for (int i = 0; i < ctorArity; i++) {
							compile(args.get(i));
							boxGenericArgIfNeeded(
									declFlat.get(i),
									getType(args.get(i)));
						}
						mv.visitMethodInsn(
								Opcodes.INVOKESPECIAL,
								subclassName,
								"<init>",
								toMethodDesc(ctor.args(), Type.UNIT),
								false);
					} else {  // 一旦Functionにしてapplyのチェーンで部分適用を実現
						// TODO: indyで最適化する余地がある
						compile(var);  // Functionオブジェクトをstackに載せる
						Type stepTy = funTy;
						for (CcExp arg : args) {
							compile(arg);
							stepTy = genInvokeApplyCurriedFunctionWithBoxing((Type.Arrow)stepTy, getType(arg));
						}
					}
				});
			} else {
				Type actualRetTy = getType(fun);
				compile(fun);

				// function object remains on stack top
				for (int i = 0; i < args.size(); i++) {
					CcExp arg = args.get(i);
					compile(arg);
					Type actualArgTy = getType(arg);
					invokeApplyWithBoxing(funTy.dropArgs(i).asArrow());
					actualRetTy = actualRetTy.apply(actualArgTy);
				}
				unboxGenericRetIfNeeded(funTy.dropArgs(args.size()), actualRetTy);
			}
		}
		case CcMkCls(Id clsFunc, IdList caps, Location _) -> {
			Id impl = clsFunc;
			List<Type> indyArgTys = caps.stream().map(types::get).toList();
			Type.Arrow indyReturnTy = types.get(impl).dropArgs(indyArgTys.size()).asArrow();

			if (indyReturnTy == null) {
				throw new Error(impl.toString());
			}

			caps.forEach(cap -> {
				loadLocal(locals.indexOf(cap), types.get(cap));
			});

			mv.visitInvokeDynamicInsn("apply", toMethodDesc(indyArgTys, indyReturnTy), new Handle(
					Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
					"(Ljava/lang/invoke/MethodHandles$Lookup;" + "Ljava/lang/String;"
							+ "Ljava/lang/invoke/MethodType;" + "Ljava/lang/invoke/MethodType;"
							+ "Ljava/lang/invoke/MethodHandle;" + "Ljava/lang/invoke/MethodType;"
							+ ")Ljava/lang/invoke/CallSite;",
					false), toMethodType(toTypeErasedMethodDesc(indyReturnTy.arg(), indyReturnTy.ret())),
					new Handle(Opcodes.H_INVOKESTATIC, module.name(), javaMethodName(impl),
							toplevelDescs.get(impl), false),
					toMethodType(toBoxedMethodDesc(indyReturnTy.arg(), indyReturnTy.ret())));
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
			compile(target);
			Label neck = new Label();

			// マッチしないパターンに遭遇したら次の選択肢に進む
			IdList localsBeforeBranch = new IdList(locals);
			for (int branchIdx = 0; branchIdx < branches.size(); branchIdx++) {
				Label nextBranchLabel = (branchIdx < branches.size() - 1) ? new Label() : null;
				CcCaseBranch branch = branches.get(branchIdx);
				checkMatchAndStoreLocals(branch.pattern(), null, nextBranchLabel); // TODO: Dectorの分解にはdeclTyは要らないのでメソッドを分ける
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
	 * 宣言時の引数型とインスタンスの引数型に合せて必要ならboxingを行う
	 * @param declTy 宣言時の引数型
	 * @param instTy インスタンスの引数型
	 */
	private void boxGenericArgIfNeeded(Type declTy, Type instTy) {
		if(declTy instanceof Type.Var && instTy instanceof Type.CtorApp) {
			Primitive.tryFrom(instTy).ifPresent(p -> {
				mv.visitMethodInsn(
						Opcodes.INVOKESTATIC,
						p.boxedClassName,
						"valueOf",
						p.boxMethodDesc,
						false
				);
			});
		}
	}
	/**
	 * 宣言時の戻り値型とインスタンスの戻り値に合せて必要ならunboxingを行う
	 * @param declTy 宣言時の戻り値型
	 * @param instTy インスタンスの戻り値型
	 */
	private void unboxGenericRetIfNeeded(Type declTy, Type instTy) {
		if(declTy instanceof Type.Var && instTy instanceof Type.CtorApp) {
			Primitive.tryFrom(instTy).ifPresent(p -> {
				mv.visitTypeInsn(Opcodes.CHECKCAST, p.boxedClassName);
				mv.visitMethodInsn(
						Opcodes.INVOKEVIRTUAL,
						p.boxedClassName,
						p.unboxMethodName,
						p.unboxMethodDesc,
						false
				);
			});
		}
	}

	/**
	 * スタックトップとパターンのマッチをチェックする．
	 * マッチした場合，値をローカル変数に格納する．
	 * マッチしなかった場合，次のLabelにjumpする．
	 * 次のLabelがnullであるとき，チェックは省略される．
	 * @param pat
	 * @param next 次のラベル
	 * @param isVarInCtorDecl
	 */
	private void checkMatchAndStoreLocals(IcPattern pat, Type declTy, Label next) {
		switch(pat) {
		case IcPattern.Var(Id id, Location _) -> {
			Type expTy = getType(new CcVar(id, Location.noLocation()));  // TODO: 利用箇所が無かったら推論されてなくね？
			if(declTy instanceof Type.Var) {
				// 必要ならキャストする
				if(expTy instanceof Type.CtorApp ctorapp) {
					Primitive.tryFrom(expTy).ifPresentOrElse(p -> {
						p.genCheckCast(mv);
						p.genUnboxing(mv);
					}, () -> {
						mv.visitTypeInsn(Opcodes.CHECKCAST, classNames.get(ctorapp.ctor()));
					});
				}
				storeLocal(locals.size(), expTy);
			} else {
				storeLocal(locals.size(), declTy);
			}
			locals.add(id);
		}
		case IcPattern.Dector(IcExp.IcVarCtor ctor, List<IcPattern.Arg> args, Location _) -> {
			CcCtor ctorDecl = ctors.get(ctor.id());
			String subClassName = classNames.get(ctor.id());
			if(next != null) {
				mv.visitInsn(Opcodes.DUP);
				mv.visitTypeInsn(Opcodes.INSTANCEOF, subClassName);  // サブクラスのインスタンスなら1
				mv.visitJumpInsn(Opcodes.IFEQ, next);  // 0なら分岐
			}
			if(args.isEmpty()) {
				mv.visitInsn(Opcodes.POP);  // fieldを引き出すためのオブジェクトを捨てる
				return;
			}
			mv.visitTypeInsn(Opcodes.CHECKCAST, subClassName);
			for(int i = 0; i < args.size(); i++) {
				if(i < args.size()-1) {
					mv.visitInsn(Opcodes.DUP);
				}
				mv.visitFieldInsn(
						Opcodes.GETFIELD,
						subClassName,
						"val"+i,
						toDesc(ctorDecl.args().get(i)));
				checkMatchAndStoreLocals(args.get(i).pattern(), ctorDecl.args().get(i), next);
			}
		}
		};
	}

	/**
	 * コンストラクタと同名の，戻り値のあるメソッドをなければ作る
	 * @param ctor
	 */
	private void ensureCtorOriginal(CcCtor ctor) {  // TODO: はじめに用意してもいいかも
		Id id = ctor.id();
		if (toplevelDescs.containsKey(id)) {
			return;
		}

		// ディスクリプタを登録
		String subclassName = classNames.get(id);
		String argsDesc = ctor.args()
				.stream()
				.map(arg -> toDesc(arg))
				.collect(Collectors.joining(""));
		String retDesc = "L"+subclassName+";";
		String desc = "("+argsDesc+")"+retDesc;
		toplevelDescs.put(id, desc);

		pendings.push(() -> {
			mv = cw.visitMethod(
					Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC + Opcodes.ACC_SYNTHETIC,
					javaMethodName(id),
					desc,
					null,
					null);
			mv.visitCode();

			mv.visitTypeInsn(Opcodes.NEW, subclassName);
			mv.visitInsn(Opcodes.DUP);

			List<Type> argTys = ctor.args();
			for (int i = 0; i < argTys.size(); i++) {
				loadLocal(i, argTys.get(i));
			}

			mv.visitMethodInsn(
					Opcodes.INVOKESPECIAL,
					subclassName,
					"<init>",
					toMethodDesc(argTys, Type.UNIT),
					false);

			mv.visitInsn(Opcodes.ARETURN);

			mv.visitMaxs(-1, -1);
			mv.visitEnd();
		});
	}

	private void getDecl(Id id,
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

		System.out.println("locals: "+locals);
		neverHappen("id must be found:"+id);
	}

	// TODO 名前に「計画に追加する」ニュアンスを
	// TODO retTyからSignatureTypeを書く
	private void genBoxedMethod(Id name, List<Type> argTys, Id original, Type retTy) {
		String desc = toBoxedMethodDesc(argTys, retTy);
		toplevelDescs.put(name, desc);
		pendings.push(() -> {
			mv = cw.visitMethod(
					Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC + Opcodes.ACC_SYNTHETIC,
					javaMethodName(name),
					desc,
					null,
					null);
			mv.visitCode();

			// 引数をロードして可能なものをunboxing
			for (int i = 0; i < argTys.size(); i++) {
				mv.visitVarInsn(Opcodes.ALOAD, i);
				Primitive.tryFrom(argTys.get(i)).ifPresent(p -> p.genUnboxing(mv));
			}

			// 元のメソッド呼び出し
			mv.visitMethodInsn(
					Opcodes.INVOKESTATIC,
					module.name(),
					javaMethodName(original),
					toplevelDescs.get(original),
					false);

			// 必要なら戻り値をboxing
			Primitive.tryFrom(retTy).ifPresent(p -> p.genBoxing(mv));

			// return
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(-1, -1); // compute all frames and local automatically
			mv.visitEnd();
		});
	}

	private void genCurryingStep(Id name, List<Type> argTys, Id target, Type.Arrow retTy) {
		String desc = toBoxedMethodDesc(argTys, retTy);
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
					toMethodType("("+OBJECT_DESC+")"+OBJECT_DESC),
					new Handle(
							Opcodes.H_INVOKESTATIC,
							module.name(),
							javaMethodName(target),
							toplevelDescs.get(target),
							false),
					toMethodType(toBoxedMethodDesc(retTy.arg(), retTy.ret())));

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
	private void loadLocal(CcVar var) {
		int localIdx = locals.indexOf(var);
		if(localIdx < 0) {  // 残りは局所変数のはず
			neverHappen("missing local var", var.loc());
		}
		loadLocal(localIdx, types.get(var.id()));
	}
	private void loadLocal(int idx, Type ty) {
		switch(ty) {
		case Type.CtorApp _ -> {
			if(ty.equals(Type.I32) || ty.equals(Type.BOOL)) {
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
			mv.visitVarInsn(Opcodes.ALOAD, idx);  // 型変数は参照型のはず
		}
	}

	private void storeLocal(int idx, Type ty) {
		switch(ty) {
		case Type.CtorApp _ -> {
			if(ty.equals(Type.I32) || ty.equals(Type.BOOL)) {
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
			mv.visitVarInsn(Opcodes.ASTORE, idx);  // 型変数は参照型のはず
		}
	}

	private void genReturn(Type type) {
		switch(type) {
		case Type.CtorApp _ -> {
			if(type.equals(Type.BOOL) || type.equals(Type.I32)) {
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
			mv.visitInsn(Opcodes.ARETURN);
		}
	}

	private void invokeApplyWithBoxing(Type.Arrow ty) {
		Type argTy = ty.arg();
		Primitive.tryFrom(argTy).ifPresent(p -> p.genBoxing(mv));

		// カリー化されている前提
		mv.visitMethodInsn(
				Opcodes.INVOKEINTERFACE,
				FUNCTION_CLASS_NAME,
				"apply",
				"(Ljava/lang/Object;)Ljava/lang/Object;",
				true);

		Type retTy = ty.ret();
		if(retTy.isArrow()) {
			mv.visitTypeInsn(Opcodes.CHECKCAST, FUNCTION_CLASS_NAME);
		} else {
			Primitive.tryFrom(retTy).ifPresent(p -> {
				p.genCheckCast(mv);
				p.genUnboxing(mv);
			});
		};
	}

	/**
	 * 関数と引数の型を指定し，関数をカリー化したFunctionのapplyをinvokeするコードを生成する．
	 * 関数の型は定義時のもの，引数の型は適用時に与えるものを指定する．
	 * 引数にBoxingが必要な場合それも行う．
	 * @param funTy 関数の型（定義の型）
	 * @param argTy 引数の型（適用時に与える型）
	 * @return apply後の関数の型
	 */
	private Type genInvokeApplyCurriedFunctionWithBoxing(Type.Arrow funTy, Type argTy) {
		Primitive.tryFrom(argTy).ifPresent(p -> p.genBoxing(mv));
		mv.visitMethodInsn(
				Opcodes.INVOKEINTERFACE,
				FUNCTION_CLASS_NAME,
				"apply",
				"(Ljava/lang/Object;)Ljava/lang/Object;",
				true);
		return funTy.ret();
	}

	private String getDescription(CcFunDecl decl, IdMap<Type> types) {

		List<Type> argTys = decl.args().stream()
				.map(pat -> types.get(pat.headId()))
				.toList();
		Type retTy = types.get(decl.id()).dropArgs(argTys.size());
		return toMethodDesc(argTys, retTy);
	}

	/**
	 * 指定した型のディスクリプタ表現を返す
	 * @param type
	 * @return
	 */
	private String toDesc(Type type) {
		return switch(type) {
		case Type.CtorApp atom -> {
			if(type.equals(Type.BOOL)) { yield "Z";}
			if(type.equals(Type.I32))  { yield "I";}
			if(type.equals(Type.UNIT))  { yield "V";}  // コンストラクタ用
			yield "L"+classNames.get(atom.ctor())+";";
		}
		case Type.Arrow _ -> FUNCTION_DESC;
		case Type.Var _ -> OBJECT_DESC;
		};
	}

	private static String toMethodDesc(List<Type> argTys, Type retTy, Function<Type, String> mapper) {
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		argTys.forEach(ty -> sb.append(mapper.apply(ty)));
		sb.append(")");
		sb.append(mapper.apply(retTy));
		return sb.toString();
	}
	private String toMethodDesc(List<Type> argTys, Type retTy) {
		return toMethodDesc(argTys, retTy, this::toDesc);
	}
	private String toBoxedMethodDesc(List<Type> argTys, Type retTy) {
		return toMethodDesc(argTys, retTy, this::toBoxed);
	}
	private String toBoxedMethodDesc(Type argTy, Type retTy) {
		return toMethodDesc(List.of(argTy), retTy, this::toBoxed);
	}
	private String toTypeErasedMethodDesc(Type argTy, Type retTy) {
		return toMethodDesc(List.of(argTy), retTy, _ -> OBJECT_DESC);
	}

	private String toBoxedSignature(List<Type> argTys, Type retTy) {
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		argTys.forEach(ty -> sb.append(toBoxedSignature(ty)));
		sb.append(")");
		sb.append(toBoxedSignature(retTy));
		return sb.toString();
	}

	private static final String FUNCTION_CLASS_NAME = "java/util/function/Function";
	private static final String OBJECT_DESC = "Ljava/lang/Object;";
	private static final String FUNCTION_DESC = "L" + FUNCTION_CLASS_NAME + ";";

	private String toBoxed(Type ty) {
		if (ty.equals(Type.I32) || ty.equals(Type.BOOL)) {
			return Primitive.of((Type.CtorApp)ty).boxedClassDesc;
		}
		return toDesc(ty);
	}

	private String toBoxedSignature(Type ty) {
		return switch(ty) {
		case Type.CtorApp atom -> toBoxed(atom);
		case Type.Arrow(Type arg, Type ret) ->
			"Ljava/util/function/Function<"+toBoxedSignature(arg)+toBoxedSignature(ret)+">;";
		case Type.Var _ -> OBJECT_DESC;  // TODO: できるだけ正確な型を
		};
	}

	private static org.objectweb.asm.Type toMethodType(String descriptor) {
		return org.objectweb.asm.Type.getMethodType(descriptor);
	}

	// TODO: expIdで型を引く．switchじゃなくていいかも．
	private Type getType(CcExp exp) {
		return switch (exp) {
		case CcCnst(ConstValue value, Location _) -> value.type();
		case CcVar(Id id, Location _) -> types.get(id);
		case CcApp(CcExp fun, List<CcExp> args, Location _) -> {
			Type result = getType(fun);
			for(CcExp arg : args) {
				result = result.apply(getType(arg));
			}
			yield result;
		}
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
