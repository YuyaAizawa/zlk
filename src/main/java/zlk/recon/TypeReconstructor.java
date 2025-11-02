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
import zlk.recon.TypeError.InfinitType;
import zlk.recon.constraint.Constraint;
import zlk.recon.constraint.Constraint.CEqual;
import zlk.recon.constraint.Constraint.CExists;
import zlk.recon.constraint.Constraint.CForeign;
import zlk.recon.constraint.Constraint.CLet;
import zlk.recon.constraint.Constraint.CLocal;
import zlk.recon.constraint.Constraint.CPattern;
import zlk.recon.constraint.Constraint.CPhase;
import zlk.recon.constraint.Content;
import zlk.recon.constraint.Content.Structure;
import zlk.recon.constraint.RcType;
import zlk.recon.constraint.RcType.AppN;
import zlk.recon.constraint.RcType.FunN;
import zlk.recon.constraint.RcType.VarN;
import zlk.util.Result;

public class TypeReconstructor {

	// TODO 型が付かなかったときは例外でなくResultの方が扱いやすそう
	// TODO 例外が起きたら，それに関する型はダミーの型に確定したとして続けたらいいか？

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

	private TypeReconstructor() {
		result = new IdMap<>();
		errors = new ArrayList<>();
	}

	public static Result<List<TypeError>, IdMap<Type>> recon(Constraint con) {
		TypeReconstructor self = new TypeReconstructor();
		self.solve(con, 0, new IdMap<>());

		if(self.errors.isEmpty()) {
			return new Result.Ok<>(self.result.traverse(v -> v.toType()));
		} else {
			return new Result.Err<>(self.errors);  // TODO: unifyのmismatchなどを入れる
		}
	}

	private void solve(Constraint con, int letRank, IdMap<Variable> env) {
		switch(con) {
		case CEqual(RcType type, RcType expectation) -> {
			Variable actual = typeToVar(letRank, type, IdMap.of());
			Variable expected = typeToVar(letRank, expectation, IdMap.of());
			Unify.unify(actual, expected);
		}
		case CLocal(Id id, RcType expectation) -> {
			Variable actual = instanciateIfNeed(letRank, env.get(id));
			Variable expected = typeToVar(letRank, expectation, IdMap.of());
			Unify.unify(actual, expected);
		}
		case CForeign(Id id, Type type, RcType expectation) -> {
			Map<String, Variable> typeVars =
					type.getVarNames()
							.stream()
							.collect(Collectors.toMap(
									name -> name,
									name -> new Variable(name, letRank)));

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
				List<CPhase> headerCons,
				List<Constraint> bodyCons)
		-> {
			final int nextRank = letRank + 1;

			introduce(rigids, nextRank);
			introduce(flexes, nextRank);

			IdMap<Variable> locals = header.traverse(ty -> typeToVar(nextRank, ty, IdMap.of()));  // TODO: 型エイリアスを追加
			IdMap<Variable> newEnv = IdMap.union(env, locals);

			// 強連結成分ごとに解決
			for (CPhase phase : headerCons) {
				solve(phase.cons(), nextRank, newEnv);

				// let宣言の関数を一般化
				List<Variable> anchors = phase.genTargets().stream().map(locals::get).toList();
				final int youngMark = gMarkCounter++;
				final int visitMark = gMarkCounter++;
				generalizeAnchors(youngMark, visitMark, nextRank, anchors);
			}
			// let宣言の結果を保存
			locals.forEach((id, v) -> result.put(id, v));

			// 本体を解く
			solve(bodyCons, letRank, newEnv);

			locals.forEach(this::occurCheck);
		}
		case CExists(
			List<Variable> vars,
			List<Constraint> cons)
		-> {
			final int nextRank = letRank + 1;
			introduce(vars, nextRank);
			solve(cons, nextRank, env);
			// assert vars.stream().allMatch(var->!var.occurs());
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
		return var;
	}

	private void occurCheck(Id id, Variable var) {
		if(var.occurs()) {
			// TODO エラーの詳細情報を構築
			errors.add(new InfinitType(id));
		}
	}

	private void generalizeAnchors(int youngMark, int visitMark, int youngRank, List<Variable> anchors) {
		// 外部に触れていたらrankを下げる
		anchors.forEach(anchor -> anchor.markAndWalk(visitMark,
				v -> adjustRank(youngMark, visitMark, youngRank, v)));

		// 一般化できるものをする
		anchors.forEach(anchor -> anchor.markAndWalk(youngMark,
				v -> {
					TypeVarState s = v.get();
					if(s.content instanceof Content.RigidVar) {
						return;
					}
					if(s.rank < youngRank) {
						// スコープの外に漏れた
					} else if (s.rank == youngRank) {
						s.rank = 0; // TODO: 一般化された形にしてresultに追加
					}
				}));
	}

	/**
	 * 型変数の結びつきをたどり，最も高いものにrankを合わせる．
	 * 修正後のrankを返す．
	 */
	private int adjustRank(int youngMark, int visitMark, int groupRank, Variable var) {
		TypeVarState state = var.get();
		if(state.gMark == youngMark) {
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

		return switch(content) {
		case Content.RigidVar _ -> groupRank;
		case Content.FlexVar _ -> groupRank;
		case Structure(FlatType.CtorApp1(_, List<Variable> args)) ->
			args.stream().mapToInt(go).max().orElse(0);
		case Structure(FlatType.Fun1(Variable arg, Variable ret)) ->
			Math.max(go.applyAsInt(ret), go.applyAsInt(arg));
		case Content.Error() -> groupRank;
		};
	}

	private static void introduce(List<Variable> vars, int letRank) {
		for(Variable var : vars) {
			var.get().rank = letRank;
		}
	}

	// rank == 0のものを具体化（コピー）
	private Variable instanciateIfNeed(int letRank, Variable v) {
		Variable copy = instanciateIfNeedHelp(letRank, v);
		restore(v);
		return copy;
	}
	private Variable instanciateIfNeedHelp(int letRank, Variable v) {
		TypeVarState s = v.get();
		if(s.cacheOnCopy != null) {
			return s.cacheOnCopy;
		}
		if(s.rank != 0) {
			return v;
		}

		System.out.println("instanciate: "+s.content);

		// キャッシュの用意
		Variable copy = new Variable(s.content, s.rank);  // contentは仮
		s.cacheOnCopy = copy;

		// 具体化
		switch(s.content) {
		case Content.RigidVar(String name) -> {
			copy.set(new TypeVarState(new Content.FlexVar(name), letRank));
		}
		case Content.Structure cture -> {
			// 再帰的にコピー
			Content.Structure cture_ = cture.traverse(v_ -> instanciateIfNeedHelp(letRank, v_));
			copy.set(new TypeVarState(cture_, letRank));
		}
		case Content.FlexVar _, Content.Error _ -> {}
		};
		return copy;
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
		case Content.FlexVar _ -> {}
		case Content.RigidVar _ -> {}
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
		case Content.Error() -> {}
		}
	}

	// TODO: 表示用の暫定なので処遇を考える
	private static Type toType(Variable root) {
		// rank==0 の TVar を ∀ で束縛。発見順で a, b, c...
		  Map<TypeVarState, String> names = new java.util.HashMap<>();
		  var next = new Object(){ int i=0; };
		  Function<TypeVarState,String> freshName = v -> {
		    return names.computeIfAbsent(v, _ -> {
		      int n = next.i++;
		      // a,b,c,...,z, a1,b1,...
		      char base = (char)('a' + (n % 26));
		      int k = n / 26;
		      return k==0 ? String.valueOf(base) : (""+base+k);
		    });
		  };

		  Function<Variable, Type> go = new Function<>() {
		    public Type apply(Variable v) {
		      var s = v.get();
		      return switch (s.content) {
		        case Content.FlexVar flexId -> {
		          if (s.rank == 0) { // ★ 多相変数
		            yield new Type.Var(freshName.apply(s));
		          } else {
		            // ここに来るのは原則 let 抜け直後のアンカーでは無いはずだが、
		            // 念のため mono として匿名名にする or エラー
		            yield new Type.Var("_t" + System.identityHashCode(v));
		          }
		        }
		        case Content.RigidVar r -> {
		          // rigid がスキームに現れるのは注釈破りなので通常は起きない。念のため停止または匿名化。
		          throw new IllegalStateException("Rigid leaked into scheme: " + r.name());
		        }
		        case Content.Structure(FlatType.CtorApp1(Id id, List<Variable> args)) -> {
		          var ts = args.stream().map(this::apply).toList();
		          yield new Type.Atom(id, ts);
		        }
		        case Content.Structure(FlatType.Fun1(Variable a, Variable b)) -> {
		          yield new Type.Arrow(apply(a), apply(b));
		        }
		        case Content.Error _ -> throw new RuntimeException();
		      };
		    }
		  };

		  Type body = go.apply(root);
		  return body;
	}
}
