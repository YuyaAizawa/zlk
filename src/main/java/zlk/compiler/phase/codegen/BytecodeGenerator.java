package zlk.compiler.phase.codegen;

import static zlk.util.ErrorUtils.neverHappen;

import java.util.Comparator;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import zlk.compiler.CompilationOptions;
import zlk.compiler.CompilationOptions.Key;
import zlk.compiler.builtin.Builtin;
import zlk.compiler.diagnostic.Diagnostic;
import zlk.compiler.diagnostic.DiagnosticReporter;
import zlk.compiler.id.Id;
import zlk.compiler.id.IdMap;
import zlk.compiler.ir.ConstValue;
import zlk.compiler.ir.reuse.LocalMap;
import zlk.compiler.ir.reuse.LocalVar;
import zlk.compiler.ir.reuse.own.OwnBlock;
import zlk.compiler.ir.reuse.own.OwnBranch;
import zlk.compiler.ir.reuse.own.OwnFunDecl;
import zlk.compiler.ir.reuse.own.OwnPattern;
import zlk.compiler.ir.reuse.own.OwnRhs;
import zlk.compiler.ir.reuse.own.OwnStmt;
import zlk.compiler.ir.reuse.own.OwnUse;
import zlk.compiler.ir.reuse.own.OwnRhs.OwnRecordField;
import zlk.compiler.ir.reuse.plan.ReusePlan;
import zlk.compiler.ir.typing.Ctor;
import zlk.compiler.ir.typing.Type;
import zlk.compiler.jvm.Primitive;
import zlk.compiler.source.Location;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;
import zlk.util.collection.Stack;

/**
 * クロージャ解決後のトップレベルにフラットになったASTをJVMのバイトコードに変換する
 */

public final class BytecodeGenerator {

	public static final int OPCODE_VERSION = Opcodes.V23;

	private final ReusePlan module;
	private final String origin;
	private final IdMap<Type> types;
	private final IdMap<Builtin> builtins;
	private final IdMap<String> toplevelDescs;
	private final IdMap<OwnFunDecl> toplevelDecls;
	private final IdMap<JavaType> javaClasses;
	private final IdMap<Ctor> ctors;
	private final Seq<CustomType> customTypes;
	private final DiagnosticReporter diagnosticReporter;
	private final CompilationOptions options;
	private ClassWriter cw;

	private static final Id LOCAL_DUMMY_ID = Id.intern("..DUMMY..");
	private static final Handle LAMBDA_METAFACTORY = new Handle(
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
			false);
	private static final Handle RECORD_LITERAL_BOOTSTRAP = new Handle(
			Opcodes.H_INVOKESTATIC,
			"zlk/runtime/internal/RecordOps",
			"bootstrapLiteral",
			"(Ljava/lang/invoke/MethodHandles$Lookup;"
			+ "Ljava/lang/String;"
			+ "Ljava/lang/invoke/MethodType;"
			+ "Ljava/lang/String;"
			+ ")Ljava/lang/invoke/CallSite;",
			false);

	// for compileDecl
	private Id compilingFun;
	private SeqBuffer<Id> locals;
	private LocalMap<OwnStmt.Bind> stack;  // 結果をstack上に置くBind
	private MethodVisitor mv;
	private Stack<Runnable> pendings;

	public BytecodeGenerator(ReusePlan module, IdMap<Type> types, Seq<Builtin> builtins, String origin) {
		this(module, types, builtins, origin, _ -> {}, CompilationOptions.DEFAULT);
	}

	public BytecodeGenerator(
			ReusePlan module,
			IdMap<Type> types,
			Seq<Builtin> builtins,
			String origin,
			DiagnosticReporter diagnosticReporter,
			CompilationOptions options
	) {
		this.module = module;
		this.types = types;
		this.builtins = builtins.fold(IdMap.folder(b -> b.id(), b -> b));
		this.origin = origin;
		this.toplevelDescs = new IdMap<>();
		this.toplevelDecls = new IdMap<>();
		this.javaClasses = new IdMap<>();
		this.ctors = new IdMap<>();
		this.customTypes = module.types().map(decl -> new CustomType(module.name(), decl, origin));
		this.diagnosticReporter = diagnosticReporter;
		this.options = options;
		this.pendings = new Stack<>();

		javaClasses.put(Type.UNIT.id(), JavaType.VOID);
		javaClasses.put(Type.BOOL.id(), new JavaType.Simple("java/lang/Boolean"));
		javaClasses.put(Type.I32.id(), new JavaType.Simple("java/lang/Integer"));
		customTypes.forEach(ct -> ct.putJavaClasses(javaClasses, ctors));
	}

	/**
	 * Output the compilation result to the specified BiConsumer as pairs of
	 * class name and byte sequence.
	 *
	 * @param fileWriter
	 */
	public void compile(BiConsumer<String, byte[]> fileWriter) {
		module.funcs().forEach(decl -> {
			toplevelDescs.put(decl.id(), getDescription(decl));
			toplevelDecls.put(decl.id(), decl);
		});

		customTypes.forEach(generator -> generator.compile(OPCODE_VERSION, this::toDesc, fileWriter));
		genMainClass(fileWriter);
	}

	private void genMainClass(BiConsumer<String, byte[]> fileWriter) {
		cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
			/*
			 * フロー合流箇所のframeの計算でカスタム型名を求める．
			 * カスタム型のvariant以外の型はここに持ち込まなれない想定．
			 * FUTURE: 将来ZLKへrecord型を追加する場合，ここでの型合流処理の対象とするか？
			 */
			@Override
			protected String getCommonSuperClass(String type1, String type2) {
				String type1Super = dropAfterLastDollar(type1);
				String type2Super = dropAfterLastDollar(type2);

				if(!type1Super.equals(type2Super)) {
					throw new IllegalArgumentException(
							"super type not match: arg1="+ type1 + ", arg2=" + type2);
				}
				return type1Super;
			}

			private String dropAfterLastDollar(String target) {
				int index = target.lastIndexOf('$');
				if(index != -1) {
					target = target.substring(0, index);
				}
				return target;
			}
		};

		cw.visit(
				OPCODE_VERSION,
				Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_SUPER,
				module.name(),
				null,
				"java/lang/Object",
				null);

		cw.visitSource(origin, null);

		genConstructor();

		customTypes.forEach(ct -> ct.registerNestMembers(cw));

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

	private void compileDecl(OwnFunDecl decl) { // TODO トップレベルは全て非カリー化する
		compilingFun = decl.id();
		this.locals = new SeqBuffer<>();
		this.stack = new LocalMap<>(decl.localIdSize());
		try {
			mv = cw.visitMethod(
					Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
					javaMethodName(compilingFun),
					toplevelDescs.get(compilingFun),
					null,
					null);

			mv.visitCode();
			registerArgs(decl.args());
			Type retTy = types.get(compilingFun).dropArgs(decl.arity());
			compile(decl.body(), toJavaType(retTy));
			genReturn(retTy);
			mv.visitMaxs(-1, -1); // compute all frames and local automatically
			mv.visitEnd();

			while(!pendings.isEmpty()) {
				pendings.pop().run();
			}
			if(!stack.isEmply()) {
				throw new RuntimeException("uncompiled stmt remained!");
			}
		} catch(RuntimeException e) {
			throw new RuntimeException("on method "+compilingFun, e);
		}
		compilingFun = null;
	}

	/**
	 * 関数の引数をlocalsに登録する
	 * @param args 引数
	 */
	private void registerArgs(Seq<OwnPattern> args) {
		args.forEach(arg -> {
			if(arg instanceof OwnPattern.Var var) {
				locals.add(var.id());
			} else {
				locals.add(LOCAL_DUMMY_ID);
			}
		});

		for (int i = 0; i < args.size(); i++) {
			if(args.at(i) instanceof OwnPattern.Ctor ctor) {
				Type subClassTy = types.get(ctor.ctor());
				loadLocal(i, subClassTy);
				registerArgRec(ctor);
			} else if(args.at(i) instanceof OwnPattern.Record record) {
				mv.visitVarInsn(Opcodes.ALOAD, i);
				registerArgRec(record);
			}
		}
	}
	private void registerArgRec(OwnPattern pat) {
		// 事前条件：パターンに対応する値がstackのトップに乗っている
		// 事後条件：パターンに対応する値をstackから消費
		switch(pat) {
		case OwnPattern.Wildcard(Type _, Location _) -> {
			mv.visitInsn(Opcodes.POP);  // TODO: 最適化 フィールドからとらないように
		}
		case OwnPattern.Var var -> {
			checkcastIfNeed(JavaType.OBJECT, toJavaType(var.type()));
			storeLocal(locals.size(), var.type());
			locals.add(var.id());
		}
		case OwnPattern.Ctor(Id id, Seq<OwnPattern> args, Type _, Location _) -> {
			Ctor ctorDecl = ctors.get(id);
			mv.visitTypeInsn(Opcodes.CHECKCAST, javaClasses.get(id).toClassName());
			// stackの数を調整
			if(args.size() == 0) {
				mv.visitInsn(Opcodes.POP);
			}

			for (int i = 0; i < args.size() - 1; i++) {
				mv.visitInsn(Opcodes.DUP);
			}

			Seq.zip(args, ctorDecl.args()).forEachIndexed((fieldIdx, ctorArg, declArgType) -> {
				mv.visitFieldInsn(
						Opcodes.GETFIELD,
						javaClasses.get(id).toClassName(),
						CustomType.componentName(fieldIdx),
						toDesc(declArgType));
				registerArgRec(ctorArg);
			});
		}
		case OwnPattern.Record(Seq<OwnPattern.Var> fields, Type _, Location _) -> {
			mv.visitTypeInsn(Opcodes.CHECKCAST, JavaType.RECORD.toClassName());
			for(int i = 0; i < fields.size(); i++) {
				if(i < fields.size() - 1) mv.visitInsn(Opcodes.DUP);
				OwnPattern.Var field = fields.at(i);
				mv.visitLdcInsn(field.id().simpleName());
				mv.visitMethodInsn(
						Opcodes.INVOKEINTERFACE,
						JavaType.RECORD.toClassName(),
						"get",
						"(Ljava/lang/String;)Ljava/lang/Object;",
						true);
				registerArgRec(field);
			}
		}
		}
	}

	private void compile(OwnBlock block, JavaType expectedJavaType) {
		block.stmts().forEach(this::compile);
		compile(block.result(), expectedJavaType);
	}
	/**
	 * OwnStmtに対応するバイトコードを生成する．
	 *
	 * Javaの型消去方式に対応するため，期待される戻り値型の上限境界を考慮し，
	 * 満たせない場合ダウンキャストを補う．
	 *
	 * 明示的な変数名が振られているLocalVarは局所変数として，
	 * それ以外はスタックでデータの受け渡しを行う．
	 *
	 * @param stmt
	 */
	private void compile(OwnStmt stmt) {
		if(stmt instanceof OwnStmt.Bind bind) {
			LocalVar lhs = bind.dst();
			// local変数の処理
			lhs.id().ifPresentOrElse(id -> {
				compile(bind, toJavaType(lhs.type()));
				storeLocal(id);
			}, () -> {
				stack.put(lhs, bind);
			});
		}
		// TODO: 必要になったらDup/Dropの処理
	}
	private void compile(OwnStmt.Bind bind, JavaType expectedJavaType) {
		LocalVar lhs = bind.dst();
		compile(lhs, bind.rhs(), expectedJavaType);
		if (options.isEnabled(Key.REPORT_BYTECODE_STMT_ORDER)) {
			diagnosticReporter.report(new Diagnostic.BytecodeStmt(
					bind.loc(),
					compilingFun,
					lhs.localId(),
					lhs.id()));
		}
	}
	/**
	 * OwnRhsに対応するバイトコードを生成する．
	 * @param site 演算の識別子（左辺の変数）
	 * @param rhs 右辺の演算
	 * @param expectedJavaType 値に対してこの使用地点で要求されるJavaの型
	 */
	private void compile(LocalVar site, OwnRhs rhs, JavaType expectedJavaType) {
		switch(rhs) {
		case OwnRhs.Cnst(ConstValue value) -> {
			loadCnst(value);
		}
		case OwnRhs.DirectApp(Id fun, Seq<OwnUse> args) -> {
			Type funTy = types.get(fun);
			Seq<Type> flattenTys = funTy.flatten();

			getDecl(fun, descriptor -> {
				// 全ての引数をstackに載せる
				Seq.zip(args, flattenTys.take(args.size())).forEach(
						(arg, ty) -> compile(arg, toJavaType(ty)));

				mv.visitMethodInsn(
						Opcodes.INVOKESTATIC,
						module.name(),
						javaMethodName(fun),
						descriptor,
						false);

				JavaType stackTopTy = toJavaType(funTy.dropArgs(args.size()));
				checkcastIfNeed(stackTopTy, expectedJavaType);
			}, builtin -> {
				// 全ての引数をstackに載せる
				Seq.zip(args, flattenTys.take(args.size())).forEach(
						(arg, ty) -> compile(arg, toJavaType(ty)));
				builtin.accept(mv);
			}, ctor -> {
				// <init>を呼ぶ
				JavaType subclass = javaClasses.get(ctor.id());
				mv.visitTypeInsn(Opcodes.NEW, subclass.toClassName());
				mv.visitInsn(Opcodes.DUP);  // 値を返さないのでポインタを複製しておく

				// 全ての引数をstackに載せる
				Seq.zip(args, flattenTys.take(args.size())).forEach(
						(arg, ty) -> compile(arg, toJavaType(ty)));
				mv.visitMethodInsn(
						Opcodes.INVOKESPECIAL,
						subclass.toClassName(),
						"<init>",
						toMethodDesc(ctor.args(), Type.UNIT),
						false);

				checkcastIfNeed(subclass, expectedJavaType);
			});
		}
		case OwnRhs.ClosureApp(OwnUse fun, Seq<OwnUse> args) -> {
			compile(fun, JavaType.FUNCTION);

			compile(args.head(), JavaType.OBJECT);
			invokeApply();
			for (OwnUse arg : args.tail()) {
				checkcast(JavaType.FUNCTION);
				compile(arg, JavaType.OBJECT);
				invokeApply();
			}
			checkcastIfNeed(JavaType.OBJECT, expectedJavaType);
		}
		case OwnRhs.MakeClosure(Id impl, Seq<OwnUse> captures) -> {
			if(ctors.containsKey(impl)) {  // データ型の初期化確認
				// 部分適用する前にコンストラクタ用メソッド（<init>とは別）があるか確認
				ensureCtorOriginal(ctors.get(impl));
			}

			// 組込みへの対処 TODO 分離
			Builtin builtinValue = builtins.getOrNull(impl);
			if(builtinValue != null) {
				builtinValue.accept(mv);
				return;
			}

			// 引数が要らない場合戻り値のデータを置く
			Type implTy = types.get(impl);
			if(!implTy.isArrow()) {
				mv.visitMethodInsn(
						Opcodes.INVOKESTATIC,
						module.name(),
						javaMethodName(impl),
						toplevelDescs.get(impl),
						false);
				return;
			}

			loadCurried(impl);

			if(captures.isEmpty()) {
				checkcastIfNeed(JavaType.FUNCTION, expectedJavaType);
				return;
			}

			for (OwnUse cap : captures) {
				checkcast(JavaType.FUNCTION);
				compile(cap, JavaType.OBJECT);
				invokeApply();
			}
			checkcastIfNeed(JavaType.OBJECT, expectedJavaType);
		}
		case OwnRhs.If(OwnUse condition, OwnBlock thenBlock, OwnBlock elseBlock) -> {
			Label l1 = new Label();
			Label l2 = new Label();
			compile(condition, toJavaType(Type.BOOL));
			int mark = markLocals();
			Primitive.BOOL.genUnboxing(mv);
			mv.visitJumpInsn(Opcodes.IFEQ, // = 0; false
					l1);
			compile(thenBlock, expectedJavaType);
			restoreLocals(mark);
			mv.visitJumpInsn(Opcodes.GOTO, l2);
			mv.visitLabel(l1);
			compile(elseBlock, expectedJavaType);
			restoreLocals(mark);
			mv.visitLabel(l2);
		}
		case OwnRhs.Case(OwnUse target, Seq<OwnBranch> branches) -> {
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
			compile(target, JavaType.OBJECT);
			int caseTargetLocal = locals.size();
			locals.add(LOCAL_DUMMY_ID);
			mv.visitVarInsn(Opcodes.ASTORE, caseTargetLocal);
			Label neck = new Label();

			// マッチしないパターンに遭遇したら次の選択肢に進む
			int mark = markLocals();
			for (int branchIdx = 0; branchIdx < branches.size(); branchIdx++) {
				Label nextBranchLabel = (branchIdx < branches.size() - 1) ? new Label() : null;
				OwnBranch branch = branches.at(branchIdx);
				mv.visitVarInsn(Opcodes.ALOAD, caseTargetLocal);
				checkMatchAndStoreLocals(branch.pattern(), null, nextBranchLabel); // TODO: Dectorの分解にはdeclTyは要らないのでメソッドを分ける
				compile(branch.body(), expectedJavaType);
				mv.visitJumpInsn(Opcodes.GOTO, neck);

				if (nextBranchLabel != null) {
					mv.visitLabel(nextBranchLabel);
				}
				restoreLocals(mark);
			}
			mv.visitLabel(neck);
		}
		case OwnRhs.MakeRecord(Seq<OwnRecordField> fields) -> {
			int mark = markLocals();

			// 計算順序は記述順に（この制限は外してもよい）
			record StoredField(OwnRecordField field, int localIndex) {}
			SeqBuffer<StoredField> canonicalFields = new SeqBuffer<>(fields.size());
			for(OwnRecordField field : fields) {
				compile(field.value(), JavaType.OBJECT);
				int localIndex = locals.size();
				locals.add(LOCAL_DUMMY_ID);
				mv.visitVarInsn(Opcodes.ASTORE, localIndex);
				canonicalFields.add(new StoredField(field, localIndex));
			}
			Seq<StoredField> canonicalFieldsSorted = canonicalFields.toSeq().sorted(
					Comparator.comparing(stored -> stored.field().name()));
			canonicalFieldsSorted.forEach(stored ->
				mv.visitVarInsn(Opcodes.ALOAD, stored.localIndex()));

			String descriptor = "(" + "Ljava/lang/Object;".repeat(fields.size())
					+ ")Lzlk/runtime/ZlkRecord;";
			StringBuilder encodedNames = new StringBuilder("v1;");
			canonicalFieldsSorted.forEach(stored -> encodedNames
					.append(stored.field().name().length())
					.append('#')
					.append(stored.field().name()));
			mv.visitInvokeDynamicInsn(
					"recordLiteral",
					descriptor,
					RECORD_LITERAL_BOOTSTRAP,
					encodedNames.toString());
			checkcastIfNeed(JavaType.RECORD, expectedJavaType);

			restoreLocals(mark);
		}
		case OwnRhs.RecordGet(OwnUse target, String field) -> {
			compile(target, JavaType.RECORD);
			mv.visitLdcInsn(field);
			mv.visitMethodInsn(
					Opcodes.INVOKEINTERFACE,
					JavaType.RECORD.toClassName(),
					"get",
					"(Ljava/lang/String;)Ljava/lang/Object;",
					true);
			checkcastIfNeed(JavaType.OBJECT, expectedJavaType);
		}
		case OwnRhs.RecordUpdate(OwnUse target, Seq<OwnRecordField> fields) -> {
			compile(target, JavaType.RECORD);
			for(OwnRecordField field : fields) {
				mv.visitLdcInsn(field.name());
				compile(field.value(), JavaType.OBJECT);
				if(module.isInplaceRecordUpdate(compilingFun, site)) {  // 破壊的更新の可否
					mv.visitMethodInsn(
							Opcodes.INVOKESTATIC,
							"zlk/runtime/internal/RecordOps",
							"inplaceUpdate",
							"(" + JavaType.RECORD.toDesc()
									+ "Ljava/lang/String;Ljava/lang/Object;)" + JavaType.RECORD.toDesc(),
							false);
				} else {
					mv.visitMethodInsn(
							Opcodes.INVOKEINTERFACE,
							JavaType.RECORD.toClassName(),
							"update",
							"(Ljava/lang/String;Ljava/lang/Object;)Lzlk/runtime/ZlkRecord;",
							true);
				}
			}
			checkcastIfNeed(JavaType.RECORD, expectedJavaType);
		}
		}
	}
	/**
	 * operand stackへ値を置く
	 * @param arg
	 * @param expectedJavaType
	 */
	private void compile(OwnUse arg, JavaType expectedJavaType) {
		LocalVar var = arg.var();
		var.id().ifPresentOrElse(id -> {
			// local変数から読み込み
			loadLocal(id);
			checkcastIfNeed(toJavaType(types.get(id)), expectedJavaType);
		}, () -> {
			// 値を生成
			compile(stack.remove(var), expectedJavaType);
		});
	}

	private void checkcastIfNeed(JavaType actual, JavaType expected) {
		if(actual.castRequired(expected)) {
			checkcast(expected);
		};
	}
	private void checkcast(JavaType expected) {
		mv.visitTypeInsn(Opcodes.CHECKCAST, expected.toClassName());
	}

	/**
	 * スタックトップとパターンのマッチをチェックする．
	 * マッチした場合，値をローカル変数に格納する．
	 * マッチしなかった場合，次のLabelにjumpする．
	 * 次のLabelがnullであるとき，チェックは省略される．
	 * @param pat
	 * @param declTy
	 * @param next 次のラベル
	 */
	private void checkMatchAndStoreLocals(OwnPattern pat, Type declTy, Label next) {
		switch(pat) {
		case OwnPattern.Wildcard(Type _, Location _) -> {
			mv.visitInsn(Opcodes.POP);
		}
		case OwnPattern.Var(LocalVar var, _, Location _) -> {
			checkcastIfNeed(JavaType.OBJECT, toJavaType(pat.type()));
			storeLocal(locals.size(), pat.type());
			locals.add(var.id().get());  // TODO: ここ明示的な変数名が無ければstackに
		}
		case OwnPattern.Ctor(Id ctor, Seq<OwnPattern> args, Type _, Location _) -> {
			Ctor ctorDecl = ctors.get(ctor);
			String subClassName = javaClasses.get(ctor).toClassName();
			if(next != null) {
				mv.visitInsn(Opcodes.DUP);
				mv.visitTypeInsn(Opcodes.INSTANCEOF, subClassName);
				Label matched = new Label();
				mv.visitJumpInsn(Opcodes.IFNE, matched);
				mv.visitInsn(Opcodes.POP);
				mv.visitJumpInsn(Opcodes.GOTO, next);
				mv.visitLabel(matched);
			}
			mv.visitTypeInsn(Opcodes.CHECKCAST, subClassName);
			int ctorLocal = locals.size();
			locals.add(LOCAL_DUMMY_ID);
			mv.visitVarInsn(Opcodes.ASTORE, ctorLocal);
			Seq.zip(args, ctorDecl.args()).forEachIndexed((idx, arg, ctorArg) -> {
				mv.visitVarInsn(Opcodes.ALOAD, ctorLocal);
				mv.visitFieldInsn(
						Opcodes.GETFIELD,
						subClassName,
						"val"+idx,
						toDesc(ctorArg));
				checkMatchAndStoreLocals(arg, ctorArg, next);
			});
		}
		case OwnPattern.Record(Seq<OwnPattern.Var> fields, Type _, Location _) -> {
			mv.visitTypeInsn(Opcodes.CHECKCAST, JavaType.RECORD.toClassName());
			int recordLocal = locals.size();
			locals.add(LOCAL_DUMMY_ID);
			mv.visitVarInsn(Opcodes.ASTORE, recordLocal);
			for(OwnPattern.Var field : fields) {
				mv.visitVarInsn(Opcodes.ALOAD, recordLocal);
				mv.visitLdcInsn(field.id().simpleName());
				mv.visitMethodInsn(
						Opcodes.INVOKEINTERFACE,
						JavaType.RECORD.toClassName(),
						"get",
						"(Ljava/lang/String;)Ljava/lang/Object;",
						true);
				checkMatchAndStoreLocals(field, null, next);
			}
		}
		};
	}

	private int markLocals() {
		return locals.size();
	}

	private void restoreLocals(int mark) {
		while(locals.size() > mark) {
			locals.removeLast();
		}
	}

	/**
	 * コンストラクタと同名の，戻り値のあるメソッドをなければ作る
	 * @param ctor
	 */
	private void ensureCtorOriginal(Ctor ctor) {
		Id id = ctor.id();
		if (toplevelDescs.containsKey(id)) {
			return;
		}

		// ディスクリプタを登録
		String subclassName = javaClasses.get(id).toClassName();
		String argsDesc = ctor.args()
				.map(arg -> toDesc(arg))
				.join("");
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

			ctor.args().forEachIndexed((i, ty) -> loadLocal(i, ty));

			mv.visitMethodInsn(
					Opcodes.INVOKESPECIAL,
					subclassName,
					"<init>",
					toMethodDesc(ctor.args(), Type.UNIT),
					false);

			mv.visitInsn(Opcodes.ARETURN);

			mv.visitMaxs(-1, -1);
			mv.visitEnd();
		});
	}

	private void getDecl(Id id,
			Consumer<String> forTopLevelDescriptor,
			Consumer<Builtin> forBuiltin,
			Consumer<Ctor> forCtor) {

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

		Ctor ctor = ctors.getOrNull(id);
		if(ctor != null) {
			forCtor.accept(ctor);
			return;
		}

		neverHappen("id must be found: "+id);
	}

	/**
	 * カリー化した関数オブジェクトを呼び出す
	 * @param originalId カリー化する前のtoplevelのId
	 */
	private void loadCurried(Id originalId) {
		if(!toplevelDescs.containsKey(originalId)) {
			throw new Error("no method exists: "+originalId);
		}

		int arity = ctors.containsKey(originalId)
				? ctors.get(originalId).args().size()
				: toplevelDecls.get(originalId).arity();

		Seq<Type> implTy = types.get(originalId).flatten();

		Id nextId = originalId;
		for (int i = arity - 1; i >= 0; i--) {
			Seq<Type> args = implTy.slice(0, i);
			Seq<Type> ret = implTy.slice(i, implTy.size());
			Id lambdaId = Id.intern(originalId, "$" + i);
			genCurryingStep(lambdaId, args, nextId, Type.arrow(ret));
			nextId = lambdaId;
		}

		mv.visitMethodInsn(
				Opcodes.INVOKESTATIC,
				module.name(),
				javaMethodName(nextId),
				"()" + JavaType.FUNCTION.toDesc(),
				false
		);
	}

	/**
	 * カリー化用のメソッドを作成予約する
	 *
	 * @param name 作成するメソッドの名前
	 * @param argTys factoryTypeの引数部分（キャプチャ変数）
	 * @param target 呼び出すメソッド
	 * @param retTy 関数オブジェクトの型
	 */
	private void genCurryingStep(Id name, Seq<Type> argTys, Id target, Type.Arrow retTy) {
		// 作成済みであれば何もしない
		if(toplevelDescs.containsKey(name)) {
			return;
		}

		String desc = toMethodDesc(argTys, retTy);
		String sign = toSignature(argTys, retTy);
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
				mv.visitVarInsn(Opcodes.ALOAD, i);
			}

			mv.visitInvokeDynamicInsn(
					"apply",
					desc,
					LAMBDA_METAFACTORY,
					toMethodType("("+JavaType.OBJECT.toDesc()+")"+JavaType.OBJECT.toDesc()),
					new Handle(
							Opcodes.H_INVOKESTATIC,
							module.name(),
							javaMethodName(target),
							toplevelDescs.get(target),
							false),
					toMethodType(toMethodDesc(Seq.of(retTy.arg()), retTy.ret())));

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
			Primitive.BOOL.genBoxing(mv);
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
			Primitive.INT.genBoxing(mv);
		}
		}
	}
	private void loadLocal(Id id) {
		loadLocal(locals.indexOf(id), types.get(id));
	}
	private void storeLocal(Id id) {
		storeLocal(locals.size(), types.get(id));
		locals.add(id);
	}

	private void loadLocal(int idx, Type ty) {
		assert ty != Type.UNIT;
		mv.visitVarInsn(Opcodes.ALOAD, idx);
	}
	private void storeLocal(int idx, Type ty) {
		assert ty != Type.UNIT;
		mv.visitVarInsn(Opcodes.ASTORE, idx);
	}

	private void genReturn(Type type) {
		if(type == Type.UNIT) {
			throw new RuntimeException();
		}
		mv.visitInsn(Opcodes.ARETURN);
	}

	private void invokeApply() {
		mv.visitMethodInsn(
				Opcodes.INVOKEINTERFACE,
				JavaType.FUNCTION.toClassName(),
				"apply",
				"(Ljava/lang/Object;)Ljava/lang/Object;",
				true);
	}

	private String getDescription(OwnFunDecl decl) {
		Type funTy = types.get(decl.id());
		int arity = decl.arity();
		Seq<Type> argTys = funTy.flatten().take(arity);
		Type retTy = funTy.dropArgs(arity);
		return toMethodDesc(argTys, retTy);
	}

	/**
	 * 指定した型に対応するディスクリプタ表現を返す
	 * @param type
	 * @return
	 */
	private String toDesc(Type ty) {
		return toJavaType(ty).toDesc();
	}

	/**
	 * 指定した型に対応するJavaType表現を返す
	 * @param type
	 * @return JavaType
	 */
	private JavaType toJavaType(Type type) {

		return switch(type) {
		case Type.CtorApp atom -> javaClasses.get(atom.id());
		case Type.Arrow _ -> JavaType.FUNCTION;
		case Type.Var _ -> JavaType.OBJECT;
		case Type.Record _ -> JavaType.RECORD;
		};
	}

	private static String toMethodDesc(Seq<Type> argTys, Type retTy, Function<Type, JavaType> mapper) {
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		argTys.forEach(ty -> sb.append(mapper.apply(ty).toDesc()));
		sb.append(")");
		sb.append(mapper.apply(retTy).toDesc());
		return sb.toString();
	}
	private String toMethodDesc(Seq<Type> argTys, Type retTy) {
		return toMethodDesc(argTys, retTy, this::toJavaType);
	}
	private String toSignature(Type type) {
		if(type instanceof Type.Arrow(Type arg, Type ret)) {
			return "Ljava/util/function/Function<"+toSignature(arg)+toSignature(ret)+">;";
		}
		return toJavaType(type).toDesc();
	}
	private String toSignature(Seq<Type> args, Type ret) {
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		args.forEach(ty -> sb.append(toSignature(ty)));
		sb.append(")");
		sb.append(toSignature(ret));
		return sb.toString();
	}

	private static org.objectweb.asm.Type toMethodType(String descriptor) {
		return org.objectweb.asm.Type.getMethodType(descriptor);
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
