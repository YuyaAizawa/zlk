package zlk.clconv;

import static zlk.util.ErrorUtils.neverHappen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
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
import zlk.common.id.IdList;
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

	private final List<CcFunDecl> toplevels;
	private final AtomicInteger closureCount;

	public ClosureConverter(IcModule src, IdMap<Type> type, IdentityHashMap<ExpOrPattern, Type> nodeTypes, IdList builtins) {
		this.src = src;
		this.type = type;
		this.nodeTypes = nodeTypes;

		this.knowns = new HashSet<>();
		src.decls().forEach(decl -> knowns.add(decl.id()));
		src.types().forEach(union -> union.ctors().forEach(ctor -> knowns.add(ctor.id())));
		knowns.addAll(builtins);

		this.arities = new IdMap<>();
		src.decls().forEach(decl ->
			arities.put(decl.id(), decl.args().size())
		);
		src.types().forEach(union ->
			union.ctors().forEach(ctor ->
				arities.put(ctor.id(), ctor.args().size())
			)
		);

		this.toplevels = new ArrayList<>();
		this.closureCount = new AtomicInteger();
	}

	public CcModule convert() {
		src.decls()
				.stream()
				.map(decl -> compileFunc(decl.id(), decl.args(), decl.body()))
				.forEach(maybeCls -> maybeCls.ifPresent(cls -> {
					throw new RuntimeException("toplevel must not be closure: "+cls.implId()); }));

		List<CcTypeDecl> types = src.types().stream().map(ty -> convert(ty)).toList();
		return new CcModule(src.name(), types, toplevels);
	}

	/**
	 * 関数をトップレベルに変換し追加する．クロージャに変換された場合，その作成を返す．
	 * @return クロージャの生成（クロージャが必要だったとき）
	 */
	private Optional<CcMkCls> compileFunc(Id id, List<IcPattern> args_, IcExp body) {

		knowns.add(id);
		CcExp ccBody = compile(body);
		IdList frees = fvFunc(ccBody, args_);

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
					frees.stream().map(id_ -> (CcExp) new CcVar(id_, Location.noLocation())).toList(),
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
			IdList frees,
			List<IcPattern> args_,
			CcExp body,
			Type retTy,
			Location loc
	) {
		List<Type> types = new ArrayList<>();
		frees.forEach(id -> types.add(type.get(id)));
		args_.forEach(pat -> types.add(type.get(pat.headId())));
		types.add(retTy);

		Type clsTy = Type.fromList(types);
		Id clsId = freshId(original);
		type.put(clsId, clsTy);

		// 自由変数を外に出すために置き換えを作る
		IdMap<Id> idMap =
				frees.stream().collect(IdMap.collector(
						Function.identity(),
						id -> Id.fromParentAndSimpleName(clsId, id.simpleName())));
		// 自己再帰のために元の関数名も置換対象
		idMap.put(original, clsId);

		List<IcPattern> clsArgs = new ArrayList<>();
		List<CcVar> caps = new ArrayList<>();
		for(Id free : frees) {
			Id newId = idMap.get(free);
			clsArgs.add(new Var(newId, Location.noLocation()));
			caps.add(new CcVar(newId, Location.noLocation()));
			type.put(newId, type.get(free));
		}
		clsArgs.addAll(args_);

		CcExp clsBody = rewriteCls(original, clsId, caps, idMap, body);  // body.substId(idMap);

		return new CcFunDecl(clsId, clsArgs, clsBody, loc);
	}
	private CcExp rewriteCls(Id origId, Id clsId, List<CcVar> caps,  IdMap<Id> idMap, CcExp target) {
		Function<CcExp, CcExp> go = exp -> rewriteCls(origId, clsId, caps, idMap, exp);

		return switch (target) {
		case CcCnst _ -> {
			yield target;
		}
		case CcVar(Id id, Location loc) -> {
			yield new CcVar(idMap.getOrDefault(id, id), loc);
		}
		case CcDirectApp(Id funId, List<CcExp> args, Location loc) -> {
			if(funId.equals(origId)) {
				List<CcExp> args_ = new ArrayList<>();
				args_.addAll(caps);  // キャプチャした変数が明示的に引数に
				args.stream().map(go).forEach(args_::add);
				yield new CcDirectApp(
						clsId,
						args_,
						loc);
			}
			yield new CcDirectApp(
					funId,
					args.stream().map(go).toList(),
					loc);
		}
		case CcClosureApp(CcExp funExp, List<CcExp> args, Location loc) -> {
			yield new CcClosureApp(
					go.apply(funExp),
					args.stream().map(go).toList(),
					loc);
		}
		case CcMkCls(Id clsFunc, List<CcExp> caps_, Location loc) -> {
			if(clsFunc.equals(origId)) {
				List<CcExp> caps__ = new ArrayList<>();
				caps__.addAll(caps);  // 部分適用の前にキャプチャした変数を含める
				caps_.stream().map(go).forEach(caps__::add);
				yield new CcMkCls(
						clsId,
						caps__,
						loc);
			}
			yield new CcMkCls(
					clsFunc,
					caps_.stream().map(go).toList(),
					loc);
		}
		case CcIf(CcExp cond, CcExp thenExp, CcExp elseExp, Location loc) -> {
			yield new CcIf(
					go.apply(cond),
					go.apply(thenExp),
					go.apply(elseExp),
					loc);
		}
		case CcLet(Id varName, CcExp boundExp, CcExp body, Location loc) -> {
			yield new CcLet(
					varName,
					go.apply(boundExp),
					go.apply(body),
					loc);
		}
		case CcCase(CcExp cond, Type targetTy, List<CcCaseBranch> branches, Location loc) -> {
			yield new CcCase(
					go.apply(cond),
					targetTy,
					branches.stream().map(branch -> new CcCaseBranch(
							branch.pattern(),
							go.apply(branch.body()),
							branch.loc())).toList(),
					loc);
		}
		};
	}

	private CcExp compile(IcExp exp) {
		return switch (exp) {
		case IcCnst(ConstValue value, Location loc) -> {
			yield new CcCnst(value, loc);
		}
		case IcVarLocal(Id id, Location loc) -> {
			if(arities.containsKey(id)) {  // IcApp以外の形で関数がでてくる場合
				yield new CcMkCls(id, List.of(), loc);
			}
			yield new CcVar(id, loc);
		}
		case IcVarForeign(Id id, Type ty, Location loc) -> {
			if(arities.containsKey(id) && !ty.isArrow()) {  // 組込みへの対処 TODO 分離
				yield new CcDirectApp(id, List.of(), loc);
			}
			yield new CcMkCls(id, List.of(), loc);
		}
		case IcVarCtor(Id id, Type ty, Location loc) -> {
			if(!ty.isArrow()) {  // 引数をとらない場合
				yield new CcDirectApp(id, List.of(), loc);
			}
			yield new CcMkCls(id, List.of(), loc);
		}
		case IcLamb(List<IcPattern> _, IcExp body, Location _) -> {
			yield neverHappen("no anonymous abs in this version.", body.loc());
		}
		case IcApp(IcExp fun, List<IcExp> args, Location loc) -> {
			// 直接呼び出せて引数が揃っていればCcDirectApp
			Id callableId =
					switch(fun) {
					case IcVarLocal v -> arities.containsKey(v.id()) ? v.id() : null;  // クロージャでない
					case IcVarForeign v -> v.id();
					case IcVarCtor v -> v.id();
					default -> null;
					};

			List<CcExp> ccArgs = args.stream().map(arg -> compile(arg)).toList();

			if(callableId != null) {
				int arity = arities.containsKey(callableId)
						? arities.get(callableId)
						: type.get(callableId).flatten().size() - 1;  // TODO: 組込み関数のアリティを求めるまともな方法

				if(arity > args.size()) {
					yield new CcMkCls(callableId, ccArgs, loc);
				} else {
					CcExp result = new CcDirectApp(callableId, ccArgs.subList(0, arity), loc);
					if(arity < args.size()) {
						result = new CcClosureApp(result, ccArgs.subList(arity, args.size()), loc);
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
		case IcLet(List<IcValDecl> decls, IcExp body, Location _) -> {
			// 関数と値に分ける
			List<IcValDecl> funDecls = new ArrayList<>();
			List<IcValDecl> valDecls = new ArrayList<>();
			for(IcValDecl decl : decls) {
				if(decl.args().isEmpty()) {
					valDecls.add(decl);
				} else {
					funDecls.add(decl);
				}
			}

			// 相互再帰のために先にknownsに登録しておく
			funDecls.forEach(decl -> knowns.add(decl.id()));

			// 暫定的に直接呼出し可能とする
			funDecls.forEach(decl -> arities.put(decl.id(), decl.args().size()));  // 暫定的に直接呼出し可能とする

			// 各関数を変換（クロージャが必要ならCcMkClsが返る）
			IdMap<Optional<CcMkCls>> closures = funDecls.stream().collect(IdMap.collector(
					decl -> decl.id(),
					decl -> compileFunc(decl.id(), decl.args(), decl.body())
			));

			// 各値を変換
			IdMap<CcExp> valRhs = valDecls.stream().collect(IdMap.collector(
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
		case IcCase(IcExp target, List<IcCaseBranch> branches, Location loc) -> {
			CcExp ccTarget = compile(target);
			List<CcCaseBranch> compiledBranches =
					branches.stream()
							.map(branch -> new CcCaseBranch(
									branch.pattern(),
									compile(branch.body()),
									branch.loc()))
							.toList();
			yield new CcCase(ccTarget, nodeTypes.get(target), compiledBranches, loc);
		}
		};
	}

	private IdList fvFunc(CcExp body, List<IcPattern> args) {
		IdList free = new IdList();
		Set<Id> bounded = new HashSet<>(knowns);
		args.forEach(arg -> arg.accumulateVars(bounded));
		fv(body, bounded, free);
		return free;
	}

	private void fv(CcExp exp, Set<Id> bounded, IdList free) {
		switch (exp) {
		case CcCnst _ -> {
		}
		case CcVar(Id id, Location _) -> {
			if (!bounded.contains(id) && !free.contains(id)) {
				free.add(id);
			}
		}
		case CcDirectApp(Id funId, List<CcExp> args, Location _) -> {
			if (!bounded.contains(funId) && !free.contains(funId)) {
				free.add(funId);
			}
			args.forEach(arg -> fv(arg, bounded, free));
		}
		case CcClosureApp(CcExp fun, List<CcExp> args, Location _) -> {
			fv(fun, bounded, free);
			args.forEach(arg -> fv(arg, bounded, free));
		}
		case CcMkCls(Id clsFunc, List<CcExp> caps, Location _) -> {
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
		case CcCase(CcExp target, Type _, List<CcCaseBranch> branches, Location _) -> {
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

		return Id.fromCanonicalName(
				src.name()
				+ Id.SEPARATOR + closureCount.getAndIncrement()
				+ Id.SEPARATOR + exceptModule);
	}

	private CcTypeDecl convert(IcTypeDecl icType) {
		return new CcTypeDecl(icType.id(), icType.ctors().stream().map(ctor -> convert(ctor)).toList(), icType.loc());
	}

	private CcCtor convert(IcCtor icCtor) {
		return new CcCtor(icCtor.id(), icCtor.args(), icCtor.loc());
	}
}
