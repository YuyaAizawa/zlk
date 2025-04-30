package zlk.recon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import zlk.common.Type;
import zlk.common.Type.Arrow;
import zlk.common.Type.Atom;
import zlk.common.Type.Var;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.core.Builtin;
import zlk.recon.TypeError.InfinitType;
import zlk.recon.constraint.Constraint;
import zlk.recon.constraint.Constraint.CEqual;
import zlk.recon.constraint.Constraint.CExists;
import zlk.recon.constraint.Constraint.CForeign;
import zlk.recon.constraint.Constraint.CLet;
import zlk.recon.constraint.Constraint.CLocal;
import zlk.recon.constraint.Constraint.CPattern;
import zlk.recon.constraint.Content;
import zlk.recon.constraint.Content.Structure;
import zlk.recon.constraint.RcType;
import zlk.recon.constraint.RcType.AppN;
import zlk.recon.constraint.RcType.FunN;
import zlk.recon.constraint.RcType.VarN;
import zlk.recon.constraint.Reason;
import zlk.util.Result;

public class TypeReconstructor {

	// TODO 型が付かなかったときは例外でなくResultの方が扱いやすそう

	/**
	 * 量化判定待ちの型変数をletのネスト深さ毎に分類
	 */
	private ArrayList<List<Variable>> pools;
	
	/**
	 * 汎化するときに使う
	 */
	private int gMarkCounter;

	/**
	 * 推論結果
	 */
	private IdMap<Variable> result;

	/**
	 * 検出された型エラー
	 */
	private List<TypeError> errors;

	public TypeReconstructor() {
		pools = new ArrayList<>();
		result = new IdMap<>();
		errors = new ArrayList<>();
	}

	public static Result<List<TypeError>, IdMap<Type>> recon(Constraint con) {
		TypeReconstructor reconstructor = new TypeReconstructor();
		reconstructor.solve(con, 0, new IdMap<>());
		if(reconstructor.errors.isEmpty()) {
			return new Result.Ok<>(reconstructor.result.traverse(v -> v.toType()));
		} else {
			return new Result.Err<>(reconstructor.errors);
		}
	}
	
	private void solve(Constraint con, int letRank, IdMap<Variable> env) {
		switch(con) {
		case CEqual(RcType type, RcType expectation, _) -> {
			Variable actual = typeToVar(letRank, type, IdMap.of());
			Variable expected = typeToVar(letRank, expectation, IdMap.of());
			Unify.unify(actual, expected);
		}
		case CLocal(Id id, RcType expectation, Reason reason) -> {
			Variable actual = makeCopy(letRank, env.get(id));
			Variable expected = typeToVar(letRank, expectation, IdMap.of());
			Unify.unify(actual, expected);
		}
		case CForeign(Id id, Type type, RcType expectation, Reason reason) -> {
			Map<String, Variable> typeVars =
					type.getVerNames()
							.stream()
							.collect(Collectors.toMap(
									name -> name,
									name -> new Variable(name, letRank)));
			List<Variable> pool = pools.get(letRank);
			pool.addAll(typeVars.values());

			Variable actual = annoTypeToVar(letRank, typeVars, type);
			Variable expected = typeToVar(letRank, expectation, IdMap.of());
			Unify.unify(actual, expected);
		}
		case CPattern(Id id, RcType ctorTy, RcType expection) -> {
			Variable actual = typeToVar(letRank, ctorTy, IdMap.of());
			Variable expected = typeToVar(letRank, expection, IdMap.of());
			Unify.unify(actual, expected);
		}
		case CLet(
				List<Variable> rigids,
				List<Variable> flexes,
				IdMap<RcType> header,
				List<Constraint> headerCons,
				List<Constraint> bodyCons
		) -> {
			if(rigids.isEmpty() && flexes.isEmpty()) {
				solve(headerCons, letRank, env);

				// 導入された型を型変数に変換
				IdMap<Variable> locals = header.traverse(ty -> typeToVar(letRank, ty, IdMap.of()));

				// 導入された型を追加した型環境を生成
				IdMap<Variable> newEnv = IdMap.union(env, locals);

				solve(bodyCons, letRank, newEnv);
				locals.forEach((id, var) -> occurCheck(id, var));
			} else {

				// 一段深いスコープ用のpoolを用意
				int nextRank = letRank + 1;
				if(pools.size() + 1 < nextRank) {
					pools.add(new ArrayList<>());
				}
				List<Variable> nextPool = pools.get(nextRank);
				nextPool.clear();

				// 導入された型変数のランクを更新してpoolに追加
				rigids.forEach(v -> {
					TypeVarState state = v.get();
					state.rank = nextRank;
					nextPool.add(v);
				});
				flexes.forEach(v -> {
					TypeVarState state = v.get();
					state.rank = nextRank;
					nextPool.add(v);
				});

				// 参照用型環境を生成
				IdMap<Variable> locals = header.traverse(ty -> typeToVar(nextRank, ty, IdMap.of()));
				
				// 前提制約のsolve
				solve(headerCons, nextRank, env);
				
				// 汎化
				int youngMark = gMarkCounter++;
				int visitMark = gMarkCounter++;
				int finalMark = gMarkCounter++;
				generalize(youngMark, visitMark, letRank);

				// 汎化できたか念のため確認
				rigids.forEach(var -> {
					TypeVarState state = var.get();
					if(state.rank != 0) {
						throw new Error("it might be a bug");
					}
				});
				
				// 導入された型を追加した型環境を生成
				IdMap<Variable> newEnv = IdMap.union(env, locals);
				solve(bodyCons, letRank, newEnv);
				
				locals.forEach((id, var) -> occurCheck(id, var));
			}
		}
		case CExists(
			List<Variable> vars,
			List<Constraint> cons
		) -> {
			introduce(vars, letRank);
			solve(cons, letRank, env);
		}
		}
	}
	
	private void solve(List<Constraint> cons, int letRank, IdMap<Variable> env) {
		for(Constraint con : cons) {
			solve(con, letRank, env);
		}
	}

	/**
	 * 指定した型を示す型変数を導入する
	 */
	private Variable typeToVar(int letRank, RcType ty, IdMap<Variable> aliases) {
		Function<RcType, Variable> go = t -> typeToVar(letRank, t, aliases);

		switch(ty) {
		case VarN(Variable var) -> {
			return var;
		}
		case AppN(Id id, List<RcType> args) -> {
			List<Variable> argVars = args.stream().map(go).toList();
			return register(letRank, new Structure(new FlatType.CtorApp1(id, argVars)));
		}
		case FunN(var arg, var ret) -> {
			Variable aVar = go.apply(arg);
			Variable bVar = go.apply(ret);
			return register(letRank, new Structure(new FlatType.Fun1(aVar, bVar)));
		}
		}
	}

	private Variable annoTypeToVar(int letRank, Map<String, Variable> typeVars, Type type) {
		Function<Type, Variable> go = t -> annoTypeToVar(letRank, typeVars, t);

		switch(type) {
		case Atom(Id id, List<Type> typeArguments) -> {
			List<Variable> argVars = typeArguments.stream().map(go).toList();
			return register(letRank, new Structure(new FlatType.CtorApp1(id, argVars)));
		}
		case Arrow(Type arg, Type ret) -> {
			Variable argVar = go.apply(arg);
			Variable retVar = go.apply(ret);
			return register(letRank, new Structure(new FlatType.Fun1(argVar, retVar)));
		}
		case Var(String name) -> {
			return typeVars.get(name);
		}
		}
	}

	private Variable register(int letRank, Content content) {
		Variable var = new Variable(content, letRank);
		pools.get(letRank).add(var);
		return var;
	}

	// TODO instantiateの方がいいか？
	private Variable makeCopy(int letRank, Variable var) {
		Variable copy = makeCopyHelp(letRank, var);
		restore(var);
		return copy;
	}
	private Variable makeCopyHelp(int maxRank, Variable var) {
		TypeVarState state = var.get();

		if(state.cacheOnCopy!=null) {
			return state.cacheOnCopy;
		}

		if(state.rank != 0) {
			return var;
		}

		Variable copy = new Variable(state.content, maxRank);
		pools.get(maxRank).add(copy);
		state.cacheOnCopy = copy;

		switch(state.content) {
		case Content.FlexVar _ -> {
			return copy;
		}
		case Content.Structure term -> {
			Structure term_ = term.traverse(v -> makeCopyHelp(maxRank, v));
			copy.set(new TypeVarState(term_, maxRank));
			return copy;
		}
		case Content.Error() -> {
			return copy;
		}
		}
	}
	private void restore(Variable var) {
		TypeVarState state = var.get();

		if(state.cacheOnCopy==null) {
			return;
		}
		state.cacheOnCopy = null;
		restoreContent(state.content);
	}
	private void restoreContent(Content content) {
		switch(content) {
		case Content.FlexVar _ -> {
		}
		case Content.Structure(FlatType term) -> {
			switch(term) {
			case FlatType.CtorApp1(_, List<Variable> args) -> {
				args.forEach(arg -> restore(arg));
			}
			case FlatType.Fun1(Variable arg, Variable ret) -> {
				restore(arg);
				restore(ret);
			}
			default -> throw new IllegalArgumentException("Unexpected value: " + term);
			}
		}
		case Content.Error() -> {
		}
		}
	}

	private void occurCheck(Id id, Variable var) {
		if(var.occurs()) {
			// TODO エラーの詳細情報を構築
			errors.add(new InfinitType(id));
		}
	}
	
	private void generalize(int youngMark, int visitMark, int youngRank) {
		List<Variable> youngVars = pools.get(youngRank);
		
		// rank毎に型変数を分類
		ArrayList<List<Variable>> rankTable = new ArrayList<>();
		for(int i = 0; i < youngRank + 1; i++) {
			rankTable.add(new ArrayList<>());
		}
		youngVars.forEach(var -> {
			TypeVarState state = var.get();
			state.gMark = youngMark; //  ついでにマークする
			rankTable.get(state.rank).add(var);
		});
		
		// rankをletのネスト数からもっとも外側の出現に補正
		for(int rank = 0; rank < rankTable.size(); rank++) {
			for(Variable var : rankTable.get(rank)) {
				adjustRank(youngMark, visitMark, rank, var);
			};
		}
		
		// letの外側に漏れた型変数をpoolsに戻す
		for(int rank = 0; rank < rankTable.size(); rank++) {
			for(Variable var : rankTable.get(rank)) {
				if(!var.isRedundant()) {  // rootだけで充分
					TypeVarState state = var.get();
					if(state.rank < youngRank) {
						// 外に漏れているので上のプールへ登録
						pools.get(state.rank).add(var);
					} else {
						// 内側なら汎化
						state.rank = 0;
					}
				}
			};
		}
		youngVars.clear();
	}
	
	/**
	 * 型変数の結びつきをたどり，最も高いものにrankを合わせる．
	 * 修正後のrankを返す．
	 */
	private int adjustRank(int youngMark, int visitMark, int groupRank, Variable var) {
		TypeVarState state = var.get();
		if(state.rank == youngMark) {
			state.gMark = visitMark;
			int maxRank = adjustRankContent(youngMark, visitMark, groupRank, state.content);
			state.rank = maxRank;
			return maxRank;
		} else if(state.gMark == visitMark) {
			return state.rank;
		} else {
			int minRank = Math.min(groupRank, state.rank);  // TODO groupRankの方が低いってことある？
			state.gMark = visitMark;
			state.rank = minRank;
			return minRank;
		}
	}
	private int adjustRankContent(int youngMark, int visitMark, int groupRank, Content content) {
		ToIntFunction<Variable> go = c -> adjustRank(youngMark, visitMark, groupRank, c);
		
		switch(content) {
		case Content.FlexVar _:
			return groupRank;
		case Structure(FlatType.CtorApp1(_, List<Variable> args)):
			return args.stream().mapToInt(go).max().orElse(0);
		case Structure(FlatType.Fun1(Variable arg, Variable ret)):
			return Math.max(go.applyAsInt(ret), go.applyAsInt(arg));
		case Content.Error():
			return groupRank;
		}
	}
	
	private void introduce(List<Variable> vars, int letRank) {
		pools.get(letRank).addAll(vars);
		for(Variable var : vars) {
			var.get().rank = letRank;
		}
	}
}
