package zlk.recon;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import zlk.common.ConstValue;
import zlk.common.id.IdMap;
import zlk.idcalc.IcCaseBranch;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcPVar;
import zlk.idcalc.IcPattern;
import zlk.recon.constraint.Constraint;
import zlk.recon.constraint.Type;
import zlk.recon.constraint.Type.FunN;
import zlk.recon.constraint.Type.VarN;
import zlk.recon.constraint.pattern.State;
import zlk.util.Stack;

public final class ConstraintExtractor {

	public static Constraint extract(Rtv rtv, IcExp exp, Type expected) {
		return exp.fold(
				cnst -> Constraint.equal(
						switch (cnst.value()) {
						case ConstValue.Bool _ -> Type.BOOL;
						case ConstValue.I32 _ -> Type.I32;
				}, expected),

				var -> Constraint.local(var.id(), expected),

				foreign -> Constraint.foreign(foreign.id(), foreign.type(), expected),

				ctor -> Constraint.foreign(ctor.id(), ctor.type(), expected),

				abs -> {
					Args args = extractFromArgs(List.of(new IcPVar(abs.id(), abs.loc())));
					Constraint bodyCon = extract(rtv, abs.body(), args.type);
					return exists(args.vars.getAllAsList(),
							Constraint.and(List.of(
									Constraint.let(
											List.of(),
											args.state.vars.getAllAsList(),
											args.state.headers,
											Constraint.ctrue(),
											bodyCon),
									Constraint.equal(args.type, expected)
					)));
				},

				app -> {
					Variable funcVar = Variable.mkFlexVar();
					Variable resultVar = Variable.mkFlexVar();
					Type funcTy = new VarN(funcVar);
					Type resultTy = new VarN(resultVar);

					Constraint funcCon = extract(rtv, app.fun(), funcTy);

					List<Variable> vars = new ArrayList<>();
					Stack<Type> argTys = new Stack<>();
					List<Constraint> argCons = new ArrayList<>();
					for(IcExp arg : app.args()) {
						Variable argVar = Variable.mkFlexVar();
						Type argTy = new VarN(argVar);
						Constraint argCon = extract(rtv, arg, argTy);
						vars.add(argVar);
						argTys.push(argTy);
						argCons.add(argCon);
					}

					Type arityType = resultTy;
					for(Type ty : argTys) {
						arityType = new FunN(ty, arityType);
					}

					vars.add(resultVar);
					vars.add(funcVar);

					return exists(vars, Constraint.and(List.of(
							funcCon,
							Constraint.equal(funcTy, arityType),
							Constraint.and(argCons),
							Constraint.equal(resultTy, expected))));
				},
				if_ -> {
					Constraint condCon = extract(rtv, if_.cond(), Type.BOOL);
					Variable branchVar = Variable.mkFlexVar();
					Type branchTy = new VarN(branchVar);
					Constraint thenCon = extract(rtv, if_.exp1(), branchTy);
					Constraint elseCon = extract(rtv, if_.exp2(), branchTy);
					return exists(List.of(branchVar),
							Constraint.and(List.of(
									condCon,
									thenCon,
									elseCon,
									Constraint.equal(branchTy, expected))));
				},
				let -> {
					// TODO 型注釈から制約を抽出
					Constraint bodyCons = extract(rtv, let.body(), expected);
					return extractFromDef(rtv, let.decl(), bodyCons);
				},
				letrec -> {
					// TODO 型注釈から制約を抽出
					Constraint bodyCons = extract(rtv, letrec.body(), expected);
					return extractFromRecursiveDef(rtv, letrec.decls(), bodyCons);
				},
				case_ -> {
					Variable patVar = Variable.mkFlexVar();
					Type patTy = new VarN(patVar);
					Constraint targetCon = extract(rtv, case_.target(), patTy);

					// TODO 型注釈から制約を抽出

					Variable branchVar = Variable.mkFlexVar();
					Type branchTy = new VarN(branchVar);

					List<Constraint> branchCons = new ArrayList<>();
					for (int i = 0; i < case_.branches().size(); i++) {
						branchCons.add(extractFromCaseBranch(rtv, case_.branches().get(i),
								patTy, branchTy));
					}
					return exists(
							List.of(patVar, branchVar),
							Constraint.and(List.of(
									targetCon,
									Constraint.and(branchCons),
									Constraint.equal(branchTy, expected))));
				});
	}

	public static Constraint extract(IcModule module) {
		return extract(module.decls().iterator());
	}

	private static Constraint extract(Iterator<IcDecl> i) {
		if(i.hasNext()) {
			IcDecl decl = i.next();
			return decl.recs().map(decls -> {
				List<IcDecl> decls_ = new ArrayList<>(decls);
				decls_.add(0, decl);
				return extractFromRecursiveDef(new Rtv(), decls_, extract(i));
			}).orElseGet(() -> extractFromDef(new Rtv(), decl, extract(i)));
		} else {
			return Constraint.saveTheEnvironment();
		}
	}

	public static Constraint extractFromDef(Rtv rtv, IcDecl decl, Constraint bodyCon) {
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
		return Constraint.let(
				List.of(),
				vars,
				IdMap.of(decl.id(), args.type),
				Constraint.let(
						List.of(),
						pvars,
						args.state.headers,
						Constraint.and(args.state.cons),
						exprCon),
				bodyCon);
	}

	public static Constraint extractFromRecursiveDef(Rtv rtv, List<IcDecl> defs, Constraint bodyCon) {
		return recDefsHelp(rtv, defs, bodyCon, new Info(), new Info());
	}
	private static Constraint recDefsHelp(Rtv rtv, List<IcDecl> defs, Constraint bodyCon,
			Info ridgedInfo, Info flexInfo) {

		if(defs.isEmpty()) {
			return Constraint.let(ridgedInfo.vars, List.of(), ridgedInfo.headers, Constraint.ctrue(),
					Constraint.let(List.of(), flexInfo.vars, flexInfo.headers, Constraint.let(List.of(), List.of(), flexInfo.headers, Constraint.ctrue(), Constraint.and(flexInfo.cons)),
							Constraint.and(List.of(Constraint.and(ridgedInfo.cons), bodyCon))));
		}
		IcDecl def = defs.get(0);
		List<IcDecl> otherDefs = defs.subList(1, defs.size());

		Args args = argsHelper(def.args(), new State(flexInfo.vars));

		Constraint exprCon =
				extract(rtv, def.body(), args.resultType);

		Constraint defCon =
				Constraint.let(
						List.of(),
						args.state.vars.getAllAsList(),
						args.state.headers,
						Constraint.and(args.state.cons),
						exprCon);

		List<Constraint> cons = new ArrayList<>(flexInfo.cons);
		cons.add(0, defCon);
		IdMap<Type> headers = flexInfo.headers.clone();
		headers.put(def.id(), args.type);
		return recDefsHelp(rtv, otherDefs, bodyCon, ridgedInfo,
				new Info(
						args.vars.getAllAsList(),
						cons,
						headers));
	}

	public static Constraint exists(List<Variable> flexes, Constraint constraint) {
		return Constraint.let(List.of(), flexes, IdMap.of(), constraint, Constraint.ctrue());
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
			Type resultType = new VarN(resultVar);
			Stack<Variable> stack = new Stack<>();
			stack.push(resultVar);
			return new Args(stack, resultType, resultType, state);
		} else {
			IcPattern pattern = args.get(0);
			List<IcPattern> otherArgs = args.subList(1, args.size());
			Variable argVar = Variable.mkFlexVar();
			Type argType = new VarN(argVar);
			Args args_ = argsHelper(otherArgs, state.add(pattern, argType));
			args_.vars.push(argVar);
			return new Args(args_.vars, (new FunN(argType, args_.type)), args_.resultType, args_.state);
		}
	}

	private static Constraint extractFromCaseBranch(Rtv rtv, IcCaseBranch branch, Type patExpected, Type branchExpected) {
		State state = new State().add(branch.pattern(), patExpected);
		return Constraint.let(
				List.of(),
				state.vars.getAllAsList(),
				state.headers,
				Constraint.and(state.cons),
				extract(rtv, branch.body(), branchExpected));
	}



	private static class Rtv {

	}

	private record Info(
			List<Variable> vars,
			List<Constraint> cons,
			IdMap<Type> headers
	) {
		public Info() {
			this(List.of(), List.of(), new IdMap<>());
		}
	}

	private record Args(
			Stack<Variable> vars,
			Type type,
			Type resultType,
			State state) {}
}
