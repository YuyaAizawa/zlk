package zlk.recon;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import zlk.common.ConstValue;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.idcalc.IcCaseBranch;
import zlk.idcalc.IcFunDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcExp.IcAbs;
import zlk.idcalc.IcExp.IcApp;
import zlk.idcalc.IcExp.IcCase;
import zlk.idcalc.IcExp.IcCnst;
import zlk.idcalc.IcExp.IcIf;
import zlk.idcalc.IcExp.IcLet;
import zlk.idcalc.IcExp.IcLetrec;
import zlk.idcalc.IcExp.IcVarCtor;
import zlk.idcalc.IcExp.IcVarForeign;
import zlk.idcalc.IcExp.IcVarLocal;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcPattern;
import zlk.recon.constraint.Constraint;
import zlk.recon.constraint.Constraint.CAnd;
import zlk.recon.constraint.Constraint.CEqual;
import zlk.recon.constraint.Constraint.CForeign;
import zlk.recon.constraint.Constraint.CLet;
import zlk.recon.constraint.Constraint.CLocal;
import zlk.recon.constraint.Constraint.CSaveTheEnvironment;
import zlk.recon.constraint.Constraint.CTrue;
import zlk.recon.constraint.RcType;
import zlk.recon.constraint.State;
import zlk.recon.constraint.RcType.FunN;
import zlk.recon.constraint.RcType.VarN;
import zlk.util.Location;
import zlk.util.Stack;

public final class ConstraintExtractor {

	public static Constraint extract(Rtv rtv, IcExp exp, RcType expected) {
		return switch (exp) {
		case IcCnst(ConstValue value, Location _) -> {
			yield new CEqual(RcType.from(value.type()), expected);
		}
		case IcVarLocal(Id id, Location _) -> {
			yield new CLocal(id, expected);
		}
		case IcVarForeign(Id id, Type type, Location _) -> {
			yield new CForeign(id, type, expected);
		}
		case IcVarCtor(Id id, Type type, Location _) -> {
			yield new CForeign(id, type, expected);
		}
		case IcAbs(Id id, Type _, IcExp body, Location loc) -> {
			Args args = extractFromArgs(List.of(new IcPattern.Var(id, loc)));
			Constraint bodyCon = extract(rtv, body, args.type);
			yield exists(args.vars.getAllAsList(),
					new CAnd(List.of(
							new CLet(
									List.of(),
									args.state.vars.getAllAsList(),
									args.state.headers,
									new CTrue(),
									bodyCon),
							new CEqual(args.type, expected)
			)));
		}
		case IcApp(IcExp fun, List<IcExp> args, Location _) -> {
			Variable funcVar = Variable.mkFlexVar();
			Variable resultVar = Variable.mkFlexVar();
			RcType funcTy = new VarN(funcVar);
			RcType resultTy = new VarN(resultVar);

			Constraint funcCon = extract(rtv, fun, funcTy);

			List<Variable> vars = new ArrayList<>();
			Stack<RcType> argTys = new Stack<>();
			List<Constraint> argCons = new ArrayList<>();
			for(IcExp arg : args) {
				Variable argVar = Variable.mkFlexVar();
				RcType argTy = new VarN(argVar);
				Constraint argCon = extract(rtv, arg, argTy);
				vars.add(argVar);
				argTys.push(argTy);
				argCons.add(argCon);
			}

			RcType arityType = resultTy;
			for(RcType ty : argTys) {
				arityType = new FunN(ty, arityType);
			}

			vars.add(resultVar);
			vars.add(funcVar);

			yield exists(vars, new CAnd(List.of(
					funcCon,
					new CEqual(funcTy, arityType),
					new CAnd(argCons),
					new CEqual(resultTy, expected))));
		}
		case IcIf(IcExp cond, IcExp thenExp, IcExp elseExp, Location _) -> {
			Constraint condCon = extract(rtv, cond, RcType.BOOL);
			Variable branchVar = Variable.mkFlexVar();
			RcType branchTy = new VarN(branchVar);
			Constraint thenCon = extract(rtv, thenExp, branchTy);
			Constraint elseCon = extract(rtv, elseExp, branchTy);
			yield exists(List.of(branchVar),
					new CAnd(List.of(
							condCon,
							thenCon,
							elseCon,
							new CEqual(branchTy, expected))));
		}
		case IcLet(IcFunDecl decl, IcExp body, Location _) -> {
			// TODO 型注釈から制約を抽出
			Constraint bodyCons = extract(rtv, body, expected);
			yield extractFromDef(rtv, decl, bodyCons);
		}
		case IcLetrec(List<IcFunDecl> decls, IcExp body, Location _) -> {
			// TODO 型注釈から制約を抽出
			Constraint bodyCons = extract(rtv, body, expected);
			yield extractFromRecursiveDef(rtv, decls, bodyCons);
		}
		case IcCase(IcExp target, List<IcCaseBranch> branches, Location _) -> {
			Variable patVar = Variable.mkFlexVar();
			RcType patTy = new VarN(patVar);
			Constraint targetCon = extract(rtv, target, patTy);

			// TODO 型注釈から制約を抽出

			Variable branchVar = Variable.mkFlexVar();
			RcType branchTy = new VarN(branchVar);

			List<Constraint> branchCons = branches.stream()
					.map(branch -> extractFromCaseBranch(rtv, branch, patTy, branchTy))
					.toList();
			yield exists(
					List.of(patVar, branchVar),
					new CAnd(List.of(
							targetCon,
							new CAnd(branchCons),
							new CEqual(branchTy, expected))));
		}
		};
	}

	public static Constraint extract(IcModule module) {
		return extract(module.decls().iterator());
	}

	private static Constraint extract(Iterator<IcFunDecl> i) {
		if(i.hasNext()) {
			IcFunDecl decl = i.next();
			return decl.recs().map(decls -> {
				List<IcFunDecl> decls_ = new ArrayList<>(decls);
				decls_.add(0, decl);
				return extractFromRecursiveDef(new Rtv(), decls_, extract(i));
			}).orElseGet(() -> extractFromDef(new Rtv(), decl, extract(i)));
		} else {
			return new CSaveTheEnvironment();
		}
	}

	public static Constraint extractFromDef(Rtv rtv, IcFunDecl decl, Constraint bodyCon) {
		Args args = extractFromArgs(decl.args());

		List<Variable> vars = new ArrayList<>();
		for(Variable v : args.vars) {
			vars.add(v);
		}

		List<Variable> pvars = new ArrayList<>();
		for(Variable v : args.state.vars) {
			pvars.add(v);
		}

		Constraint exprCon = extract(rtv, decl.body(), args.resultType);
		return new CLet(
				List.of(),
				vars,
				IdMap.of(decl.id(), args.type),
				new CLet(
						List.of(),
						pvars,
						args.state.headers,
						new CAnd(args.state.cons),
						exprCon),
				bodyCon);
	}

	public static Constraint extractFromRecursiveDef(Rtv rtv, List<IcFunDecl> defs, Constraint bodyCon) {
		return recDefsHelp(rtv, defs, bodyCon, new Info(), new Info());
	}
	private static Constraint recDefsHelp(Rtv rtv, List<IcFunDecl> defs, Constraint bodyCon,
			Info ridgedInfo, Info flexInfo) {

		if(defs.isEmpty()) {
			return new CLet(ridgedInfo.vars, List.of(), ridgedInfo.headers, new CTrue(),
					new CLet(List.of(), flexInfo.vars, flexInfo.headers, new CLet(List.of(), List.of(), flexInfo.headers, new CTrue(), new CAnd(flexInfo.cons)),
							new CAnd(List.of(new CAnd(ridgedInfo.cons), bodyCon))));
		}
		IcFunDecl def = defs.get(0);
		List<IcFunDecl> otherDefs = defs.subList(1, defs.size());

		Args args = argsHelper(def.args(), new State(flexInfo.vars));

		Constraint exprCon =
				extract(rtv, def.body(), args.resultType);

		Constraint defCon =
				new CLet(
						List.of(),
						args.state.vars.getAllAsList(),
						args.state.headers,
						new CAnd(args.state.cons),
						exprCon);

		List<Constraint> cons = new ArrayList<>(flexInfo.cons);
		cons.add(0, defCon);
		IdMap<RcType> headers = flexInfo.headers.clone();
		headers.put(def.id(), args.type);
		return recDefsHelp(rtv, otherDefs, bodyCon, ridgedInfo,
				new Info(
						args.vars.getAllAsList(),
						cons,
						headers));
	}

	public static Constraint exists(List<Variable> flexes, Constraint constraint) {
		return new CLet(List.of(), flexes, IdMap.of(), constraint, new CTrue());
	}

	public static Args extractFromArgs(List<IcPattern> args) {
		// f a b c = ... に対して
		// var:[A, B, C]
		// type:A->B->C->D
		// resultType:D
		// headers:[a:A, b:B, c:C] <- ここstateにした
		// を返す

		return argsHelper(args, new State());
	}
	private static Args argsHelper(List<IcPattern> args, State state) {
		if(args.isEmpty()) {
			Variable resultVar = Variable.mkFlexVar();
			RcType resultType = new VarN(resultVar);
			Stack<Variable> stack = new Stack<>();
			stack.push(resultVar);
			return new Args(stack, resultType, resultType, state);
		} else {
			IcPattern pattern = args.get(0);
			List<IcPattern> otherArgs = args.subList(1, args.size());
			Variable argVar = Variable.mkFlexVar();
			RcType argType = new VarN(argVar);
			Args args_ = argsHelper(otherArgs, state.add(pattern, argType));
			args_.vars.push(argVar);
			return new Args(args_.vars, (new FunN(argType, args_.type)), args_.resultType, args_.state);
		}
	}

	private static Constraint extractFromCaseBranch(Rtv rtv, IcCaseBranch branch, RcType patExpected, RcType branchExpected) {
		State state = new State().add(branch.pattern(), patExpected);
		return new CLet(
				List.of(),
				state.vars.getAllAsList(),
				state.headers,
				new CAnd(state.cons),
				extract(rtv, branch.body(), branchExpected));
	}



	private static class Rtv {

	}

	private record Info(
			List<Variable> vars,
			List<Constraint> cons,
			IdMap<RcType> headers
	) {
		public Info() {
			this(List.of(), List.of(), new IdMap<>());
		}
	}

	private record Args(
			Stack<Variable> vars,
			RcType type,
			RcType resultType,
			State state) {}
}
