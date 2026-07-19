package zlk.patterncheck;

import java.util.IdentityHashMap;
import java.util.Optional;

import zlk.common.Location;
import zlk.common.RecordField;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.idcalc.ExpOrPattern;
import zlk.idcalc.IcCaseBranch;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcPattern;
import zlk.idcalc.IcPattern.Arg;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

// http://moscova.inria.fr/~maranget/papers/warn/warn.pdf

public final class PatternChecker {
	public static Seq<PcError> check(
			IcModule module,
			IdentityHashMap<ExpOrPattern, Type> nodeTypes) {
		PatternChecker checker = new PatternChecker(module, nodeTypes);
		module.decls().forEach(decl -> {
			decl.body().walk(exp -> {
				if(exp instanceof IcExp.IcCase caseExp) {
					checker.check(caseExp);
				}
			});
		});
		return checker.errors.toSeq();
	}

	private final IdMap<UnionInfo> unionInfos;
	private final IdMap<Id> ctorToUnion;
	private final SeqBuffer<PcError> errors;
	private final IdentityHashMap<ExpOrPattern, Type> nodeTypes;
	private PatternChecker(
			IcModule module,
			IdentityHashMap<ExpOrPattern, Type> nodeTypes) {
		this.unionInfos = new IdMap<>();
		this.ctorToUnion = new IdMap<>();
		// 組込み
		Id boolId = Type.BOOL.id();
		UnionInfo boolInfo = new UnionInfo(
				boolId,
				Seq.of(
						new CtorInfo(boolId, Id.intern("Basic.False"), 0),
						new CtorInfo(boolId, Id.intern("Basic.True"), 0)));
		unionInfos.put(boolId, boolInfo);
		boolInfo.ctors.forEach(ctor -> ctorToUnion.put(ctor.id, boolId));
		// コード
		module.types().forEach(tyDecl -> {
			Id unionId = tyDecl.id();
			Seq<CtorInfo> ctors = tyDecl.ctors()
					.map(ctor -> new CtorInfo(unionId, ctor.id(), ctor.args().size()));
			unionInfos.put(unionId, new UnionInfo(unionId, ctors));
			ctors.forEach(ctor -> ctorToUnion.put(ctor.id(), unionId));
		});

		this.errors = new SeqBuffer<>();
		this.nodeTypes = nodeTypes;
	}

	private record CtorInfo(
			Id unionId,
			Id id,
			int arity
	) {}

	private record UnionInfo(
			Id id,
			Seq<CtorInfo> ctors
	) {}


	/**
	 * ケース式のパターンの冗長性と網羅性を検査しエラーに記録する
	 * @param exp ケース式
	 */
	private void check(IcExp.IcCase exp) {
		Seq<IcPattern> patterns = exp.branches().map(IcCaseBranch::pattern);
		Location overallLoc = exp.loc();
		SeqBuffer<Seq<PcPattern>> usefulRows = new SeqBuffer<>();


		// 冗長パターンチェック
		patterns.forEachIndexed((caseIdx, pat) -> {
			Seq<PcPattern> row = Seq.of(toPcPattern(pat, nodeTypes.get(pat)));

			// 上の行まで完全に覆われている行は冗長
			if(findWitness(row, usefulRows).isEmpty()) {
				errors.add(new PcError.Redundant(overallLoc, pat.loc(), caseIdx));
			} else {
				usefulRows.add(row);
			}
		});

		// 網羅チェック
		findWitness(anythings(1), usefulRows).ifPresent(missing -> {
			errors.add(new PcError.Incomplete(overallLoc, missing));
		});
	}

	private PcPattern toPcPattern(IcPattern pattern, Type expected) {
		return switch(pattern) {
		case IcPattern.Wildcard(Location _) -> PcPattern.Anything.SINGLETON;
		case IcPattern.Var(Id _, Location _) -> PcPattern.Anything.SINGLETON;
		case IcPattern.Dector(IcExp.IcVarCtor ctor, Seq<Arg> args, Location _) -> {
			Id ctorId = ctor.id();
			Id unionId = ctorToUnion.get(ctorId);
			yield new PcPattern.Ctor(
					unionId,
					ctorId,
					args.map(arg -> toPcPattern(
							arg.pattern(), nodeTypes.get(arg.pattern()))));
		}
		case IcPattern.Record(Seq<IcPattern.RecordField> fields, Location _) -> {
			if(!(expected instanceof Type.Record(Seq<RecordField<Type>> shape))) {
				throw new IllegalArgumentException("record pattern has non-record type: " + expected);
			}
			StringBuilder key = new StringBuilder("$record$");
			shape.forEach(field -> key
					.append(field.name().length()).append('$').append(field.name()));
			Id productId = Id.intern(key.toString());
			CtorInfo product = new CtorInfo(productId, productId, shape.size());
			if(!unionInfos.containsKey(productId)) {
				unionInfos.put(productId, new UnionInfo(productId, Seq.of(product)));
			}
			Seq<PcPattern> productArgs = shape.map(shapeField -> fields
					.findFirst(field -> field.name().equals(shapeField.name()))
					.map(field -> toPcPattern(field.pattern(), shapeField.value()))
					.orElse(PcPattern.Anything.SINGLETON));
			if(productArgs.size() != shape.size()) {
				throw new IllegalStateException(
						"record product arity mismatch: " + shape.size() + " vs " + productArgs.size());
			}
			yield new PcPattern.Ctor(productId, productId, productArgs);
		}
		};
	}

	/**
	 * {@code vector}に含まれるが{@code matrix}に含まれないwitnessがあれば返す
	 * @param vector
	 * @param matrix
	 * @return
	 */
	private Optional<Seq<PcPattern>> findWitness(
			Seq<PcPattern> vector,
			SeqBuffer<Seq<PcPattern>> matrix
	) {
		if (matrix.isEmpty()) {
			return Optional.of(vector);
		}

		if (vector.isEmpty()) {
			return Optional.empty();
		}

		return switch (vector.head()) {
		case PcPattern.Anything _ -> findWitnessForAnything(vector.tail(), matrix);

		case PcPattern.Ctor ctor -> {
			SeqBuffer<Seq<PcPattern>> specializedMatrix = specializeByCtor(matrix, ctor);

			Seq<PcPattern> specializedVector = Seq.concat(ctor.args(), vector.tail());

			yield findWitness(specializedVector, specializedMatrix)
					.map(witness -> recoverCtor(ctor, witness));
		}

		// TODO: リテラル
		// case SimplePattern.Literal lit -> {
		// List<List<SimplePattern>> specializedMatrix =
		// specializeByLiteral(matrix, lit.value());
		// yield findWitness(specializedMatrix, tail)
		// .map(witness -> prepend(lit, witness));
		// }
		};
	}

	private Optional<Seq<PcPattern>> findWitnessForAnything(
			Seq<PcPattern> tail,
			SeqBuffer<Seq<PcPattern>> matrix
		) {
		Seq<PcPattern.Ctor> seenCtors = seenConstructors(matrix);

		if (seenCtors.isEmpty()) {
			return findWitness(tail, specializeByDefault(matrix))
					.map(witness -> Seq.concat(anythings(1), witness));
		}

		UnionInfo unionInfo = unionInfos.get(seenCtors.head().unionId());

		// 念のため同じunionであることを確認
		Id unionId = unionInfo.id();
		seenCtors.findFirst(ctor -> ctor.unionId() != unionId).ifPresent(ctor -> {
			throw new IllegalStateException(
					"constructors from different unions appear in the same pattern column: "
							+ unionId + " and " + ctor.unionId());
		});

		// 出現していないCtorがあるとき
		if (seenCtors.size() < unionInfo.ctors().size()) {
			// AnythingでCtorが吸収されているかも
			Optional<Seq<PcPattern>> tailWitness = findWitness(tail, specializeByDefault(matrix));
			if (tailWitness.isEmpty()) {
				return Optional.empty();
			}

			Seq<Id> seenCtorIds = seenCtors.map(ctor -> ctor.ctorId());
			CtorInfo missingCtor = unionInfo.ctors()
					.findFirst(ctor -> !seenCtorIds.contains(ctor.id()))
					.get();

			return Optional.of(prepend(
					new PcPattern.Ctor(
							missingCtor.unionId(),
							missingCtor.id(),
							anythings(missingCtor.arity())),
					tailWitness.get()));
		}

		// 全Ctorが出現済み
		for (CtorInfo ctor : unionInfo.ctors()) {
			SeqBuffer<Seq<PcPattern>> specializedMatrix = specializeByCtor(matrix, ctor);

			Seq<PcPattern> specializedVector = Seq.concat(anythings(ctor.arity()), tail);

			Optional<Seq<PcPattern>> witness = findWitness(specializedVector, specializedMatrix);

			if (witness.isPresent()) {
				return Optional.of(recoverCtor(ctor, witness.get()));
			}
		}
		return Optional.empty();
	}

	private static SeqBuffer<Seq<PcPattern>> specializeByCtor(
			SeqBuffer<Seq<PcPattern>> matrix,
			CtorInfo ctor
	) {
		SeqBuffer<Seq<PcPattern>> result = new SeqBuffer<>();

		for (Seq<PcPattern> row : matrix) {
			Optional<Seq<PcPattern>> specialized = specializeRowByCtor(row, ctor);
			specialized.ifPresent(result::add);
		}

		return result;
	}
	private static SeqBuffer<Seq<PcPattern>> specializeByCtor(
			SeqBuffer<Seq<PcPattern>> matrix,
			PcPattern.Ctor ctor
	) {
		return specializeByCtor(matrix, new CtorInfo(ctor.unionId(), ctor.ctorId(), ctor.args().size()));
	}

	private static Optional<Seq<PcPattern>> specializeRowByCtor(Seq<PcPattern> row, CtorInfo ctor) {
		if (row.isEmpty()) {
			throw new IllegalStateException("cannot specialize an empty row");
		}

		return switch (row.head()) {

		case PcPattern.Anything _ -> Optional.of(Seq.concat(anythings(ctor.arity()), row.tail()));

		case PcPattern.Ctor(Id _, Id ctorId, Seq<PcPattern> args) -> {
			if (ctor.id() == ctorId) {
				yield Optional.of(Seq.concat(args, row.tail()));
			} else {
				yield Optional.empty();
			}
		}

		// TODO: リテラル
		// コンストラクタとリテラルは同位置に出現しないためエラー
		};
	}

	private static SeqBuffer<Seq<PcPattern>> specializeByDefault(SeqBuffer<Seq<PcPattern>> matrix) {
		SeqBuffer<Seq<PcPattern>> result = new SeqBuffer<>();

		for (Seq<PcPattern> row : matrix) {
			Optional<Seq<PcPattern>> specialized = specializeRowByDefault(row);
			specialized.ifPresent(result::add);
		}

		return result;
	}

	private static Optional<Seq<PcPattern>> specializeRowByDefault(Seq<PcPattern> row) {
		if (row.isEmpty()) {
			return Optional.empty();
		}

		return switch (row.head()) {
		case PcPattern.Anything _ -> Optional.of(row.tail());
		case PcPattern.Ctor _ -> Optional.empty();

		// TODO: リテラル
		// コンストラクタと同様にデフォルトはない
		};
	}

	private static Seq<PcPattern.Ctor> seenConstructors(SeqBuffer<Seq<PcPattern>> matrix) {
		SeqBuffer<PcPattern.Ctor> result = new SeqBuffer<>();

		matrix.forEach(row -> {
			if(!row.isEmpty() && row.head() instanceof PcPattern.Ctor ctor) {
				result.add(ctor);
			}
		});

		return result.toSeq();
	}

	private static Seq<PcPattern> recoverCtor(CtorInfo ctor, Seq<PcPattern> specializedWitness) {
		if (specializedWitness.size() < ctor.arity()) {
			throw new IllegalStateException(
					"witness is shorter than constructor arity: "
							+ ctor.id() + " requires " + ctor.arity()
							+ " but got " + specializedWitness.size()
							+ ": " + specializedWitness);
		}

		Seq<PcPattern> args = specializedWitness.take(ctor.arity());
		Seq<PcPattern> rest = specializedWitness.drop(ctor.arity());

		return prepend(new PcPattern.Ctor(ctor.unionId(), ctor.id(), args), rest);
	}
	private static Seq<PcPattern> recoverCtor(
			PcPattern.Ctor ctor,
			Seq<PcPattern> specializedWitness
	) {
		return recoverCtor(new CtorInfo(ctor.unionId(), ctor.ctorId(), ctor.args().size()), specializedWitness);
	}

	private static Seq<PcPattern> anythings(int size) {
		return Seq.repeat(PcPattern.Anything.SINGLETON, size);
	}

	// 特に速くはないが読み易さのため
	private static <E> Seq<E> prepend(E head, Seq<E> tail) {
		SeqBuffer<E> result = new SeqBuffer<>(tail.size() + 1);
		result.add(head);
		result.addAll(tail);
		return result.toSeq();
	}
}
