package zlk.phase.clconv;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import zlk.common.ConstValue;
import zlk.common.Location;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.common.id.IdSet;
import zlk.ir.clcalc.CcCaseBranch;
import zlk.ir.clcalc.CcCtor;
import zlk.ir.clcalc.CcExp;
import zlk.ir.clcalc.CcExp.CcCase;
import zlk.ir.clcalc.CcExp.CcClosureApp;
import zlk.ir.clcalc.CcExp.CcCnst;
import zlk.ir.clcalc.CcExp.CcDirectApp;
import zlk.ir.clcalc.CcExp.CcIf;
import zlk.ir.clcalc.CcExp.CcLet;
import zlk.ir.clcalc.CcExp.CcMkCls;
import zlk.ir.clcalc.CcExp.CcRecord;
import zlk.ir.clcalc.CcExp.CcRecordAccess;
import zlk.ir.clcalc.CcExp.CcRecordField;
import zlk.ir.clcalc.CcExp.CcRecordUpdate;
import zlk.ir.clcalc.CcExp.CcVar;
import zlk.ir.clcalc.CcFunDecl;
import zlk.ir.clcalc.CcModule;
import zlk.ir.clcalc.CcPattern;
import zlk.ir.clcalc.CcTypeDecl;
import zlk.ir.idcalc.IcCaseBranch;
import zlk.ir.idcalc.IcCtor;
import zlk.ir.idcalc.IcExp;
import zlk.ir.idcalc.IcExp.IcApp;
import zlk.ir.idcalc.IcExp.IcCase;
import zlk.ir.idcalc.IcExp.IcCnst;
import zlk.ir.idcalc.IcExp.IcIf;
import zlk.ir.idcalc.IcExp.IcLamb;
import zlk.ir.idcalc.IcExp.IcLet;
import zlk.ir.idcalc.IcExp.IcRecord;
import zlk.ir.idcalc.IcExp.IcRecordAccess;
import zlk.ir.idcalc.IcExp.IcRecordField;
import zlk.ir.idcalc.IcExp.IcRecordUpdate;
import zlk.ir.idcalc.IcExp.IcVarCtor;
import zlk.ir.idcalc.IcExp.IcVarForeign;
import zlk.ir.idcalc.IcExp.IcVarLocal;
import zlk.ir.idcalc.IcModule;
import zlk.ir.idcalc.IcPattern;
import zlk.ir.idcalc.IcTypeDecl;
import zlk.ir.idcalc.IcValDecl;
import zlk.phase.recon.ExpOrPatternMap;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

/**
 * クロージャ変換と関数のトップレベルへのflattenを行う
 * 関数適用をトップレベルの呼出しと関数オブジェクトへのapplyに分離する
 */
public final class ClosureConverter {

	private final IcModule src;
	private final IdMap<Type> type;  // 変換後のIdの型を追加
	private final ExpOrPatternMap<Type> partExpTypes;
	private final IdSet knowns;
	private final IdMap<Integer> arities;  // その時点で直接呼出し可能であることが確定した関数の引数の数

	private final SeqBuffer<CcFunDecl> toplevels;
	private final AtomicInteger closureCount;

	public ClosureConverter(
			IcModule src,
			IdMap<Type> type,
			ExpOrPatternMap<Type> partExpTypes,
			Seq<Id> builtins
	) {
		this.src = src;
		this.type = type;
		this.partExpTypes = partExpTypes;

		this.knowns = new IdSet();
		src.decls().forEach(decl -> knowns.add(decl.id()));
		src.types().forEach(union -> union.ctors().forEach(ctor -> knowns.add(ctor.id())));
		builtins.forEach(knowns::add);

		this.arities = new IdMap<>();
		src.decls().forEach(decl ->
			arities.put(decl.id(), decl.args().size())
		);
		src.types().forEach(union ->
			union.ctors().forEach(ctor ->
				arities.put(ctor.id(), ctor.args().size())
			)
		);

		this.toplevels = new SeqBuffer<>();
		this.closureCount = new AtomicInteger();
	}

	public CcModule convert() {
		src.decls()
				.map(decl -> compileFunc(decl.id(), decl.args(), decl.body()))
				.forEach(maybeCls -> maybeCls.ifPresent(cls -> {
					throw new RuntimeException("toplevel must not be closure: "+cls.implId()); }));

		Seq<CcTypeDecl> types = src.types().map(ty -> convert(ty));
		return new CcModule(src.name(), types, toplevels.toSeq());
	}

	/**
	 * 関数をトップレベルに変換し追加する．クロージャに変換された場合，その作成を返す．
	 * @return クロージャの生成（クロージャが必要だったとき）
	 */
	private Optional<CcMkCls> compileFunc(Id id, Seq<IcPattern> args, IcExp body) {

		knowns.add(id);
		CcExp ccBody = compile(body);
		Seq<Id> frees = fvFunc(ccBody, args);
		Seq<CcPattern> args_ = args.map(arg -> convert(arg));

		if(frees.isEmpty()) {
			toplevels.add(new CcFunDecl(id, args_, ccBody, body.loc()));
			return Optional.empty();
		} else {
			CcFunDecl closureFunc = makeClosure(id, frees, args_, ccBody, body.loc());
			toplevels.add(closureFunc);
			arities.remove(id);  // 直接呼出し出来なかったので取り除く
			knowns.add(closureFunc.id());
			return Optional.of(new CcMkCls(
					closureFunc.id(),
					frees.map(id_ -> (CcExp) new CcVar(id_, type.get(id_), Location.noLocation())),
					type.get(id),
					closureFunc.loc()));
		}
	}

	private CcMkCls compileLambda(Id id, Seq<IcPattern> args, IcExp body, Type lambdaType) {
		CcExp ccBody = compile(body);
		Seq<Id> frees = fvFunc(ccBody, args);
		CcFunDecl closureFunc =
				makeClosure(
						id,
						frees,
						args.map(arg -> convert(arg)),
						ccBody,
						body.loc());

		toplevels.add(closureFunc);
		knowns.add(closureFunc.id());
		return new CcMkCls(
				closureFunc.id(),
				frees.map(id_ -> (CcExp) new CcVar(id_, type.get(id_), Location.noLocation())),
				lambdaType,
				closureFunc.loc());
	}

	/**
	 * 自由変数を引数に入れた新しい関数宣言を作る
	 * @param origId 元の関数のId
	 * @param frees 自由変数
	 * @param args_ 元の関数の引数
	 * @param body 元の関数の中身
	 * @param loc 元の関数のLocation
	 * @return
	 */
	private CcFunDecl makeClosure(
			Id origId,
			Seq<Id> frees,
			Seq<CcPattern> args_,
			CcExp body,
			Location loc
	) {
		Type clsTy = Type.fromSeq(
			Seq.concat(  // 前に自由変数を付加
					frees.map(id -> type.get(id)),
					type.get(origId).flatten()
			));
		Id clsId = freshId(origId);
		type.put(clsId, clsTy);

		// 自由変数を外に出すために置き換えを作る
		IdMap<Id> idMap =
				frees.fold(IdMap.folder(
						id -> id,
						id -> Id.intern(clsId, id.simpleName())));
		// 自己再帰のために元の関数名も置換対象
		idMap.put(origId, clsId);

		SeqBuffer<CcPattern> clsArgs = new SeqBuffer<>();
		SeqBuffer<CcVar> caps = new SeqBuffer<>();
		for(Id free : frees) {
			Id newId = idMap.get(free);
			Type ty = type.get(free);
			clsArgs.add(new CcPattern.Var(newId, ty, Location.noLocation()));
			caps.add(new CcVar(newId, ty, Location.noLocation()));
			type.put(newId, ty);
		}
		clsArgs.addAll(args_);

		CcExp clsBody = rewriteCls(origId, clsId, caps.toSeq(), idMap, body);  // body.substId(idMap);

		return new CcFunDecl(clsId, clsArgs.toSeq(), clsBody, loc);
	}
	private CcExp rewriteCls(Id origId, Id clsId, Seq<CcVar> caps, IdMap<Id> idMap, CcExp target) {
		Function<CcExp, CcExp> go = exp -> rewriteCls(origId, clsId, caps, idMap, exp);

		return switch (target) {
		case CcCnst _ ->
			target;

		case CcVar(Id id, Type expType, Location loc) ->
			new CcVar(idMap.getOrDefault(id, id), expType, loc);

		case CcDirectApp(Id funId, Seq<CcExp> args, Type expType, Location loc) ->
			funId.equals(origId) ?
				new CcDirectApp(
						clsId,
						Seq.concat(caps, args.map(go)),  // キャプチャした変数が明示的に引数に
						expType,
						loc) :
				new CcDirectApp(
					funId,
					args.map(go),
					expType,
					loc);

		case CcClosureApp(CcExp funExp, Seq<CcExp> args, Type expType, Location loc) ->
			new CcClosureApp(
					go.apply(funExp),
					args.map(go),
					expType,
					loc);

		case CcMkCls(Id clsFunc, Seq<CcExp> caps_, Type expType, Location loc) ->
			clsFunc.equals(origId) ?
				new CcMkCls(
						clsId,
						Seq.concat(caps, caps_.map(go)),  // 部分適用の前にキャプチャした変数を含める
						expType,
						loc) :
				new CcMkCls(
					clsFunc,
					caps_.map(go),
					expType,
					loc);

		case CcIf(CcExp cond, CcExp thenExp, CcExp elseExp, Type expType, Location loc) ->
			new CcIf(
					go.apply(cond),
					go.apply(thenExp),
					go.apply(elseExp),
					expType,
					loc);

		case CcLet(Id varName, CcExp boundExp, CcExp body, Type expType, Location loc) ->
			new CcLet(
					varName,
					go.apply(boundExp),
					go.apply(body),
					expType,
					loc);

		case CcCase(CcExp cond, Seq<CcCaseBranch> branches, Type expType, Location loc) ->
			new CcCase(
					go.apply(cond),
					branches.map(branch -> new CcCaseBranch(
							branch.pattern(),
							go.apply(branch.body()),
							branch.loc())),
					expType,
					loc);

		case CcRecord(Seq<CcRecordField> fields, Type expType, Location loc) ->
			new CcRecord(
					fields.map(field -> new CcRecordField(
							field.name(), go.apply(field.value()), field.loc())),
					expType,
					loc);
		case CcRecordAccess(CcExp recordTarget, String field, Type expType, Location loc) ->
			new CcRecordAccess(go.apply(recordTarget), field, expType, loc);
		case CcRecordUpdate(CcExp recordTarget, Seq<CcRecordField> fields, Type expType, Location loc) ->
			new CcRecordUpdate(
					go.apply(recordTarget),
					fields.map(field -> new CcRecordField(
							field.name(), go.apply(field.value()), field.loc())),
					expType,
					loc);
		};
	}

	private CcExp compile(IcExp exp) {
		Type expType = partExpTypes.get(exp);
		return switch (exp) {
		case IcCnst(ConstValue value, Location loc) -> {
			yield new CcCnst(value, loc);
		}
		case IcVarLocal(Id id, Location loc) -> {
			if(arities.containsKey(id)) {  // IcApp以外の形で関数がでてくる場合
				yield new CcMkCls(id, Seq.of(), expType, loc);
			}
			yield new CcVar(id, expType, loc);
		}
		case IcVarForeign(Id id, Type ty, Location loc) -> {
			if(arities.containsKey(id) && !ty.isArrow()) {  // 組込みへの対処 TODO 分離
				yield new CcDirectApp(id, Seq.of(), expType, loc);
			}
			yield new CcMkCls(id, Seq.of(), expType, loc);
		}
		case IcVarCtor(Id id, Type ty, Location loc) -> {
			if(!ty.isArrow()) {  // 引数をとらない場合
				yield new CcDirectApp(id, Seq.of(), expType, loc);
			}
			yield new CcMkCls(id, Seq.of(), expType, loc);
		}
		case IcLamb(Id id, Seq<IcPattern> args, IcExp body, Location _) -> {
			yield compileLambda(id, args, body, expType);
		}
		case IcApp(IcExp fun, Seq<IcExp> args, Location loc) -> {
			// 直接呼び出せて引数が揃っていればCcDirectApp
			Id callableId =
					switch(fun) {
					case IcVarLocal v -> arities.containsKey(v.id()) ? v.id() : null;  // クロージャでない
					case IcVarForeign v -> v.id();
					case IcVarCtor v -> v.id();
					default -> null;
					};

			Seq<CcExp> ccArgs = args.map(arg -> compile(arg));

			if(callableId != null) {
				int arity = arities.containsKey(callableId)
						? arities.get(callableId)
						: type.get(callableId).flatten().size() - 1;  // TODO: 組込み関数のアリティを求めるまともな方法

				if(arity > args.size()) {
					yield new CcMkCls(callableId, ccArgs, expType, loc);
				} else {
					Type directType = partExpTypes.get(fun).dropArgs(arity);
					CcExp result = new CcDirectApp(callableId, ccArgs.take(arity), directType, loc);
					if(arity < args.size()) {
						result = new CcClosureApp(result, ccArgs.drop(arity), expType, loc);
					}
					yield result;
				}
			} else {
				yield new CcClosureApp(compile(fun), ccArgs, expType, loc);
			}
		}
		case IcIf(IcExp cond, IcExp thenExp, IcExp elseExp, Location loc) -> {
			yield new CcIf(
					compile(cond),
					compile(thenExp),
					compile(elseExp),
					expType,
					loc);
		}
		case IcLet(Seq<IcValDecl> decls, IcExp body, Location _) -> {
			Seq<IcValDecl> funDecls = decls.filter(decl -> !decl.args().isEmpty());
			Seq<IcValDecl> valDecls = decls.filter(decl -> decl.args().isEmpty());

			// 相互再帰のために先にknownsに登録しておく
			funDecls.forEach(decl -> knowns.add(decl.id()));

			// 暫定的に直接呼出し可能とする
			funDecls.forEach(decl -> arities.put(decl.id(), decl.args().size()));  // 暫定的に直接呼出し可能とする

			// 各関数を変換（クロージャが必要ならCcMkClsが返る）
			IdMap<Optional<CcMkCls>> closures = funDecls.fold(IdMap.folder(
					decl -> decl.id(),
					decl -> compileFunc(decl.id(), decl.args(), decl.body())
			));

			// 各値を変換
			IdMap<CcExp> valRhs = valDecls.fold(IdMap.folder(
					decl -> decl.id(),
					decl -> compile(decl.body())
			));

			// 逆順に畳み込む
			CcExp result = compile(body);
			for(IcValDecl decl : decls.reversed()) {
				Id id = decl.id();
				if(decl.args().isEmpty()) {
					result = new CcLet(id, valRhs.get(id), result, expType, decl.loc());
				} else {
					CcExp cap = result;
					result = closures.get(id)
							.map(cls -> (CcExp)new CcLet(id, cls, cap, expType, decl.loc()))
							.orElse(cap);
				}
			}
			yield result;
		}
		case IcCase(IcExp target, Seq<IcCaseBranch> branches, Location loc) -> {
			CcExp ccTarget = compile(target);
			Seq<CcCaseBranch> compiledBranches =
					branches.map(branch -> new CcCaseBranch(
									convert(branch.pattern()),
									compile(branch.body()),
									branch.loc()));
			yield new CcCase(ccTarget, compiledBranches, expType, loc);
		}
		case IcRecord(Seq<IcRecordField> fields, Location loc) ->
			new CcRecord(
					fields.map(field -> new CcRecordField(
							field.name(), compile(field.value()), field.loc())),
					expType,
					loc);
		case IcRecordAccess(IcExp target, String field, Location loc) ->
			new CcRecordAccess(compile(target), field, expType, loc);
		case IcRecordUpdate(IcExp target, Seq<IcRecordField> fields, Location loc) ->
			new CcRecordUpdate(
					compile(target),
					fields.map(field -> new CcRecordField(
							field.name(), compile(field.value()), field.loc())),
					expType,
					loc);
		};
	}

	private Seq<Id> fvFunc(CcExp body, Seq<IcPattern> args) {
		SeqBuffer<Id> free = new SeqBuffer<>();
		IdSet bounded = new IdSet(knowns);
		args.forEach(arg -> arg.accumulateVars(bounded));
		fv(body, bounded, free);
		return free.toSeq();
	}

	private void fv(CcExp exp, IdSet bounded, SeqBuffer<Id> free) {
		switch (exp) {
		case CcCnst _ -> {
		}
		case CcVar(Id id, Type _, Location _) -> {
			if (!bounded.contains(id) && !free.contains(id)) {
				free.add(id);
			}
		}
		case CcDirectApp(Id funId, Seq<CcExp> args, Type _, Location _) -> {
			if (!bounded.contains(funId) && !free.contains(funId)) {
				free.add(funId);
			}
			args.forEach(arg -> fv(arg, bounded, free));
		}
		case CcClosureApp(CcExp fun, Seq<CcExp> args, Type _, Location _) -> {
			fv(fun, bounded, free);
			args.forEach(arg -> fv(arg, bounded, free));
		}
		case CcMkCls(Id clsFunc, Seq<CcExp> caps, Type _, Location _) -> {
			if (!bounded.contains(clsFunc)) {
				throw new AssertionError();
			}
			caps.forEach(exp_ -> fv(exp_, bounded, free));
		}
		case CcIf(CcExp cond, CcExp thenExp, CcExp elseExp, Type _, Location _) -> {
			fv(cond, bounded, free);
			fv(thenExp, bounded, free);
			fv(elseExp, bounded, free);
		}
		case CcLet(Id varName, CcExp boundExp, CcExp body, Type _, Location _) -> {
			bounded.add(varName);
			fv(boundExp, bounded, free);
			fv(body, bounded, free);
		}
		case CcCase(CcExp target, Seq<CcCaseBranch> branches, Type _, Location _) -> {
			fv(target, bounded, free);
			for (CcCaseBranch branch : branches) {
				branch.pattern().accumulateVars(bounded);
				fv(branch.body(), bounded, free);
			}
		}
		case CcRecord(Seq<CcRecordField> fields, Type _, Location _) ->
			fields.forEach(field -> fv(field.value(), bounded, free));
		case CcRecordAccess(CcExp target, String _, Type _, Location _) ->
			fv(target, bounded, free);
		case CcRecordUpdate(CcExp target, Seq<CcRecordField> fields, Type _, Location _) -> {
			fv(target, bounded, free);
			fields.forEach(field -> fv(field.value(), bounded, free));
		}
		}
	}

	private Id freshId(Id original) {
		String orgStr = original.canonicalName();

		int idx = orgStr.indexOf('.');
		if(idx == -1) {
			throw new Error(orgStr);
		}
		String exceptModule = orgStr.substring(idx+1);

		return Id.intern(
				src.name()
				+ Id.SEPARATOR + closureCount.getAndIncrement()
				+ Id.SEPARATOR + exceptModule);
	}

	private CcTypeDecl convert(IcTypeDecl icType) {
		return new CcTypeDecl(icType.id(), icType.ctors().map(ctor -> convert(ctor)), icType.loc());
	}

	private CcCtor convert(IcCtor icCtor) {
		return new CcCtor(icCtor.id(), icCtor.args(), icCtor.loc());
	}

	private CcPattern convert(IcPattern pat) {
		return switch(pat) {
		case IcPattern.Wildcard(Location loc) ->
			new CcPattern.Wildcard(loc);
		case IcPattern.Var var -> convert(var);
		case IcPattern.Dector(IcExp.IcVarCtor ctor, Seq<IcPattern> args, Location loc) ->
			new CcPattern.Dector(ctor, args.map(arg -> convert(arg)), partExpTypes.get(pat), loc);
		case IcPattern.Record(Seq<IcPattern.Var> fields, Location loc) ->
			new CcPattern.Record(fields.map(this::convert), partExpTypes.get(pat), loc);
		};
	}
	private CcPattern.Var convert(IcPattern.Var var) {
		return new CcPattern.Var(var.id(), partExpTypes.get(var), var.loc());
	}
}
