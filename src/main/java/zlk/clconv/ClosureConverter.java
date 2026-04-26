package zlk.clconv;

import static zlk.util.ErrorUtils.neverHappen;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

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
import zlk.clcalc.CcTypeDecl;
import zlk.common.ConstValue;
import zlk.common.Location;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.idcalc.ExpOrPattern;
import zlk.idcalc.IcCaseBranch;
import zlk.idcalc.IcCtor;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcExp.IcApp;
import zlk.idcalc.IcExp.IcCase;
import zlk.idcalc.IcExp.IcCnst;
import zlk.idcalc.IcExp.IcIf;
import zlk.idcalc.IcExp.IcLamb;
import zlk.idcalc.IcExp.IcLet;
import zlk.idcalc.IcExp.IcVarCtor;
import zlk.idcalc.IcExp.IcVarForeign;
import zlk.idcalc.IcExp.IcVarLocal;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcPattern;
import zlk.idcalc.IcPattern.Var;
import zlk.idcalc.IcTypeDecl;
import zlk.idcalc.IcValDecl;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

/**
 * クロージャ変換と関数のトップレベルへのflattenを行う
 * 関数適用をトップレベルの呼出しと関数オブジェクトへのapplyに分離する
 */
public final class ClosureConverter {

	private final IcModule src;
	private final IdMap<Type> type;  // 変換後のIdの型を追加
	private final IdentityHashMap<ExpOrPattern, Type> nodeTypes;  // 項の型 TODO: caseのcondしか使っていないので消せそう
	private final Set<Id> knowns;
	private final IdMap<Integer> arities;  // その時点で直接呼出し可能であることが確定した関数の引数の数

	private final SeqBuffer<CcFunDecl> toplevels;
	private final AtomicInteger closureCount;

	public ClosureConverter(IcModule src, IdMap<Type> type, IdentityHashMap<ExpOrPattern, Type> nodeTypes, Seq<Id> builtins) {
		this.src = src;
		this.type = type;
		this.nodeTypes = nodeTypes;

		this.knowns = new HashSet<>();
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
	private Optional<CcMkCls> compileFunc(Id id, Seq<IcPattern> args_, IcExp body) {

		knowns.add(id);
		CcExp ccBody = compile(body);
		Seq<Id> frees = fvFunc(ccBody, args_);

		if(frees.isEmpty()) {
			toplevels.add(new CcFunDecl(id, args_, ccBody, body.loc()));
			return Optional.empty();

		} else {
			CcFunDecl closureFunc = makeClosure(id, frees, args_, ccBody, type.get(id).dropArgs(args_.size()), body.loc());
			toplevels.add(closureFunc);
			arities.remove(id);  // 直接呼出し出来なかったので取り除く
			knowns.add(closureFunc.id());
			return Optional.of(new CcMkCls(
					closureFunc.id(),
					frees.map(id_ -> (CcExp) new CcVar(id_, Location.noLocation())),
					closureFunc.loc()));
		}
	}

	/**
	 * 自由変数を引数に入れた新しい関数宣言を作る
	 * @param original 元の関数のId
	 * @param frees 自由変数
	 * @param args_ 元の関数の引数
	 * @param body 元の関数の中身
	 * @param retTy 元の関数の戻り値型
	 * @param loc 元の関数のLocation
	 * @return
	 */
	private CcFunDecl makeClosure(
			Id original,
			Seq<Id> frees,
			Seq<IcPattern> args_,
			CcExp body,
			Type retTy,
			Location loc
	) {
		SeqBuffer<Type> types = new SeqBuffer<>();
		frees.forEach(id -> types.add(type.get(id)));
		args_.forEach(pat -> types.add(type.get(pat.headId())));
		types.add(retTy);

		Type clsTy = Type.fromSeq(types.toSeq());
		Id clsId = freshId(original);
		type.put(clsId, clsTy);

		// 自由変数を外に出すために置き換えを作る
		IdMap<Id> idMap =
				frees.fold(IdMap.folder(
						id -> id,
						id -> Id.intern(clsId, id.simpleName())));
		// 自己再帰のために元の関数名も置換対象
		idMap.put(original, clsId);

		SeqBuffer<IcPattern> clsArgs = new SeqBuffer<>();
		SeqBuffer<CcVar> caps = new SeqBuffer<>();
		for(Id free : frees) {
			Id newId = idMap.get(free);
			clsArgs.add(new Var(newId, Location.noLocation()));
			caps.add(new CcVar(newId, Location.noLocation()));
			type.put(newId, type.get(free));
		}
		clsArgs.addAll(args_);

		CcExp clsBody = rewriteCls(original, clsId, caps.toSeq(), idMap, body);  // body.substId(idMap);

		return new CcFunDecl(clsId, clsArgs.toSeq(), clsBody, loc);
	}
	private CcExp rewriteCls(Id origId, Id clsId, Seq<CcVar> caps, IdMap<Id> idMap, CcExp target) {
		Function<CcExp, CcExp> go = exp -> rewriteCls(origId, clsId, caps, idMap, exp);

		return switch (target) {
		case CcCnst _ ->
			target;

		case CcVar(Id id, Location loc) ->
			new CcVar(idMap.getOrDefault(id, id), loc);

		case CcDirectApp(Id funId, Seq<CcExp> args, Location loc) ->
			funId.equals(origId) ?
				new CcDirectApp(
						clsId,
						Seq.concat(caps, args.map(go)),  // キャプチャした変数が明示的に引数に
						loc) :
				new CcDirectApp(
					funId,
					args.map(go),
					loc);

		case CcClosureApp(CcExp funExp, Seq<CcExp> args, Location loc) ->
			new CcClosureApp(
					go.apply(funExp),
					args.map(go),
					loc);

		case CcMkCls(Id clsFunc, Seq<CcExp> caps_, Location loc) ->
			clsFunc.equals(origId) ?
				new CcMkCls(
						clsId,
						Seq.concat(caps, caps_.map(go)),  // 部分適用の前にキャプチャした変数を含める
						loc) :
				new CcMkCls(
					clsFunc,
					caps_.map(go),
					loc);

		case CcIf(CcExp cond, CcExp thenExp, CcExp elseExp, Location loc) ->
			new CcIf(
					go.apply(cond),
					go.apply(thenExp),
					go.apply(elseExp),
					loc);

		case CcLet(Id varName, CcExp boundExp, CcExp body, Location loc) ->
			new CcLet(
					varName,
					go.apply(boundExp),
					go.apply(body),
					loc);

		case CcCase(CcExp cond, Type targetTy, Seq<CcCaseBranch> branches, Location loc) ->
			new CcCase(
					go.apply(cond),
					targetTy,
					branches.map(branch -> new CcCaseBranch(
							branch.pattern(),
							go.apply(branch.body()),
							branch.loc())),
					loc);
		};
	}

	private CcExp compile(IcExp exp) {
		return switch (exp) {
		case IcCnst(ConstValue value, Location loc) -> {
			yield new CcCnst(value, loc);
		}
		case IcVarLocal(Id id, Location loc) -> {
			if(arities.containsKey(id)) {  // IcApp以外の形で関数がでてくる場合
				yield new CcMkCls(id, Seq.of(), loc);
			}
			yield new CcVar(id, loc);
		}
		case IcVarForeign(Id id, Type ty, Location loc) -> {
			if(arities.containsKey(id) && !ty.isArrow()) {  // 組込みへの対処 TODO 分離
				yield new CcDirectApp(id, Seq.of(), loc);
			}
			yield new CcMkCls(id, Seq.of(), loc);
		}
		case IcVarCtor(Id id, Type ty, Location loc) -> {
			if(!ty.isArrow()) {  // 引数をとらない場合
				yield new CcDirectApp(id, Seq.of(), loc);
			}
			yield new CcMkCls(id, Seq.of(), loc);
		}
		case IcLamb(Seq<IcPattern> _, IcExp body, Location _) -> {
			yield neverHappen("no anonymous abs in this version.", body.loc());
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
					yield new CcMkCls(callableId, ccArgs, loc);
				} else {
					CcExp result = new CcDirectApp(callableId, ccArgs.take(arity), loc);
					if(arity < args.size()) {
						result = new CcClosureApp(result, ccArgs.drop(arity), loc);
					}
					yield result;
				}
			} else {
				yield new CcClosureApp(compile(fun), ccArgs, loc);
			}
		}
		case IcIf(IcExp cond, IcExp thenExp, IcExp elseExp, Location loc) -> {
			yield new CcIf(
					compile(cond),
					compile(thenExp),
					compile(elseExp),
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
					result = new CcLet(id, valRhs.get(id), result, decl.loc());
				} else {
					CcExp cap = result;
					result = closures.get(id)
							.map(cls -> (CcExp)new CcLet(id, cls, cap, decl.loc()))
							.orElse(cap);
				}
			}
			yield result;
		}
		case IcCase(IcExp target, Seq<IcCaseBranch> branches, Location loc) -> {
			CcExp ccTarget = compile(target);
			Seq<CcCaseBranch> compiledBranches =
					branches.map(branch -> new CcCaseBranch(
									branch.pattern(),
									compile(branch.body()),
									branch.loc()));
			yield new CcCase(ccTarget, nodeTypes.get(target), compiledBranches, loc);
		}
		};
	}

	private Seq<Id> fvFunc(CcExp body, Seq<IcPattern> args) {
		SeqBuffer<Id> free = new SeqBuffer<>();
		Set<Id> bounded = new HashSet<>(knowns);
		args.forEach(arg -> arg.accumulateVars(bounded));
		fv(body, bounded, free);
		return free.toSeq();
	}

	private void fv(CcExp exp, Set<Id> bounded, SeqBuffer<Id> free) {
		switch (exp) {
		case CcCnst _ -> {
		}
		case CcVar(Id id, Location _) -> {
			if (!bounded.contains(id) && !free.contains(id)) {
				free.add(id);
			}
		}
		case CcDirectApp(Id funId, Seq<CcExp> args, Location _) -> {
			if (!bounded.contains(funId) && !free.contains(funId)) {
				free.add(funId);
			}
			args.forEach(arg -> fv(arg, bounded, free));
		}
		case CcClosureApp(CcExp fun, Seq<CcExp> args, Location _) -> {
			fv(fun, bounded, free);
			args.forEach(arg -> fv(arg, bounded, free));
		}
		case CcMkCls(Id clsFunc, Seq<CcExp> caps, Location _) -> {
			if (!bounded.contains(clsFunc)) {
				throw new AssertionError();
			}
			caps.forEach(exp_ -> fv(exp_, bounded, free));
		}
		case CcIf(CcExp cond, CcExp thenExp, CcExp elseExp, Location _) -> {
			fv(cond, bounded, free);
			fv(thenExp, bounded, free);
			fv(elseExp, bounded, free);
		}
		case CcLet(Id varName, CcExp boundExp, CcExp body, Location _) -> {
			bounded.add(varName);
			fv(boundExp, bounded, free);
			fv(body, bounded, free);
		}
		case CcCase(CcExp target, Type _, Seq<CcCaseBranch> branches, Location _) -> {
			fv(target, bounded, free);
			for (CcCaseBranch branch : branches) {
				branch.pattern().accumulateVars(bounded);
				fv(branch.body(), bounded, free);
			}
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
}
