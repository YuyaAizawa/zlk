package zlk.compiler.phase.recon;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import zlk.compiler.PhaseResult;
import zlk.compiler.diagnostic.Diagnostic;
import zlk.compiler.diagnostic.DiagnosticReporter;
import zlk.compiler.id.Id;
import zlk.compiler.id.IdMap;
import zlk.compiler.ir.idcalc.ExpOrPatternMap;
import zlk.compiler.ir.typing.RecordField;
import zlk.compiler.ir.typing.Type;
import zlk.compiler.phase.recon.Constraint.CEqual;
import zlk.compiler.phase.recon.Constraint.CExists;
import zlk.compiler.phase.recon.Constraint.CForeign;
import zlk.compiler.phase.recon.Constraint.CLet;
import zlk.compiler.phase.recon.Constraint.CLocal;
import zlk.compiler.phase.recon.Constraint.CPattern;
import zlk.compiler.phase.recon.Constraint.CPhase;
import zlk.compiler.phase.recon.Constraint.Provenance;
import zlk.compiler.phase.recon.Content.Structure;
import zlk.compiler.phase.recon.RcType.AppN;
import zlk.compiler.phase.recon.RcType.FunN;
import zlk.compiler.phase.recon.RcType.RecordN;
import zlk.compiler.phase.recon.RcType.RowN;
import zlk.compiler.phase.recon.RcType.VarN;
import zlk.util.collection.Seq;

public class TypeReconstructor {
	public record Result(
			IdMap<Type> types,  // 識別子の汎化された型
			ExpOrPatternMap<Type> partExpType  // 部分式の汎化されていない型
	) {}

	/** 型再構築内部だけで期待されたsource failureをphase入口まで運ぶ． */
	private static final class ReconstructionAbort extends RuntimeException {
		private static final long serialVersionUID = 1L;

		private final Diagnostic diagnostic;

		private ReconstructionAbort(Diagnostic diagnostic, Throwable cause) {
			super(diagnostic.toString(), cause);
			this.diagnostic = diagnostic;
		}
	}

	// TODO 例外が起きたら，それに関する型はダミーの型に確定したとして続けたらいいか？

	/**
	 * 推論結果
	 */
	private IdMap<Variable> result;

	/**
	 * 衝突しない型変数の生成器
	 */
	private FreshFlex freshFlex;
	private final Seq<Id> adtFamilies;

	/**
	 * 汎化するときに使う
	 */
	private int gMarkCounter;

	private TypeReconstructor(FreshFlex freshFlex, Seq<Id> adtFamilies) {
		this.result = new IdMap<>();
		this.freshFlex = freshFlex;
		this.adtFamilies = adtFamilies;
	}

	public static PhaseResult<Result> recon(
			ConstraintExtractor.Result extracted,
			FreshFlex freshFlex,
			DiagnosticReporter reporter) {
		TypeReconstructor self = new TypeReconstructor(freshFlex, extracted.adtFamilies());
		try {
			self.solve(extracted.constraint(), 0, new IdMap<>());
			return PhaseResult.ready(new Result(
					self.result.traverse(Variable::toType),
					extracted.partExpType().traverse(RcType::toType)));
		} catch(ReconstructionAbort abort) {
			reporter.report(abort.diagnostic);
			return PhaseResult.blocked();
		}
	}

	private void solve(Constraint con, int letRank, IdMap<Variable> env) {
		solve(con, letRank, env, null);
	}

	private void solve(
			Constraint con,
			int letRank,
			IdMap<Variable> env,
			Diagnostic.InfiniteType infiniteType
	) {
		switch(con) {
		case CEqual(RcType type, RcType expectation, Provenance provenance) -> {
			Variable actual = typeToVar(letRank, type, IdMap.of());
			Variable expected = typeToVar(letRank, expectation, IdMap.of());
			unify(actual, expected, provenance, letRank);
		}
		case CLocal(Id id, RcType expectation, Provenance provenance) -> {
			Variable actual = instantiateIfGeneralized(letRank, env.get(id));
			Variable expected = typeToVar(letRank, expectation, IdMap.of());
			unify(actual, expected, provenance, letRank);
		}
		case CForeign(Id _, Type type, RcType expectation, Provenance provenance) -> {
			RcType.Inst inst = RcType.instantiate(type, freshFlex);
			introduce(inst.flexes(), letRank);
			Variable actual = typeToVar(letRank, inst.type(), IdMap.of());
			Variable expected = typeToVar(letRank, expectation, IdMap.of());
			unify(actual, expected, provenance, letRank);
		}
		case CPattern(Id constructor, Id family, RcType ctorTy, RcType expection, var location) -> {
			Variable actual = typeToVar(letRank, ctorTy, IdMap.of());
			Variable expected = typeToVar(letRank, expection, IdMap.of());
			unifyPattern(actual, expected, constructor, family, location, letRank);
		}
		case CLet(
				Seq<Variable> rigids,
				Seq<Variable> flexes,
				IdMap<RcType> header,
				Seq<CPhase> headerCons,
				Seq<Constraint> bodyCons,
				IdMap<zlk.compiler.source.Location> declarationLocations)
		-> {
			final int nextRank = letRank + 1;

			introduce(rigids, nextRank);
			introduce(flexes, nextRank);

			IdMap<Variable> locals = header.traverse(ty -> typeToVar(nextRank, ty, IdMap.of()));  // TODO: 型エイリアスを追加
			introduce(locals.values(), nextRank);
			IdMap<Variable> newEnv = IdMap.union(env, locals);

			// 強連結成分ごとに解決
			for (CPhase phase : headerCons) {
				Diagnostic.InfiniteType phaseInfiniteType = infiniteType;
				if(!phase.genTargets().isEmpty()) {
					Id id = phase.genTargets().head();
					if(declarationLocations.containsKey(id)) {
						phaseInfiniteType = new Diagnostic.InfiniteType(
								declarationLocations.get(id), id.simpleName());
					}
				}
				solve(phase.cons(), nextRank, newEnv, phaseInfiniteType);

				// let宣言の関数を一般化
				Seq<Variable> anchors = phase.genTargets().map(locals::get);
				final int youngMark = gMarkCounter++;
				final int visitMark = gMarkCounter++;
				generalizeAnchors(youngMark, visitMark, nextRank, anchors);
			}
			// rigidはスコープ外の型へ接続されていなければ再量化できる．
			// 低いrankへ落ちたrigidはskolem escapeなのでコンパイラエラーとする．
			if(!rigids.isEmpty()) {
				final int youngMark = gMarkCounter++;
				final int visitMark = gMarkCounter++;
				generalizeAnchors(youngMark, visitMark, nextRank, rigids);
				for(Variable rigid : rigids) {
					if(!rigid.get().isQuantified()) {
						Id id = declarationLocations.keys().head();
						throw abort(new Diagnostic.TypeMismatch(
								declarationLocations.get(id),
								new Diagnostic.TypingContext.Annotation(id.simpleName()),
								Diagnostic.TypeMismatchReason.INCOMPATIBLE), null);
					}
				}
			}
			// let宣言の結果を保存
			locals.forEach((id, v) -> result.put(id, v));

			// 本体を解く
			solve(bodyCons, letRank, newEnv, infiniteType);

			declarationLocations.forEach((id, location) -> {
				if(locals.containsKey(id)) {
					occurCheck(id, locals.get(id), location);
				}
			});
		}
		case CExists(
				Seq<Variable> vars,
				Seq<Constraint> cons)
		-> {
			final int nextRank = letRank + 1;
			introduce(vars, nextRank);
			solve(cons, nextRank, env, infiniteType);
			if(vars.anyMatch(Variable::occurs)) {
				if(infiniteType != null) {
					throw abort(infiniteType, null);
				}
				throw new IllegalStateException("cyclic inference variable escaped CExists scope");
			}
		}
		}
	}

	private void solve(Seq<Constraint> cons, int letRank, IdMap<Variable> env) {
		for(Constraint con : cons) {
			solve(con, letRank, env);
		}
	}

	private void solve(
			Seq<Constraint> cons,
			int letRank,
			IdMap<Variable> env,
			Diagnostic.InfiniteType infiniteType
	) {
		for(Constraint con : cons) {
			solve(con, letRank, env, infiniteType);
		}
	}

	private void unify(Variable actual, Variable expected, Provenance provenance, int letRank) {
		try {
			Unify.unify(actual, expected, freshFlex, letRank);
		} catch(Mismatch mismatch) {
			throw abort(new Diagnostic.TypeMismatch(
					provenance.location(), provenance.context(), toDiagnosticReason(mismatch.reason())), mismatch);
		}
	}

	private void unifyPattern(
			Variable actual,
			Variable expected,
			Id constructor,
			Id actualFamily,
			zlk.compiler.source.Location location,
			int letRank) {
		try {
			Unify.unify(actual, expected, freshFlex, letRank);
		} catch(Mismatch mismatch) {
			throw abort(toPatternDiagnostic(constructor, actualFamily, location, mismatch), mismatch);
		}
	}

	private Diagnostic toPatternDiagnostic(
			Id constructor,
			Id actualFamily,
			zlk.compiler.source.Location location,
			Mismatch mismatch) {
		if(mismatch.detail() instanceof Mismatch.Detail.ConstructorFamily(var left, var right)) {
			Id expectedFamily = null;
			if(actualFamily.equals(left)) {
				expectedFamily = right;
			} else if(actualFamily.equals(right)) {
				expectedFamily = left;
			}
			if(expectedFamily != null && adtFamilies.contains(expectedFamily)) {
				return new Diagnostic.ConstructorFamilyMismatch(
						location, constructor, actualFamily, expectedFamily);
			}
		}
		return new Diagnostic.TypeMismatch(
				location, Diagnostic.TypingContext.NONE, toDiagnosticReason(mismatch.reason()));
	}

	private static Diagnostic.TypeMismatchReason toDiagnosticReason(Mismatch.Reason reason) {
		return switch(reason) {
		case INCOMPATIBLE -> Diagnostic.TypeMismatchReason.INCOMPATIBLE;
		case KIND -> Diagnostic.TypeMismatchReason.KIND;
		case ROW_LACKS -> Diagnostic.TypeMismatchReason.ROW_LACKS;
		case RECURSIVE_ROW -> Diagnostic.TypeMismatchReason.RECURSIVE_ROW;
		};
	}

	private static ReconstructionAbort abort(Diagnostic diagnostic, Throwable cause) {
		return new ReconstructionAbort(diagnostic, cause);
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
		case AppN(Id id, Seq<RcType> args) -> {
			Seq<Variable> argVars = args.map(go);
			return register(letRank, new Structure(new FlatType.CtorApp1(id, argVars)));
		}
		case FunN(var arg, var ret) -> {
			Variable aVar = go.apply(arg);
			Variable bVar = go.apply(ret);
			return register(letRank, new Structure(new FlatType.Fun1(aVar, bVar)));
		}
		case RecordN(RowN row) -> {
					return buildRecordVar(freshFlex, letRank,
						row.fields().map(field ->
							new RecordField<>(field.name(), go.apply(field.value()))),
						row.extension());
				}
		}
	}

	static Variable buildRecordVar(
			FreshFlex freshFlex,
			int letRank,
			Seq<RecordField<Variable>> fields,
			Optional<Variable> extension) {
		Variable row = new Variable(
				new Structure(new FlatType.Row1(fields, extension)),
				letRank,
				Variable.Kind.ROW);
		return new Variable(
				new Structure(new FlatType.Record1(row)),
				letRank,
				Variable.Kind.TYPE);
	}

	private Variable register(int letRank, Content content) {
		Variable var = new Variable(content, letRank);
		return var;
	}

	private void occurCheck(Id id, Variable var, zlk.compiler.source.Location location) {
		if(var.occurs()) {
			throw abort(new Diagnostic.InfiniteType(location, id.simpleName()), null);
		}
	}

	private void generalizeAnchors(int youngMark, int visitMark, int youngRank, Seq<Variable> anchors) {
		// 外部に触れていたらrankを下げる
		anchors.forEach(anchor -> anchor.markAndWalk(visitMark,
				v -> adjustRank(youngMark, visitMark, youngRank, v)));

		// 一般化できるものをする
		anchors.forEach(anchor -> anchor.markAndWalk(youngMark,
				v -> {
					VariableState s = v.get();
					if(s.content instanceof Content.RigidVar) {
						if(s.rank == youngRank) {
							s.rank = 0;
						}
						return;
					}
					if(s.rank < youngRank) {
						// スコープの外に漏れた
					} else if (s.rank == youngRank) {
						s.rank = 0; // TODO: 一般化された形にしてresultに追加？
					}
				}));
	}

	/**
	 * 型変数の結びつきをたどり，最も高いものにrankを合わせる．
	 * @return 修正後の rank
	 */
	private int adjustRank(int youngMark, int visitMark, int groupRank, Variable var) {
		VariableState state = var.get();
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
		case Structure(FlatType.CtorApp1(_, Seq<Variable> args)) ->
			args.mapToInt(go).max().orElse(groupRank);
		case Structure(FlatType.Fun1(Variable arg, Variable ret)) ->
			Math.max(go.applyAsInt(arg), go.applyAsInt(ret));
		case Structure(FlatType.Record1(Variable row)) ->
			go.applyAsInt(row);
		case Structure(FlatType.Row1(Seq<RecordField<Variable>> fields, Optional<Variable> extension)) ->
			Math.max(
				fields.mapToInt(field -> go.applyAsInt(field.value())).max().orElse(groupRank),
				extension.map(go::applyAsInt).orElse(groupRank));
		case Content.Error() -> groupRank;
		};
	}

	private static void introduce(Seq<Variable> vars, int letRank) {
		for(Variable var : vars) {
			var.get().rank = letRank;
		}
	}

	// rank == 0のものを具体化（コピー）
	private Variable instantiateIfGeneralized(int letRank, Variable v) {
		Variable copy = instanciateIfNeedHelp(letRank, v);
		restore(v);
		return copy;
	}
	private Variable instanciateIfNeedHelp(int letRank, Variable v) {
		VariableState s = v.get();
		if(s.cacheOnCopy != null) {
			return s.cacheOnCopy;
		}
		if(s.rank != 0) {
			return v;
		}

		// キャッシュの用意（元kindを維持）
		Variable copy = new Variable(new VariableState(
				new Content.Error(), s.rank, s.kind, s.forbiddenLabels));  // contentは仮
		s.cacheOnCopy = copy;

		// 具体化
		switch(s.content) {
		case Content.RigidVar(String name) -> {
			copy.set(new VariableState(
					freshFlex.getContent(name), letRank, s.kind, s.forbiddenLabels));
		}
		case Content.Structure cture -> {
			// 再帰的にコピー
			Content.Structure cture_ = cture.traverse(v_ -> instanciateIfNeedHelp(letRank, v_));
			copy.set(new VariableState(cture_, letRank, s.kind, s.forbiddenLabels));
		}
		case Content.FlexVar _ -> {
			copy.set(new VariableState(
					freshFlex.getContent(), letRank, s.kind, s.forbiddenLabels));
		}
		case Content.Error _ -> { throw new RuntimeException(); }
		};
		return copy;
	}

	private void restore(Variable var) {
		VariableState state = var.get();

		if(state.cacheOnCopy == null) {
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
			case FlatType.CtorApp1(_, Seq<Variable> args) -> {
				args.forEach(arg -> restore(arg));
			}
			case FlatType.Fun1(Variable arg, Variable ret) -> {
				restore(arg);
				restore(ret);
			}
			case FlatType.Record1(Variable row) ->
				// 内包するrow変数をrestore
				restore(row);
			case FlatType.Row1(Seq<RecordField<Variable>> fields, Optional<Variable> extension) -> {
				fields.forEach(field -> restore(field.value()));
				extension.ifPresent(ext -> restore(ext));
			}
			}
		}
		case Content.Error() -> {}
		}
	}
}