package zlk.recon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import zlk.common.ConstValue;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.idcalc.IcCaseBranch;
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
import zlk.idcalc.IcValDecl;
import zlk.recon.constraint.Constraint;
import zlk.recon.constraint.Constraint.CEqual;
import zlk.recon.constraint.Constraint.CExists;
import zlk.recon.constraint.Constraint.CForeign;
import zlk.recon.constraint.Constraint.CLet;
import zlk.recon.constraint.Constraint.CLocal;
import zlk.recon.constraint.Constraint.CPhase;
import zlk.recon.constraint.RcType;
import zlk.recon.constraint.RcType.FunN;
import zlk.recon.constraint.RcType.VarN;
import zlk.recon.constraint.State;
import zlk.util.Location;
import zlk.util.Stack;

record Env(Env parent, IdMap<Variable> table) {
	static Env empty() {
		return new Env(null, new IdMap<>());
	}

	Variable get(Id id) {
		for(Env e = this; e != null; e = e.parent) {
			Variable result = e.table.getOrNull(id);
			if(result != null) {
				return result;
			}
		}
		throw new NoSuchElementException(id.buildString());
	}

	Env extend(IdMap<Variable> adds) {
		return new Env(this, adds); // TODO: copyOf(adds)?
	}
}

public final class ConstraintExtractor {

	public static Constraint extract(IcModule module) {
		// TODO: enumのコンストラクタとか
		// TODO: 組込み関数どうするの？
		return extractFromDef(module.decls(),
				new CExists(  // TODO: main関数のletにする
						List.of(),
						List.of()));
	}

	/**
	 * 指定された式の制約を抽出して返す．
	 * @param exp 式
	 * @param expected 期待される型
	 */
	public static Constraint extract(IcExp exp, RcType expected) {
		return switch (exp) {

		case IcCnst(ConstValue value, Location _) ->
			new CEqual(RcType.from(value.type()).resultTy(), expected);

		case IcVarLocal(Id id, Location _) ->
			new CLocal(id, expected);

		case IcVarForeign(Id id, Type type, Location _) ->
			new CForeign(id, type, expected);

		case IcVarCtor(Id id, Type type, Location _) ->
			new CForeign(id, type, expected);

		case IcLamb(List<IcPattern> args, IcExp body, Location _) -> {
			Args args_ = extractFromArgs(args);

			List<Constraint> argsCons = List.of(
				new CLet(
					List.of(),
					args_.state.vars,
					args_.state.headers,
					List.of(new CPhase(args_.state.cons, extractIds(args))),
					extract(body, args_.resultTy)),
				new CEqual(args_.funTy, expected));
			yield new CExists(args_.vars, argsCons);
		}

		case IcApp(IcExp fun, List<IcExp> args, Location _) -> {
			// f a b c : R
			// f : F
			// a : A
			// b : B
			// c : C
			// f : A -> B -> C -> R

			List<Variable> vars = new ArrayList<>();
			List<Constraint> cons = new ArrayList<>();

			Variable funVar = Variable.unbounded();
			vars.add(funVar);
			RcType funTy = new VarN(funVar);
			cons.add(extract(fun, funTy));

			List<Constraint> argCons = new ArrayList<>();
			List<RcType> argTys = new ArrayList<>();
			for(IcExp arg : args) {
				Variable argVar = Variable.unbounded();
				vars.add(argVar);
				RcType argTy = new VarN(argVar);
				argCons.add(extract(arg, argTy));
				argTys.add(argTy);
			}

			Variable resultVar = Variable.unbounded();
			vars.add(resultVar);
			RcType resultType = new VarN(resultVar);
			RcType arityType = resultType;
			for(RcType ty : argTys.reversed()) {
				arityType = new FunN(ty, arityType);
			}

			cons.add(new CEqual(funTy, arityType));
			cons.addAll(argCons);  // アリティの後にしないと引数の数のチェックができない
			cons.add(new CEqual(resultType, expected));

			yield new CExists(vars, cons);
		}

		case IcIf(IcExp condExp, IcExp thenExp, IcExp elseExp, Location _) -> {
			// TODO expectedに型注釈を入れたら展開して対応させる

			Variable branchVar = Variable.unbounded();
			RcType branchTy = new VarN(branchVar);

			yield new CExists(
					List.of(branchVar),
					List.of(extract(condExp, RcType.BOOL),
							extract(thenExp, branchTy),
							extract(elseExp, branchTy),
							new CEqual(branchTy, expected)));
		}

		case IcLet(List<IcValDecl> decls, IcExp body, Location _) ->
			extractFromDef(decls, extract(body, expected));

		case IcCase(IcExp target, List<IcCaseBranch> branches, Location _) -> {
			List<Constraint> cons = new ArrayList<>();

			Variable patVar = Variable.unbounded();
			RcType patTy = new VarN(patVar);
			cons.add(extract(target, patTy));

			// TODO 型注釈から制約を抽出（ここに制約って書けたっけ？）
			Variable branchVar = Variable.unbounded();
			RcType branchTy = new VarN(branchVar);
			for(IcCaseBranch branch : branches) {
				cons.add(extractFromCaseBranch(branch, patTy, branchTy));  // TODO Reason系
			}
			cons.add(new CEqual(branchTy, expected));

			yield new CExists(List.of(patVar, branchVar), cons);
		}
		};
	}

	/**
	 * 引数のパターンの解析結果
	 *
	 * @param vars 引数内で導入された自由変数
	 * @param funTy 関数の型
	 * @param resultTy 結果の型
	 * @param state
	 */
	record Args(
			List<Variable> vars,
			RcType funTy,
			RcType resultTy,
			State state) {}

	public static CLet extractFromDef(List<IcValDecl> decls, Constraint bodyCon) {
		// 0) 先に “外へ出る器 = アンカー” を全部配る（別オブジェクト！）
		IdMap<RcType> header = new IdMap<>();
		for (var decl : decls) {
			header.put(decl.id(), new VarN(Variable.unbounded())); // アンカー
		}

		// 1) 各 def の rhs ブロックを作る（★引数はこの内側CLetのheader）
		// id -> rhsConstraint
		IdMap<Constraint> defCons = new IdMap<>();
		for (var decl : decls) {
			Args a = extractFromArgs(decl.args());

			List<Constraint> headerCons = new ArrayList<>();
			headerCons.addAll(a.state.cons);
			headerCons.add(extract(decl.body(), a.resultTy));

			Constraint rhs = new CLet(
					List.of(), // TODO: 型注釈のrigid
					a.state.vars,
					a.state.headers,
					List.of(new CPhase(headerCons, new IdList())),  // 内側CLetは一般化なし
					new CEqual(a.funTy, header.get(decl.id()))
			);
			defCons.put(decl.id(), rhs);
		}

		// 2) 同フレーム内の依存グラフ→SCC分解→トポ順（できている前提）
		List<IdList> sccTopo = sccTopo(buildGraph(decls));

		// 3) フェーズ列を作る：SCCごとに rhs を並べ、targets=そのSCCのid群
		List<CPhase> phases = new ArrayList<>();
		for (var scc : sccTopo) {
			List<Constraint> items = new ArrayList<>();
			IdList targets = new IdList();
			for (Id id : scc) {
				items.add(defCons.get(id));
				targets.add(id);
			}
			phases.add(new CPhase(items, targets));
		}

		// 4) 外側の CLet にまとめる（★ここが実際の let フレーム）
		return new CLet(/* rigids */ List.of(), // この let 自体で Skolem を効かせるならここ
				/* flexes */ List.of(), // 外側フレームで導入する自由変数があるならここ（通常は空）
				/* header */ header, // アンカー（id -> VarN(...))
				/* phases */ phases, // SCC順のフェーズ列。各フェーズ末尾で一般化される
				/* bodyCon */ bodyCon);
	}

//	public static CLet extractFromDef(
//			List<IcValDecl> decls,
//			Constraint bodyCon) {
//
//
//		List<CPhase> phases = new ArrayList<>();
//		List<Variable> vars = new ArrayList<>();
//		IdMap<RcType> funTys = new IdMap<>();
//		for(IcValDecl decl : decls) {
//			Args args = extractFromArgs(decl.args());
//			Variable varFunTy = Variable.unbounded();
//			VarN rcVarFunTy = new VarN(varFunTy);
//
//			vars.add(varFunTy);
//			funTys.put(decl.id(), args.funTy);
//			headerCons.add(new CLet(
//					List.of(),
//					args.state.vars,
//					args.state.headers,
//					args.state.cons,
//					List.of(new CEqual(rcVarFunTy, args.funTy), extract(decl.body(), args.resultTy))
//					));
//		}
//		return new CLet(
//				List.of(),
//				vars,
//				funTys,
//				headerCons,
//				bodyCon);

//		Args args = extractFromArgs(decl.args());
//		Constraint exprCon = extract(decl.body(), args.resultTy);
//		return new CLet(
//				List.of(),
//				args.vars,
//				IdMap.of(decl.id(), args.funTy),
//				List.of(new CLet(
//						List.of(),
//						args.state.vars,
//						args.state.headers,
//						args.state.cons,
//						List.of(exprCon))),
//				List.of(bodyCon));
//	}

//	public static void extractFromDef(IcValDecl decl, List<Constraint> bodyCons) {
//		Args args = extractFromArgs(decl.args());
//
//		List<Constraint> exprCons = new ArrayList<>();
//		extract(decl.body(), args.resultTy, Reason.NO_EXPECTATION, exprCons);
//
//		return new CLet(
//				List.of(),
//				args.vars,
//				IdMap.of(decl.id(), args.funTy),
//				List.of(new CLet(
//						List.of(),
//						args.state.vars,
//						args.state.headers,
//						args.state.cons,
//						exprCons)),
//				bodyCons));
//	}

//	record Info(List<Variable> vars, List<Constraint> cons, IdMap<RcType> headers) {
//		static Info empty() {
//			return new Info(List.of(), List.of(), IdMap.of());
//		}
//	}
//
//	public static void extractFromRecDef(List<IcValDecl> defs, List<Constraint> bodyCon, List<Constraint> acc) {
//		recDefsHelp(defs, bodyCon, Info.empty(), Info.empty(), acc);
//	}
//	private static void recDefsHelp(List<IcValDecl> defs, List<Constraint> bodyCon, Info ridgedInfo, Info flexInfo, List<Constraint> acc) {
//
//		if(defs.isEmpty()) {
//			List<Constraint> ridgedAndBodyCons = new ArrayList<>();
//			ridgedAndBodyCons.addAll(ridgedInfo.cons);
//			ridgedAndBodyCons.addAll(bodyCon);
//			acc.add(new CLet(
//					ridgedInfo.vars,
//					List.of(),
//					ridgedInfo.headers,
//					List.of(),
//					List.of(new CLet(
//							List.of(),
//							flexInfo.vars,
//							flexInfo.headers,
//							List.of(new CLet(
//									List.of(),
//									List.of(),
//									flexInfo.headers,
//									List.of(),
//									flexInfo.cons)),
//							ridgedAndBodyCons))));
//			return;
//		}
//		IcValDecl def = defs.get(0);
//		List<IcValDecl> otherDefs = defs.subList(1, defs.size());
//
//		State flexState = new State();
//		flexState.vars.addAll(flexInfo.vars);
//		Args args = extractFromArgs(def.args(), flexState);
//
//		Constraint defCon =
//				new CLet(
//						List.of(),
//						args.state.vars,
//						args.state.headers,
//						args.state.cons,
//						List.of(extract(def.body(), args.resultTy)));
//
//		List<Constraint> cons = new ArrayList<>(flexInfo.cons);
//		cons.add(0, defCon);
//		IdMap<RcType> headers = flexInfo.headers.clone();
//		headers.put(def.id(), args.funTy);
//		recDefsHelp(
//				otherDefs,
//				bodyCon,
//				ridgedInfo,
//				new Info(args.vars, cons, headers),
//				acc);
//	}

	public static Args extractFromArgs(List<IcPattern> args) {
		return extractFromArgs(args, new State());
	}

	public static Args extractFromArgs(List<IcPattern> args, State state) {
		List<Variable> vars = new ArrayList<>();
		Stack<RcType> argTys = new Stack<>();
		for(IcPattern arg : args) {
			Variable argVar = Variable.unbounded();
			vars.add(argVar);
			RcType argTy = new VarN(argVar);
			argTys.push(argTy);
			state.add(arg, argTy);
		}

		Variable resultVar = Variable.unbounded();
		vars.add(resultVar);
		RcType resultTy = new VarN(resultVar);
		RcType funType = resultTy;
		for(RcType argTy : argTys) {
			funType = new FunN(argTy, funType);
		}

		return new Args(vars, funType, resultTy, state);
	}

	private static Constraint extractFromCaseBranch(IcCaseBranch branch, RcType patExpected, RcType branchExpected) {
		PatternBinder pb = new PatternBinder();
		pb.bind(branch.pattern(), patExpected);

		Constraint bodyCon = extract(branch.body(), branchExpected);

		ArrayList<Constraint> cons = new ArrayList<>(pb.cons.size()+1);
		cons.addAll(pb.cons);
		cons.add(bodyCon);

		return new CLet(
				List.of(),
				pb.vars,
				pb.headers,
				List.of(new CPhase(cons, new IdList())),  // case branchは一般化する対象なし
				new CEqual(branchExpected, branchExpected)  // TODO: 特に制約がないことを表せた方が良いか？
		);
	}

	private static IdList extractIds(List<IcPattern> patterns) {
		Set<Id> ids = new HashSet<>();
		for(IcPattern pattern : patterns) {
			pattern.accumulateVars(ids);
		}
		return new IdList(ids);
	}

	// 強連結成分分解のための呼び出しグラフ作成
	public static IdMap<IdList> buildGraph(List<IcValDecl> decls) {
		IdList targets = decls.stream().map(decl -> decl.id()).collect(IdList.collector());
		IdMap<IdList> result = new IdMap<>();
		decls.forEach(decl -> result.put(decl.id(), collectRefs(decl.body(), targets)));
		return result;
	}
	private static IdList collectRefs(IcExp exp, IdList targets) {
		IdList acc = new IdList();
		collectRefsHelp(exp, targets, acc);
		return acc;
	}
	private static void collectRefsHelp(IcExp exp, IdList targets, IdList acc) {
		switch(exp) {
		case IcVarLocal(Id id, _) -> {
			if(targets.contains(id)) {
				acc.addIfNotContains(id);
			}
		}

		case IcLamb(List<IcPattern> _, IcExp body, _) -> {
			collectRefsHelp(body, targets, acc);
		}

		case IcApp(IcExp fun, List<IcExp> args, _) -> {
			collectRefsHelp(fun, targets, acc);
			args.forEach(arg -> collectRefsHelp(arg, targets, acc));
		}

		case IcIf(IcExp cond, IcExp exp1, IcExp exp2, _) -> {
			collectRefsHelp(cond, targets, acc);
			collectRefsHelp(exp1, targets, acc);
			collectRefsHelp(exp2, targets, acc);
		}

		case IcLet(List<IcValDecl> _, IcExp body, _) -> {
			collectRefsHelp(body, targets, acc);  // 同じ階層の定義のみ TODO: 本当にこれでOK？
		}

		case IcCase(IcExp target, List<IcCaseBranch> branches, _) -> {
			collectRefsHelp(target, targets, acc);
			branches.forEach(branche -> collectRefsHelp(branche.body(), targets, acc));
		}

		case IcCnst _, IcVarForeign _, IcVarCtor _ -> {}
		}
	}

	// 強連結成分分解
	public static List<IdList> sccTopo(IdMap<IdList> graph) {
		IdList postorder = new IdList();
		IdList seen = new IdList();
		for(Id v : graph.keys()) {
			if(!seen.contains(v)) {
				dfs(v, seen, graph, postorder);
			}
		}

		IdMap<IdList> rgraph = new IdMap<>();
		graph.keys().forEach(id -> rgraph.put(id, new IdList()));
		graph.forEach((v, es) -> es.forEach(e -> rgraph.get(e).addIfNotContains(v)));

		seen.clear();
		List<IdList> result = new ArrayList<>();
		for(Id v : postorder.reversed()) {
			if(!seen.contains(v)) {
				IdList scc = new IdList();
				dfs(v, seen, rgraph, scc);
				result.add(scc);
			}
		}
		return result;
	}
	private static void dfs(Id v, IdList seen, IdMap<IdList> graph, IdList acc) {
		if(!seen.contains(v)) {
			seen.add(v);
		}
		for(Id e : graph.get(v)) {
			if(!seen.contains(e)) {
				dfs(e, seen, graph, acc);
			}
		}
		acc.addIfNotContains(v);  // 自己辺が無かったら入れる
	}
}