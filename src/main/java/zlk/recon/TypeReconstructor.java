package zlk.recon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.recon.constraint.Constraint;
import zlk.recon.constraint.Content;
import zlk.recon.constraint.FlatType;
import zlk.recon.constraint.Constraint.Category;
import zlk.recon.constraint.Content.FlexVar;
import zlk.recon.constraint.Content.Structure;
import zlk.recon.constraint.Type.AppN;
import zlk.recon.constraint.Type.FunN;
import zlk.recon.constraint.Type.VarN;
import zlk.recon.constraint.IdVar;
import zlk.util.Stack;

// unifyがList<Variable>に値を入れるのはレコードのときだけ
public class TypeReconstructor {

	private Pool pool = new Pool(8);
	private IdMap<Type> reconed = null;

	private static record State(
			IdMap<Variable> env,
			int mark) {
		static State empty() {
			return new State(IdMap.of(), Variable.NO_MARK + 1);
		}
		State saveTheEnvironment(IdMap<Variable> env) {
			return new State(env, this.mark);
		}
		void occurs(Id id, Variable var) {
			if(var.occurs()) {
				throw new RuntimeException("infinite type. id:"+id+", var:"+var);
			}
		}
	}

	public IdMap<Type> run(Constraint constraint) {
		reconed = new IdMap<>();
		solve(new IdMap<>(), Variable.OUTERMOST_RANK, State.empty(), constraint);
		return reconed;
	}

	public State solve(IdMap<Variable> env, int rank, State state, Constraint constraint) {
		return constraint.fold(
				// CTrue
				() -> state,

				// CEqual
				(ty, expectation) -> {
					Variable actual = typeToVariable(rank, ty);
					Variable expected = expectedToVariable(rank, expectation);
					Unify.unify(actual, expected);
					return state;

				},

				// CLocal
				(id, expectation) -> {
					// env内の変数は再利用する
					Variable actual = makeCopy(rank, env.get(id));
					Variable expected = expectedToVariable(rank, expectation);
					Unify.unify(actual, expected);
					return state;
				},

				// CForeign
				(id, ty, expectation) -> {
					Variable actual = srcTypeToVariable(rank, ty);
					Variable expected = expectedToVariable(rank, expectation);
					Unify.unify(actual, expected);
					return state;
				},

				// CPattern
				(ty, expectation) -> {
					Variable actual = typeToVariable(rank, ty);
					Variable expected = expectedToVariable(rank, expectation);
					Unify.unify(actual, expected);
					return state;
				},

				// CAnd,
				list -> {
					State result = state;
					for(Constraint c : list) {
						result = solve(env, rank, state, c);
					}
					return result;
				},

				// CLet
				cint -> {
					if(cint.ridids().isEmpty()) {
						if(cint.bodyCon().category == Category.CTrue) {
							introduce(rank, cint.flexes());
							return solve(env, rank, state, cint.headerCon());
						} else if(cint.flexes().isEmpty()) {
							State state1 =
									solve(env, rank, state, cint.headerCon());
							IdMap<Variable> locals =
									cint.headers().traverse(ty -> typeToVariable(rank, ty));
							IdMap<Variable> newEnv = IdMap.union(env, locals);
							State state2 =
									solve(newEnv, rank, state1, cint.bodyCon());
							locals.forEach((id, var) -> state2.occurs(id, var));

							// record reconstructed types
							locals.forEach((id, var) -> reconed.put(id, var.toType()));
							return state2;
						}
					}

					int nextRank = rank + 1;
					pool.ensureCapasity(nextRank);

					List<Variable> vars = new ArrayList<>();
					vars.addAll(cint.ridids());
					vars.addAll(cint.flexes());
					vars.forEach(v -> {
						Descriptor d = v.get();
						Descriptor d_ = new Descriptor(d.content, nextRank, d.mark);
						v.set(d_);
					});
					pool.clear(nextRank);
					pool.addAll(vars, nextRank);

					IdMap<Variable> locals = cint.headers().traverse(ty -> typeToVariable(nextRank, ty));
					State state_ = solve(env, nextRank, state, cint.headerCon());

					int youngMark = state_.mark;
					int visitMark = youngMark + 1;
					int finalMark = visitMark + 1;

					generalize(youngMark, visitMark, nextRank);
					pool.clear(nextRank);

					// check
					cint.ridids().forEach(var -> checkGeneric(var));

					// record reconstructed types
					// is it really correct to record here?
					locals.forEach((id, var) -> reconed.putOrConfirm(id, var.toType()));

					IdMap<Variable> newEnv = IdMap.union(env, locals);
					State tmpState = new State(state_.env, finalMark);
					State newState = solve(newEnv, rank, tmpState, cint.bodyCon());

					locals.forEach((id, var) -> newState.occurs(id, var));
					return newState;
				},

				// SaveTheEnvironment
				() -> state.saveTheEnvironment(env)
		);
	}

	private void checkGeneric(Variable var) {
		Descriptor d = var.get();
		if(d.rank != Variable.NO_RANK) {
			throw new Error("var: "+var+", rank:"+d.rank);
		}
	}

	private Variable expectedToVariable(int rank, zlk.recon.constraint.Type expectation) {
		return typeToVariable(rank, expectation);
	}

	private void generalize(int youngMark, int visitMark, int youngRank) {
		List<Variable> youngVars = pool.getAll(youngRank);
		Pool rankTable = getRankTable(youngMark, youngRank, youngVars);

		for(int rank = 0; rank < rankTable.size(); rank++) {
			for(Variable var : rankTable.getAll(rank)) {
				adjustRank(var, youngMark, visitMark, rank);
			}
		}

		// For variables that have rank lower than youngRank, register them in
		// the appropriate old pool if they are not redundant.

		for(List<Variable> vars : rankTable.heads()) {
			for(Variable var : vars) {
				if(!var.redundant()) {
					Descriptor d = var.get();
					pool.add(var, d.rank);
				}
			}
		}

		// For variables with rank youngRank
		// If rank < youngRank: register in oldPool
		// otherwise generalize
		for(Variable var : rankTable.last()) {
			if(!var.redundant()) {
				Descriptor d = var.get();
				if(d.rank < youngRank) {
					pool.add(var, d.rank);
				} else {
					var.set(new Descriptor(d.content, Variable.NO_RANK, d.mark));
				}
			}
		}
	}

	private Pool getRankTable(int youngMark, int youngRank, List<Variable> youngInhabitants) {
		Pool mutableTable = new Pool(0);
		mutableTable.ensureCapasity(youngRank);

		for(Variable var : youngInhabitants) {
			Descriptor d = var.get();
			var.set(new Descriptor(d.content, d.rank, youngMark));
			mutableTable.add(var, d.rank);
		}

		return mutableTable;
	}

	private int adjustRank(Variable var, int youngMark, int visitMark, int groupRank) {
		Descriptor d = var.get();
		if(d.mark == youngMark) {
			// set the variable as marked first because it may be cyclic.
			var.set(new Descriptor(d.content, d.rank, visitMark));
			int maxRank = adjustRankContent(d.content, youngMark, visitMark, groupRank);
			var.set(new Descriptor(d.content, maxRank, visitMark));
			return maxRank;
		} else if(d.mark == visitMark) {
			return d.rank;
		} else {
			int minRank = Math.min(groupRank, d.rank);
			var.set(new Descriptor(d.content, minRank, visitMark));
			return minRank;
		}
	}

	private int adjustRankContent(Content content, int youngMark, int visitMark, int groupRank) {
		ToIntFunction<Variable> go = var -> adjustRank(var, youngMark, visitMark, groupRank);
		return switch(content) {
		case FlexVar _ -> groupRank;
		case Structure(FlatType.App1(_, List<Variable> args)) -> {
			int tmp = args.stream()
					.mapToInt(go)
					.max()
					.orElse(Variable.OUTERMOST_RANK);
			yield Math.max(tmp, Variable.OUTERMOST_RANK);
		}
		case Structure(FlatType.Fun1(Variable arg, Variable ret)) ->
			Math.max(go.applyAsInt(arg), go.applyAsInt(ret));
		};
	}

	private void introduce(int rank, List<Variable> vars) {
		pool.addAll(vars, rank);
		for(Variable var : vars) {
			Descriptor d = var.get();
			var.set(new Descriptor(d.content, rank, d.mark));
		}
	}

	private Variable typeToVariable(int rank, zlk.recon.constraint.Type ty) {
		return typeToVar(rank, new IdMap<>(), ty);
	}

	private Variable typeToVar(int rank, IdMap<Variable> aliasDict, zlk.recon.constraint.Type ty) {
		Function<zlk.recon.constraint.Type, Variable> go = t -> typeToVar(rank, aliasDict, t);

		return switch(ty) {
		case VarN(Variable var) -> var;
		case AppN(Id id, var args) -> {
			List<Variable> argVars = args.stream().map(go).toList();
			yield register(rank, Content.app(id, argVars));
		}
		case FunN(var arg, var ret) -> {
			Variable aVar = go.apply(arg);
			Variable bVar = go.apply(ret);
			yield register(rank, Content.fun(aVar, bVar));
		}
		};
	}

	private Variable register(int rank, Content content) {
		Variable var = new Variable(new Descriptor(content, rank, Variable.NO_MARK));
		pool.add(var, rank);
		return var;
	}

	private Variable srcTypeToVariable(int rank, Type ty) {
		Function<String, Variable> makeVar = name -> new Variable(
				new Descriptor(Variable.mkFlexVar(name), rank, Variable.NO_MARK));

		Map<String, Variable> flexVars = ty.flatten().stream()
				.flatMap(t ->
					switch(t) {
					case Type.Var(String name) -> Stream.of(name);
					default -> Stream.of();
					}
				).collect(Collectors.toMap(v -> v, makeVar));

		pool.addAll(flexVars.values(), rank);
		return srcTypeToVar(rank, flexVars, ty);
	}

	private Variable srcTypeToVar(int rank, Map<String, Variable> flexVars, Type ty) {
		Function<Type, Variable> go = t -> srcTypeToVar(rank, flexVars, t);
		return switch(ty) {
		case Type.Atom(Id id) ->
			register(rank, Content.app(id, List.of()));
		case Type.Arrow(Type arg, Type ret) -> {
			Variable argVar = go.apply(arg);
			Variable retVar = go.apply(ret);
			yield register(rank, Content.fun(argVar, retVar));
		}
		case Type.Var(String name) ->
			Objects.requireNonNull(flexVars.get(name));
		};
	}

	private Variable makeCopy(int rank, Variable target) {
		// 再帰型のコピーで循環するので，コピー済みの値を渡す
		return makeCopy(rank, target, new CopyList());
	}
	private Variable makeCopy(int rank, Variable target, CopyList copieds) {
		Variable copied = copieds.getCopyOrNull(target);
		if(copied != null) {
			return copied;
		}

		Descriptor descriptor = target.get();

		if(descriptor.rank != Variable.NO_RANK) {
			return target;
		} else {
			System.out.println("copied: "+target);
		}

		Function<Content, Descriptor> md = c -> new Descriptor(c, rank, Variable.NO_MARK);

		Variable copy = new Variable(md.apply(descriptor.content));
		pool.add(copy, rank);
		copieds.add(target, copy);

		if(descriptor.content instanceof Structure(FlatType flatType)) {
			FlatType newTerm = flatType.traverse(v -> makeCopy(rank, v, copieds));
			copy.set(md.apply(new Structure(newTerm)));
		}

		return copy;
	}
	@SuppressWarnings("serial")
	private static class CopyList extends ArrayList<VarCopy> {
		void add(Variable original, Variable copy) {
			add(new VarCopy(original, copy));
		}

		Variable getCopyOrNull(Variable target) {
			for(int i = 0; i < size(); i++) {
				VarCopy vc = get(i);
				if(vc.var == target) {
					return vc.copy;
				}
			}
			return null;
		}
	}
	private static record VarCopy(Variable var, Variable copy) {}




	private static class Pool {
		final List<List<Variable>> impl;

		Pool(int size) {
			this.impl = new ArrayList<>();
			for(int i = 0; i < size; i++) {
				impl.add(new ArrayList<>());
			}
		}

		void ensureCapasity(int rank) {
			while(impl.size() <= rank) {
				impl.add(new ArrayList<>());
			}
		}

		int size() {
			return impl.size();
		}

		void clear(int rank) {
			impl.get(rank).clear();
		}

		void add(Variable variable, int rank) {
			impl.get(rank).add(0, variable);
		}

		void addAll(Collection<Variable> variables, int rank) {
			impl.get(rank).addAll(0, variables);
		}

		List<Variable> getAll(int rank) {
			return Collections.unmodifiableList(impl.get(rank));
		}

		List<List<Variable>> heads() {
			return impl.subList(0, impl.size()-1);
		}

		List<Variable> last() {
			return impl.get(impl.size()-1);
		}


	}

	private static class Itv {
		private Stack<ArrayList<IdVar>> impl;

		public Itv() {
			impl = new Stack<>();
			push();
		}

		public void push() {
			impl.push(new ArrayList<>());
		}

		public void pop() {
			impl.pop();
		}

		public void put(Id id, Variable var) {
			impl.peek().add(new IdVar(id, var));
		}

		public Variable get(Id id) {
			for(ArrayList<IdVar> env : impl) {
				for(IdVar idvar : env) {
					if(id.equals(idvar.id())) {
						return idvar.var();
					}
				}
			}
			throw new NoSuchElementException(id.toString());
		}
	}
}
