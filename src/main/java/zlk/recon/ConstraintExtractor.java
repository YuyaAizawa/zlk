package zlk.recon;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

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
		IdMap<IdList> sccs = module.recscc();

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
			new CEqual(RcType.from(value.type()), expected);

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
					args_.state.cons,
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

			// TODO 型注釈から制約を抽出
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
		List<Constraint> headerCons = new ArrayList<>();
		List<Variable> vars = new ArrayList<>();
		IdMap<RcType> funTys = new IdMap<>();
		for(IcValDecl decl : decls) {
			Args args = extractFromArgs(decl.args());
			Variable varFunTy = Variable.unbounded();
			VarN rcVarFunTy = new VarN(varFunTy);

			vars.add(varFunTy);
			funTys.put(decl.id(), args.funTy);
			headerCons.add(new CLet(
					List.of(),
					args.state.vars,
					args.state.headers,
					args.state.cons,
					List.of(new CEqual(rcVarFunTy, args.funTy), extract(decl.body(), args.resultTy))
					));
		}
		return new CLet(
				List.of(),
				vars,
				funTys,
				headerCons,
				bodyCon);

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
	}

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
		State state = new State().add(branch.pattern(), patExpected);
		return new CLet(
				List.of(),
				state.vars,
				state.headers,
				state.cons,
				extract(branch.body(), branchExpected));
	}
}