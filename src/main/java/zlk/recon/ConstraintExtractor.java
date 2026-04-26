package zlk.recon;

import java.util.IdentityHashMap;

import zlk.common.ConstValue;
import zlk.common.Location;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.idcalc.ExpOrPattern;
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
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

public final class ConstraintExtractor {

	private FreshFlex freshFlex;
	private IdMap<Seq<Id>> letDependers; // dependee -> dependers
	private IdentityHashMap<ExpOrPattern, RcType> nodeTypes;

	private ConstraintExtractor(IdMap<Seq<Id>> dependers, FreshFlex freshFlex) {
		this.letDependers = dependers;
		this.freshFlex = freshFlex;
		this.nodeTypes = new IdentityHashMap<>();
	}

	public static Result extract(IcModule module, FreshFlex freshFlex) {
		ConstraintExtractor extractor = new ConstraintExtractor(
				LetDependencyExtractor.extract(module),
				freshFlex
		);
		Constraint constraint = extractor.extractFromDef(
				module.decls(),
				new CExists(  // TODO: main関数のletにする
						Seq.of(),
						Seq.of()));
		return new Result(constraint, extractor.nodeTypes);
	}

	public record Result(
			Constraint constraint,
			IdentityHashMap<ExpOrPattern, RcType> nodeTypes) {

		public IdentityHashMap<ExpOrPattern, Type> resolvedNodeTypes() {
			IdentityHashMap<ExpOrPattern, Type> result = new IdentityHashMap<>();
			nodeTypes.forEach((node, rcType) -> result.put(node, rcType.toType()));
			return result;
		}
	}

	/**
	 * 指定された式の制約を抽出して返す．
	 * @param exp 式
	 * @param expected 期待される型
	 */
	public Constraint extract(IcExp exp, RcType expected) {
		nodeTypes.put(exp, expected);
		return switch (exp) {

		case IcCnst(ConstValue value, Location _) ->
			new CEqual(RcType.from(value.type(), freshFlex).resultTy(), expected);

		case IcVarLocal(Id id, Location _) ->
			new CLocal(id, expected);

		case IcVarForeign(Id id, Type type, Location _) ->
			new CForeign(id, type, expected);

		case IcVarCtor(Id id, Type type, Location _) ->
			new CForeign(id, type, expected);

		case IcLamb(Seq<IcPattern> args, IcExp body, Location _) -> {
			Args args_ = extractFromArgs(args);

			Seq<Constraint> argsCons = Seq.of(
				new CLet(
						Seq.of(),
						args_.binder.vars.toSeq(),
						args_.binder.headers,
						Seq.of(new CPhase(args_.binder.cons.toSeq(), Seq.of())),  // argsは一般化対象でない
						extract(body, args_.resultTy)),
				new CEqual(args_.funTy, expected));
			yield new CExists(args_.vars, argsCons);
		}

		case IcApp(IcExp fun, Seq<IcExp> args, Location _) -> {
			// f a b c : R
			// f : F
			// a : A
			// b : B
			// c : C
			// F = A -> B -> C -> R

			SeqBuffer<Variable> vars = new SeqBuffer<>();
			SeqBuffer<Constraint> cons = new SeqBuffer<>();

			Variable funVar = freshFlex.getVariable();
			vars.add(funVar);
			RcType funTy = new VarN(funVar);
			cons.add(extract(fun, funTy));

			SeqBuffer<Constraint> argCons = new SeqBuffer<>();
			SeqBuffer<RcType> argTys = new SeqBuffer<>();
			for(IcExp arg : args) {
				Variable argVar = freshFlex.getVariable();
				vars.add(argVar);
				RcType argTy = new VarN(argVar);
				argCons.add(extract(arg, argTy));
				argTys.add(argTy);
			}

			Variable resultVar = freshFlex.getVariable();
			vars.add(resultVar);
			RcType resultType = new VarN(resultVar);
			RcType arityType = resultType;
			for(RcType ty : argTys.toSeq().reversed()) {
				arityType = new FunN(ty, arityType);
			}

			cons.add(new CEqual(funTy, arityType));
			cons.addAll(argCons);  // アリティの後にしないと引数の数のチェックができない
			cons.add(new CEqual(resultType, expected));

			yield new CExists(vars.toSeq(), cons.toSeq());
		}

		case IcIf(IcExp condExp, IcExp thenExp, IcExp elseExp, Location _) -> {
			// TODO expectedに型注釈を入れたら展開して対応させる

			Variable branchVar = freshFlex.getVariable();
			RcType branchTy = new VarN(branchVar);

			yield new CExists(
					Seq.of(branchVar),
					Seq.of(extract(condExp, RcType.BOOL),
							extract(thenExp, branchTy),
							extract(elseExp, branchTy),
							new CEqual(branchTy, expected)));
		}

		case IcLet(Seq<IcValDecl> decls, IcExp body, Location _) ->
			extractFromDef(decls, extract(body, expected));

		case IcCase(IcExp target, Seq<IcCaseBranch> branches, Location _) -> {
			SeqBuffer<Constraint> cons = new SeqBuffer<>();

			Variable patVar = freshFlex.getVariable();
			RcType patTy = new VarN(patVar);
			cons.add(extract(target, patTy));

			// TODO 型注釈から制約を抽出（ここに制約って書けたっけ？）
			Variable branchVar = freshFlex.getVariable();
			RcType branchTy = new VarN(branchVar);
			for(IcCaseBranch branch : branches) {
				cons.add(extractFromCaseBranch(branch, patTy, branchTy));  // TODO Reason系
			}
			cons.add(new CEqual(branchTy, expected));

			yield new CExists(Seq.of(patVar, branchVar), cons.toSeq());
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
			Seq<Variable> vars,
			RcType funTy,
			RcType resultTy,
			PatternBinder binder) {}

	public CLet extractFromDef(Seq<IcValDecl> decls, Constraint bodyCon) {
		// 0) 先に “外へ出る器 = アンカー” を全部配る（別オブジェクト！）
		IdMap<RcType> header = new IdMap<>();
		for (var decl : decls) {
			header.put(decl.id(), new VarN(freshFlex.getVariable())); // アンカー
		}

		// 1) 各 def の rhs ブロックを作る（引数はこの内側CLetのheader）
		// id -> rhsConstraint
		IdMap<Constraint> defCons = new IdMap<>();
		for (var decl : decls) {
			Args a = extractFromArgs(decl.args());

			SeqBuffer<Constraint> headerCons = new SeqBuffer<>();
			headerCons.addAll(a.binder.cons);
			headerCons.add(extract(decl.body(), a.resultTy));

			Constraint rhs = new CLet(
					Seq.of(), // TODO: 型注釈のrigid
					a.binder.vars.toSeq(),
					a.binder.headers,
					Seq.of(new CPhase(headerCons.toSeq(), Seq.of())),  // 内側CLetは一般化なし
					new CEqual(a.funTy, header.get(decl.id()))
			);
			defCons.put(decl.id(), rhs);
		}

		// 2) 同フレーム内の依存グラフ→SCC分解→トポ順
		Seq<Seq<Id>> sccTopo = sccTopo(buildGraph(decls));

		// 3) フェーズ列を作る SCCごとに rhs を並べtargets=SCCのid群
		Seq<CPhase> phases = sccTopo.map(
				scc -> new CPhase(scc.map(defCons::get), scc));

		// 4) 外側の CLet にまとめる（実際の let フレーム）
		return new CLet(
				Seq.of(),
				Seq.of(),
				header,
				phases,
				bodyCon);
	}

	public Args extractFromArgs(Seq<IcPattern> args) {
		PatternBinder pb = new PatternBinder(nodeTypes);

		// 引数型に変数を割当て
		SeqBuffer<RcType> argTys = new SeqBuffer<>(args.size());
		for(IcPattern arg : args) {
			Variable v = freshFlex.getVariable();
			RcType.VarN ty = new RcType.VarN(v);
			pb.vars.add(v);  // パターン内と関数のアリティ由来を分けるならpb.varsにpushせず外側で保持
			pb.bind(arg, ty, freshFlex);
			argTys.add(ty);
		}

		// 戻り値型に変数を割当て
		Variable v = freshFlex.getVariable();
		RcType.VarN retTy = new RcType.VarN(v);
		pb.vars.add(v);

		RcType funTy = retTy;
		for(RcType argTy : argTys.toSeq().reversed()) {
			funTy = new RcType.FunN(argTy, funTy);
		}

		return new Args(
				pb.vars.toSeq(),
				funTy,
				retTy,
				pb
		);
	}

	private Constraint extractFromCaseBranch(IcCaseBranch branch, RcType patExpected, RcType branchExpected) {
		PatternBinder pb = new PatternBinder(nodeTypes);
		pb.bind(branch.pattern(), patExpected, freshFlex);
		Constraint bodyCon = extract(branch.body(), branchExpected);

		SeqBuffer<Constraint> cons = new SeqBuffer<>(pb.cons.size()+1);
		cons.addAll(pb.cons);
		cons.add(bodyCon);
		return new CLet(
				Seq.of(),
				pb.vars.toSeq(),
				pb.headers,
				Seq.of(new CPhase(cons.toSeq(), Seq.of())),  // case branchは一般化する対象なし
				new CEqual(branchExpected, branchExpected)  // TODO: 特に制約がないことを表せた方が良いか？
		);
	}

	// let内での依存を関係を取得する
	public IdMap<Seq<Id>> buildGraph(Seq<IcValDecl> decls) {
		// 全体の依存関係から関係するものを抽出する
		Seq<Id> targets = decls.map(IcValDecl::id);
		IdMap<Seq<Id>> result = new IdMap<>();
		for(Id target : targets) {
			result.put(target, letDependers.get(target).filter(targets::contains));
		}
		return result;
	}

	// 強連結成分分解 Kosaraju-Sharir's algorithm
	public static Seq<Seq<Id>> sccTopo(IdMap<Seq<Id>> graph) {
		SeqBuffer<Id> postorder = new SeqBuffer<>();
		SeqBuffer<Id> seen = new SeqBuffer<>();
		for(Id v : graph.keys()) {
			if(!seen.contains(v)) {
				dfs(v, seen, graph, postorder);
			}
		}

		IdMap<SeqBuffer<Id>> rgraphBuffer = new IdMap<>();
		graph.keys().forEach(id -> rgraphBuffer.put(id, new SeqBuffer<>()));
		graph.forEach((v, es) -> es.forEach(e -> rgraphBuffer.get(e).addIfNotContains(v)));
		IdMap<Seq<Id>> rgraph = rgraphBuffer.traverse(SeqBuffer::toSeq);

		seen = new SeqBuffer<>(seen.size());
		SeqBuffer<Seq<Id>> result = new SeqBuffer<>();
		for(Id v : postorder.toSeq().reversed()) {
			if(!seen.contains(v)) {
				SeqBuffer<Id> scc = new SeqBuffer<>();
				dfs(v, seen, rgraph, scc);
				result.add(scc.toSeq());
			}
		}
		return result.toSeq();
	}
	private static void dfs(Id v, SeqBuffer<Id> seen, IdMap<Seq<Id>> graph, SeqBuffer<Id> acc) {
		if(seen.contains(v)) {
			return;
		}
		seen.add(v);
		for(Id e : graph.get(v)) {
			dfs(e, seen, graph, acc);
		}
		acc.addIfNotContains(v);
	}
}
