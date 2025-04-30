package zlk.recon;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import zlk.common.ConstValue;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.idcalc.IcCaseBranch;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcExp.IcApp;
import zlk.idcalc.IcExp.IcCase;
import zlk.idcalc.IcExp.IcCnst;
import zlk.idcalc.IcExp.IcIf;
import zlk.idcalc.IcExp.IcLamb;
import zlk.idcalc.IcExp.IcLet;
import zlk.idcalc.IcExp.IcLetrec;
import zlk.idcalc.IcExp.IcVarCtor;
import zlk.idcalc.IcExp.IcVarForeign;
import zlk.idcalc.IcExp.IcVarLocal;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcPattern;
import zlk.idcalc.IcValDecl;
import zlk.recon.constraint.Constraint;
import zlk.recon.constraint.Constraint.*;
import zlk.recon.constraint.Context;
import zlk.recon.constraint.RcType;
import zlk.recon.constraint.RcType.FunN;
import zlk.recon.constraint.RcType.VarN;
import zlk.recon.constraint.Reason;
import zlk.recon.constraint.State;
import zlk.util.Location;
import zlk.util.Stack;

public final class ConstraintExtractor {

	// TODO ArrayListか何かを継承して，インスタンスメソッドにする

	/**
	 * 指定された式の制約を抽出し，指定されたリストに追加する
	 * @param exp 式
	 * @param expected 期待される型
	 * @param reason 期待される理由
	 * @param acc リスト
	 */
	public static void extract(IcExp exp, RcType expected, Reason reason, List<Constraint> acc) {
		switch (exp) {

		case IcCnst(ConstValue value, Location _) -> {
			acc.add(new CEqual(RcType.from(value.type()), expected, reason));
		}

		case IcVarLocal(Id id, Location _) -> {
			acc.add(new CLocal(id, expected, reason));
		}

		case IcVarForeign(Id id, Type type, Location _) -> {
			acc.add(new CForeign(id, type, expected, reason));
		}

		case IcVarCtor(Id id, Type type, Location _) -> {
			acc.add(new CForeign(id, type, expected, reason));
		}

		case IcLamb(List<IcPattern> args, IcExp body, Location loc) -> {
			Args args_ = extractFromArgs(args);

			List<Constraint> bodyCons = new ArrayList<>();
			extract(body, args_.resultTy, Reason.NO_EXPECTATION, bodyCons);

			exists(
					args_.vars,
					List.of(
							new CLet(
									List.of(),
									args_.state.vars,
									args_.state.headers,
									args_.state.cons,
									bodyCons),
							new CEqual(args_.funTy, expected, reason)),
					acc);
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

			Optional<Id> funName = fun.getName();

			Variable funVar = Variable.unbounded();
			vars.add(funVar);
			RcType funTy = new VarN(funVar);
			extract(fun, funTy, Reason.NO_EXPECTATION, cons);

			List<Constraint> argCons = new ArrayList<>();
			Stack<RcType> argTys = new Stack<>();
			for(IcExp arg : args) {
				Variable argVar = Variable.unbounded();
				vars.add(argVar);
				RcType argTy = new VarN(argVar);
				Reason reason_ = new Reason.FromContext(
						new Context.CallArg(funName, argTys.size()),
						fun.loc());
				extract(arg, argTy, reason_, argCons);
				argTys.push(argTy);
			}

			Variable resultVar = Variable.unbounded();
			vars.add(resultVar);
			RcType resultType = new VarN(resultVar);
			RcType arityType = resultType;
			for(RcType ty : argTys) {
				arityType = new FunN(ty, arityType);
			}

			Reason reason_ = new Reason.FromContext(
					new Context.CallArity(funName, args.size()), fun.loc());
			cons.add(new CEqual(funTy, arityType, reason_));
			cons.addAll(argCons);  // アリティの後にしないと引数の数のチェックができない
			cons.add(new CEqual(resultType, expected, reason));

			exists(vars, cons, acc);
		}

		case IcIf(IcExp condExp, IcExp thenExp, IcExp elseExp, Location _) -> {
			Reason boolExpect = new Reason.FromContext(Context.IF_CONDITION, condExp.loc());

			if(reason instanceof Reason.FromAnnotation anno) {
				// TODO 正しいreasonをつける
				extract(condExp, RcType.BOOL, boolExpect, acc);
				extract(thenExp, RcType.from(anno.type()), reason, acc);
				extract(elseExp, RcType.from(anno.type()), reason, acc);
			} else {
				Variable branchVar = Variable.unbounded();
				RcType branchTy = new VarN(branchVar);

				List<Constraint> cons = new ArrayList<>();
				// TODO 正しいreasonをつける
				extract(thenExp, branchTy, Reason.NO_EXPECTATION, cons);
				extract(elseExp, branchTy, Reason.NO_EXPECTATION, cons);

				exists(List.of(branchVar), cons, acc);
			}
		}

		case IcLet(IcValDecl decl, IcExp body, Location _) -> {
			List<Constraint> bodyCons = new ArrayList<>();
			extract(body, expected, reason, bodyCons);
			extractFromDef(decl, bodyCons, acc);
		}

		case IcLetrec(List<IcValDecl> decls, IcExp body, Location _) -> {
			List<Constraint> bodyCons = new ArrayList<>();
			extract(body, expected, reason, bodyCons);
			extractFromRecDef(decls, bodyCons, acc);
		}
		case IcCase(IcExp target, List<IcCaseBranch> branches, Location _) -> {
			Variable patVar = Variable.unbounded();
			RcType patTy = new VarN(patVar);
			List<Constraint> cons = new ArrayList<>();
			extract(target, patTy, Reason.NO_EXPECTATION, cons);

			// TODO 型注釈から制約を抽出
			Variable branchVar = Variable.unbounded();
			RcType branchTy = new VarN(branchVar);

			for(IcCaseBranch branch : branches) {
				extractFromCaseBranch(branch, patTy, branchTy, Reason.NO_EXPECTATION, cons);  // TODO Reason系
			}
			cons.add(new CEqual(branchTy, expected, reason));

			exists(List.of(patVar, branchVar), cons, acc);
		}
		};
	}

//	record State(
//			IdMap<RcType> headers,
//			List<Variable> vars,
//			Stack<Constraint> cons) {
//		State() {
//			this(new IdMap<>(), new ArrayList<>(), new Stack<>());
//		}
//	}

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

	public static Constraint extract(IcModule module) {
		List<Constraint> cons = new ArrayList<>();
		for(IcValDecl decl : module.decls()) {
			extractFromDef(decl, List.of(), cons);  // TODO 関数の依存関係とか再帰とか
		}
		return new CLet(List.of(), List.of(), IdMap.of(), cons, List.of());  // 組込み関数とか
	}

	public static void extractFromDef(IcValDecl decl, List<Constraint> bodyCons, List<Constraint> acc) {
		Args args = extractFromArgs(decl.args());

		List<Constraint> exprCons = new ArrayList<>();
		extract(decl.body(), args.resultTy, Reason.NO_EXPECTATION, exprCons);

		acc.add(new CLet(
				List.of(),
				args.vars,
				IdMap.of(decl.id(), args.funTy),
				List.of(new CLet(
						List.of(),
						args.state.vars,
						args.state.headers,
						args.state.cons,
						exprCons)),
				bodyCons));
	}

	public static void extractFromRecDef(List<IcValDecl> defs, List<Constraint> bodyCon, List<Constraint> acc) {
//		return recDefsHelp(defs, bodyCon, new Info(), new Info());
	}
//	private static Constraint recDefsHelp(Rtv rtv, List<IcFunDecl> defs, Constraint bodyCon,
//			Info ridgedInfo, Info flexInfo) {
//
//		if(defs.isEmpty()) {
//			return new CLet(ridgedInfo.vars, List.of(), ridgedInfo.headers, new CTrue(),
//					new CLet(List.of(), flexInfo.vars, flexInfo.headers, new CLet(List.of(), List.of(), flexInfo.headers, new CTrue(), new CAnd(flexInfo.cons)),
//							new CAnd(List.of(new CAnd(ridgedInfo.cons), bodyCon))));
//		}
//		IcFunDecl def = defs.get(0);
//		List<IcFunDecl> otherDefs = defs.subList(1, defs.size());
//
//		Args args = argsHelper(def.args(), new State(flexInfo.vars));
//
//		Constraint exprCon =
//				extract(rtv, def.body(), args.resultType);
//
//		Constraint defCon =
//				new CLet(
//						List.of(),
//						args.state.vars.getAllAsList(),
//						args.state.headers,
//						new CAnd(args.state.cons),
//						exprCon);
//
//		List<Constraint> cons = new ArrayList<>(flexInfo.cons);
//		cons.add(0, defCon);
//		IdMap<RcType> headers = flexInfo.headers.clone();
//		headers.put(def.id(), args.type);
//		return recDefsHelp(rtv, otherDefs, bodyCon, ridgedInfo,
//				new Info(
//						args.vars.getAllAsList(),
//						cons,
//						headers));
//	}

	/**
	 * 指定したリストに，自由型変数を前提として制約を解く，という制約を追加する．
	 * varを存在型として，これを前提に制約を解くことでスコープ内部のみの制約を実現できる．
	 * @param var 自由型変数
	 * @param cons 本体の制約
	 * @param acc 追加先
	 */
	public static void exists(List<Variable> var, List<Constraint> cons, List<Constraint> acc) {
		acc.add(new CExists(var, acc));
	}

	public static Args extractFromArgs(List<IcPattern> args) {
		List<Variable> vars = new ArrayList<>();
		State state = new State();
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

	private static void extractFromCaseBranch(IcCaseBranch branch, RcType patExpected, RcType branchExpected, Reason branchReason, List<Constraint> acc) {
		State state = new State().add(branch.pattern(), patExpected);
		List<Constraint> branchCons = new ArrayList<>();
		extract(branch.body(), branchExpected, branchReason, branchCons);
		acc.add(new CLet(
				List.of(),
				state.vars,
				state.headers,
				state.cons,
				branchCons));
	}
}