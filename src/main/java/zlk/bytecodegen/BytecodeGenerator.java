package zlk.bytecodegen;

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

import zlk.clcalc.CcCaseBranch;
import zlk.clcalc.CcCtor;
import zlk.clcalc.CcExp;
import zlk.clcalc.CcExp.CcCase;
import zlk.clcalc.CcExp.CcClosureApp;
import zlk.clcalc.CcExp.CcCnst;
import zlk.clcalc.CcExp.CcDirectApp;
import zlk.clcalc.CcExp.CcIf;
import zlk.clcalc.CcExp.CcLet;
import zlk.clcalc.CcExp.CcMkCls;
import zlk.clcalc.CcExp.CcVar;
import zlk.clcalc.CcFunDecl;
import zlk.clcalc.CcModule;
import zlk.common.ConstValue;
import zlk.common.Location;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.core.Builtin;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcPattern;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;
import zlk.util.collection.Stack;

/**
 * クロージャ解決後のトップレベルにフラットになったASTをJVMのバイトコードに変換する
 */

public final class BytecodeGenerator {

	public static final int OPCODE_VERSION = Opcodes.V23;

	private final CcModule module;
	private final String origin;
	private final IdMap<Type> types;
	private final IdMap<Builtin> builtins;
	private final IdMap<String> toplevelDescs;
	private final IdMap<CcFunDecl> toplevelDecls;
	private final IdMap<JavaType> javaClasses;
	private final IdMap<CcCtor> ctors;
	private final Seq<CustomType> customTypes;
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
			"zlk/runtime/ArrayRecord",
			"bootstrapLiteral",
			"(Ljava/lang/invoke/MethodHandles$Lookup;"
			+ "Ljava/lang/String;"
			+ "Ljava/lang/invoke/MethodType;"
			+ "Ljava/lang/String;"
			+ ")Ljava/lang/invoke/CallSite;",
			false);

	// for compileDecl
	private SeqBuffer<Id> locals;
	private MethodVisitor mv;
	private Stack<Runnable> pendings;

	public BytecodeGenerator(CcModule module, IdMap<Type> types, Seq<Builtin> builtins, String origin) {
		this.module = module;
		this.types = types;
		this.builtins = builtins.fold(IdMap.folder(b -> b.id(), b -> b));
		this.origin = origin;
		this.toplevelDescs = new IdMap<>();
		this.toplevelDecls = new IdMap<>();
		this.javaClasses = new IdMap<>();
		this.ctors = new IdMap<>();
		this.customTypes = module.types().map(decl -> new CustomType(module.name(), decl, origin));
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

	private void compileDecl(CcFunDecl decl) { // TODO トップレベルは全て非カリー化する
		Id id = decl.id();
		try {
			this.locals = new SeqBuffer<>();
			mv = cw.visitMethod(
					Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
					javaMethodName(id),
					toplevelDescs.get(id),
					null,
					null);

			mv.visitCode();
			registerArgs(decl.args());
			Type retTy = types.get(id).dropArgs(decl.arity());
			compile(decl.body(), toJavaType(retTy));
			genReturn(retTy);
			mv.visitMaxs(-1, -1); // compute all frames and local automatically
			mv.visitEnd();

			while(!pendings.isEmpty()) {
				pendings.pop().run();
			}
		} catch(RuntimeException e) {
			throw new RuntimeException("on method "+id, e);
		}
	}

	/**
	 * 関数の引数をlocalsに登録する
	 * @param args 引数
	 */
	private void registerArgs(Seq<IcPattern> args) {
		args.forEach(arg -> {
			if(arg instanceof IcPattern.Var var) {
				locals.add(var.id());
			} else {
				locals.add(LOCAL_DUMMY_ID);
			}
		});

		for (int i = 0; i < args.size(); i++) {
			if(args.at(i) instanceof IcPattern.Dector ctor) {
				Type subClassTy = types.get(ctor.ctor().id());
				loadLocal(i, subClassTy);
				registerArgRec(ctor);
			} else if(args.at(i) instanceof IcPattern.Record record) {
				mv.visitVarInsn(Opcodes.ALOAD, i);
				registerArgRec(record);
			}
		}
	}
	private void registerArgRec(IcPattern pat) {
		// 事前条件：パターンに対応する値がstackのトップに乗っている
		// 事後条件：パターンに対応する値をstackから消費
		switch(pat) {
		case IcPattern.Wildcard(Location _) -> {
			mv.visitInsn(Opcodes.POP);  // TODO: 最適化 フィールドからとらないように
		}
		case IcPattern.Var(Id id, Location _) -> {
			Type varType = types.get(id);
			checkcastIfNeed(JavaType.OBJECT, toJavaType(varType));
			storeLocal(locals.size(), varType);
			locals.add(id);
		}
		case IcPattern.Dector(IcExp.IcVarCtor ctor, Seq<IcPattern.Arg> args, Location _) -> {
			mv.visitTypeInsn(Opcodes.CHECKCAST, javaClasses.get(ctor.id()).toClassName());
			// stackの数を調整
			if(args.size() == 0) {
				mv.visitInsn(Opcodes.POP);
			}

			for (int i = 0; i < args.size() - 1; i++) {
				mv.visitInsn(Opcodes.DUP);
			}

			for(int fieldIdx = 0; fieldIdx < args.size(); fieldIdx++) {
				IcPattern.Arg ctorArg = args.at(fieldIdx);
				mv.visitFieldInsn(
						Opcodes.GETFIELD,
						javaClasses.get(ctor.id()).toClassName(),
						CustomType.componentName(fieldIdx),
						toDesc(ctorArg.type()));
				registerArgRec(ctorArg.pattern());
			}
		}
		case IcPattern.Record(Seq<IcPattern.RecordField> fields, Location _) -> {
			mv.visitTypeInsn(Opcodes.CHECKCAST, JavaType.RECORD.toClassName());
			if(fields.isEmpty()) mv.visitInsn(Opcodes.POP);
			for(int i = 0; i < fields.size(); i++) {
				if(i < fields.size() - 1) mv.visitInsn(Opcodes.DUP);
				IcPattern.RecordField field = fields.at(i);
				mv.visitLdcInsn(field.name());
				mv.visitMethodInsn(
						Opcodes.INVOKEINTERFACE,
						JavaType.RECORD.toClassName(),
						"get",
						"(Ljava/lang/String;)Ljava/lang/Object;",
						true);
				registerArgRec(field.pattern());
			}
		}
		}
	}

	/**
	 * 式に対応するバイトコードを生成する．
	 *
	 * Javaの型消去方式に対応するため，期待される戻り値型の上限境界を指定し，
	 * 満たせない場合ダウンキャストを補う．
	 *
	 * @param exp
	 * @param ubTy 式がstackに残す値の上限境界の型
	 */
	private void compile(CcExp exp, JavaType ubTy) {
		switch (exp) {
		case CcCnst(ConstValue value, Location _) -> {
			loadCnst(value);
		}
		case CcVar(Id id, Location _) -> {
			int localIndex = locals.indexOf(id);
			if(localIndex == -1) {
				throw new Error("No such locals: "+id);
			}
			loadLocal(localIndex, types.get(id));
		}
		case CcDirectApp(Id funId, Seq<CcExp> args, Location _) -> {
			Type funTy = types.get(funId);
			Seq<Type> flattenTys = funTy.flatten();

			getDecl(funId, descriptor -> {
				// 全ての引数をstackに載せる
				args.forEachIndexed((i, arg) -> compile(arg, toJavaType(flattenTys.at(i))));  // TODO: zipがあると簡潔

				mv.visitMethodInsn(
						Opcodes.INVOKESTATIC,
						module.name(),
						javaMethodName(funId),
						descriptor,
						false);

				JavaType stackTopTy = toJavaType(funTy.dropArgs(args.size()));
				checkcastIfNeed(stackTopTy, ubTy);
			}, builtin -> {
				// 全ての引数をstackに載せる
				args.forEachIndexed((i, arg) -> compile(arg, toJavaType(flattenTys.at(i))));  // TODO: zipがあると簡潔
				builtin.accept(mv);
			}, ctor -> {
				// <init>を呼ぶ
				JavaType subclass = javaClasses.get(ctor.id());
				mv.visitTypeInsn(Opcodes.NEW, subclass.toClassName());
				mv.visitInsn(Opcodes.DUP);  // 値を返さないのでポインタを複製しておく

				// 全ての引数をstackに載せる
				args.forEachIndexed((i, arg) -> compile(arg, toJavaType(flattenTys.at(i))));  // TODO: zipがあると簡潔
				mv.visitMethodInsn(
						Opcodes.INVOKESPECIAL,
						subclass.toClassName(),
						"<init>",
						toMethodDesc(ctor.args(), Type.UNIT),
						false);

				checkcastIfNeed(subclass, ubTy);
			});
		}
		case CcClosureApp(CcExp funExp, Seq<CcExp> args, Location _) -> {
			compile(funExp, JavaType.FUNCTION);

			compile(args.head(), JavaType.OBJECT);
			invokeApply();
			for (CcExp arg : args.tail()) {
				checkcast(JavaType.FUNCTION);
				compile(arg, JavaType.OBJECT);
				invokeApply();
			}
			checkcastIfNeed(JavaType.OBJECT, ubTy);
		}
		case CcMkCls(Id implId, Seq<CcExp> caps, Location _) -> {
			if(ctors.containsKey(implId)) {  // データ型の初期化確認
				// 部分適用する前にコンストラクタ用メソッド（<init>とは別）があるか確認
				ensureCtorOriginal(ctors.get(implId));
			}

			// 組込みへの対処 TODO 分離
			Builtin builtinValue = builtins.getOrNull(implId);
			if(builtinValue != null) {
				builtinValue.accept(mv);
				return;
			}

			// 引数が要らない場合戻り値のデータを置く
			Type implTy = types.getOrNull(implId);
			if(implTy == null) {
				throw new RuntimeException(implId.toString());
			}
			if(!implTy.isArrow()) {
				mv.visitMethodInsn(
						Opcodes.INVOKESTATIC,
						module.name(),
						javaMethodName(implId),
						toplevelDescs.get(implId),
						false);
				return;
			}

			loadCurried(implId);

			if(caps.isEmpty()) {
				checkcastIfNeed(JavaType.FUNCTION, ubTy);
				return;
			}

			for (CcExp cap : caps) {
				checkcast(JavaType.FUNCTION);
				compile(cap, JavaType.OBJECT);
				invokeApply();
			}
			checkcastIfNeed(JavaType.OBJECT, ubTy);
		}
		case CcIf(CcExp cond, CcExp thenExp, CcExp elseExp, Location _) -> {
			Label l1 = new Label();
			Label l2 = new Label();
			compile(cond, toJavaType(Type.BOOL));
			Primitive.BOOL.genUnboxing(mv);
			mv.visitJumpInsn(Opcodes.IFEQ, // = 0; false
					l1);
			compile(thenExp, ubTy);
			mv.visitJumpInsn(Opcodes.GOTO, l2);
			mv.visitLabel(l1);
			compile(elseExp, ubTy);
			mv.visitLabel(l2);
		}
		case CcLet(Id varName, CcExp boundExp, CcExp body, Location _) -> {
			Type varTy = types.get(varName);
			compile(boundExp, toJavaType(varTy));
			locals.add(varName);
			storeLocal(locals.size() - 1, varTy);
			compile(body, ubTy);
		}
		case CcCase(CcExp target, Type targetTy, Seq<CcCaseBranch> branches, Location _) -> {
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
			compile(target, toJavaType(targetTy));
			int caseTargetLocal = locals.size();
			locals.add(LOCAL_DUMMY_ID);
			mv.visitVarInsn(Opcodes.ASTORE, caseTargetLocal);
			Label neck = new Label();

			// マッチしないパターンに遭遇したら次の選択肢に進む
			SeqBuffer<Id> localsBeforeBranch = new SeqBuffer<>(locals);
			for (int branchIdx = 0; branchIdx < branches.size(); branchIdx++) {
				Label nextBranchLabel = (branchIdx < branches.size() - 1) ? new Label() : null;
				CcCaseBranch branch = branches.at(branchIdx);
				mv.visitVarInsn(Opcodes.ALOAD, caseTargetLocal);
				checkMatchAndStoreLocals(branch.pattern(), null, nextBranchLabel); // TODO: Dectorの分解にはdeclTyは要らないのでメソッドを分ける
				compile(branch.body(), ubTy);
				mv.visitJumpInsn(Opcodes.GOTO, neck);

				if (nextBranchLabel != null) {
					mv.visitLabel(nextBranchLabel);
				}
				locals = localsBeforeBranch;
			}
			mv.visitLabel(neck);
		}
		case CcExp.CcRecord(Seq<CcExp.CcRecordField> fields, Location _) -> {
			// 計算順序は記述順に（この制限は外してもよい）
			record StoredField(CcExp.CcRecordField field, int localIndex) {}
			SeqBuffer<StoredField> canonicalFields = new SeqBuffer<>(fields.size());
			for(CcExp.CcRecordField field : fields) {
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
			checkcastIfNeed(JavaType.RECORD, ubTy);
		}
		case CcExp.CcRecordAccess(CcExp target, String field, Location _) -> {
			compile(target, JavaType.RECORD);
			mv.visitLdcInsn(field);
			mv.visitMethodInsn(
					Opcodes.INVOKEINTERFACE,
					JavaType.RECORD.toClassName(),
					"get",
					"(Ljava/lang/String;)Ljava/lang/Object;",
					true);
			checkcastIfNeed(JavaType.OBJECT, ubTy);
		}
		case CcExp.CcRecordUpdate(CcExp target, Seq<CcExp.CcRecordField> fields, Location _) -> {
			compile(target, JavaType.RECORD);
			for(CcExp.CcRecordField field : fields) {
				mv.visitLdcInsn(field.name());
				compile(field.value(), JavaType.OBJECT);
				mv.visitMethodInsn(
						Opcodes.INVOKEINTERFACE,
						JavaType.RECORD.toClassName(),
						"update",
						"(Ljava/lang/String;Ljava/lang/Object;)Lzlk/runtime/ZlkRecord;",
						true);
			}
			checkcastIfNeed(JavaType.RECORD, ubTy);
		}
		}
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
	private void checkMatchAndStoreLocals(IcPattern pat, Type declTy, Label next) {
		switch(pat) {
		case IcPattern.Wildcard(Location _) -> {
			mv.visitInsn(Opcodes.POP);
		}
		case IcPattern.Var(Id id, Location _) -> {
			Type expTy = types.get(id);
			checkcastIfNeed(JavaType.OBJECT, toJavaType(expTy));
			storeLocal(locals.size(), expTy);
			locals.add(id);
		}
		case IcPattern.Dector(IcExp.IcVarCtor ctor, Seq<IcPattern.Arg> args, Location _) -> {
			CcCtor ctorDecl = ctors.get(ctor.id());
			String subClassName = javaClasses.get(ctor.id()).toClassName();
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
			for(int i = 0; i < args.size(); i++) {
				mv.visitVarInsn(Opcodes.ALOAD, ctorLocal);
				mv.visitFieldInsn(
						Opcodes.GETFIELD,
						subClassName,
						"val"+i,
						toDesc(ctorDecl.args().at(i)));
				checkMatchAndStoreLocals(args.at(i).pattern(), ctorDecl.args().at(i), next);
			}
		}
		case IcPattern.Record(Seq<IcPattern.RecordField> fields, Location _) -> {
			mv.visitTypeInsn(Opcodes.CHECKCAST, JavaType.RECORD.toClassName());
			int recordLocal = locals.size();
			locals.add(LOCAL_DUMMY_ID);
			mv.visitVarInsn(Opcodes.ASTORE, recordLocal);
			for(IcPattern.RecordField field : fields) {
				mv.visitVarInsn(Opcodes.ALOAD, recordLocal);
				mv.visitLdcInsn(field.name());
				mv.visitMethodInsn(
						Opcodes.INVOKEINTERFACE,
						JavaType.RECORD.toClassName(),
						"get",
						"(Ljava/lang/String;)Ljava/lang/Object;",
						true);
				checkMatchAndStoreLocals(field.pattern(), null, next);
			}
		}
		};
	}

	/**
	 * コンストラクタと同名の，戻り値のあるメソッドをなければ作る
	 * @param ctor
	 */
	private void ensureCtorOriginal(CcCtor ctor) {
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
				mv.visitIntInsn(Opcodes.ALOAD, i);
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
			mv.visitInsn(Opcodes.RETURN);
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

	private String getDescription(CcFunDecl decl) {
		Type funTy = types.get(decl.id());
		int arity = decl.arity();
		Seq<Type> argTys = funTy.flatten().take(arity);
		Type retTy = funTy.dropArgs(arity);
		return toMethodDesc(argTys, retTy);
	}

	/**
	 * 指定した型に対応するクラス名を返す
	 * @param ty
	 * @return
	 */
	private String toClassName(Type ty) {
		return toJavaType(ty).toClassName();
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
