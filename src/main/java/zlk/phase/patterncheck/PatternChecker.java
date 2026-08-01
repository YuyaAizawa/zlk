package zlk.phase.patterncheck;

import java.util.Optional;

import zlk.common.Location;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.diagnostic.Diagnostic;
import zlk.diagnostic.PatternWitness;
import zlk.ir.idcalc.IcExp;
import zlk.ir.idcalc.IcModule;
import zlk.ir.idcalc.IcPattern;
import zlk.ir.typing.CaseTyping;
import zlk.ir.typing.PatternTyping;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

// http://moscova.inria.fr/~maranget/papers/warn/warn.pdf

public final class PatternChecker {
	public static Seq<Diagnostic> check(
			IcModule module,
			Seq<CaseTyping<Type>> caseTypings) {
		PatternChecker checker = new PatternChecker(module);
		caseTypings.forEach(checker::check);
		return checker.errors.toSeq();
	}

	private final IdMap<UnionInfo> unionInfos;
	private final IdMap<Id> ctorToUnion;
	private final IdMap<Seq<String>> recordFields;
	private final SeqBuffer<Diagnostic> errors;
	private PatternChecker(IcModule module) {
		this.unionInfos = new IdMap<>();
		this.ctorToUnion = new IdMap<>();
		this.recordFields = new IdMap<>();
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
	 * @param caseTyping case式のbranch patternと解決済み型
	 */
	private void check(CaseTyping<Type> caseTyping) {
		Location overallLoc = caseTyping.loc();
		SeqBuffer<Seq<Pattern>> usefulRows = new SeqBuffer<>();


		// 冗長パターンチェック
		caseTyping.patterns().forEachIndexed((caseIdx, pattern) -> {
			Seq<Pattern> row = Seq.of(toPcPattern(pattern));

			// 上の行まで完全に覆われている行は冗長
			if(findWitness(row, usefulRows).isEmpty()) {
				errors.add(new Diagnostic.RedundantPattern(
						overallLoc, pattern.pattern().loc(), caseIdx));
			} else {
				usefulRows.add(row);
			}
		});

		// 網羅チェック
		findWitness(anythings(1), usefulRows).ifPresent(missing ->
			errors.add(new Diagnostic.IncompletePattern(overallLoc, missing.map(this::toWitness))));
	}

	private Pattern toPcPattern(PatternTyping<Type> typing) {
		IcPattern pattern = typing.pattern();
		return switch(pattern) {
		case IcPattern.Wildcard(Location _) -> Pattern.Anything.SINGLETON;
		case IcPattern.Var(Id _, Location _) -> Pattern.Anything.SINGLETON;
		case IcPattern.Dector(IcExp.IcVarCtor ctor, _, Location _) -> {
			Id ctorId = ctor.id();
			Id unionId = ctorToUnion.get(ctorId);
			yield new Pattern.Ctor(
					unionId,
					ctorId,
					typing.children().map(this::toPcPattern));
		}
		case IcPattern.Record(Seq<IcPattern.RecordField> fields, Location _) -> {
			if(!(typing.type() instanceof Type.Record(Type.Row row))) {
				throw new IllegalArgumentException(
						"record pattern has non-record type: " + typing.type());
			}
			StringBuilder key = new StringBuilder("$record$");
			row.fields().forEach(field -> key
					.append(field.name().length()).append('$').append(field.name()));
			Id productId = Id.intern(key.toString());
			CtorInfo product = new CtorInfo(productId, productId, row.fields().size());
			if(!unionInfos.containsKey(productId)) {
				unionInfos.put(productId, new UnionInfo(productId, Seq.of(product)));
				recordFields.put(productId, row.fields().map(field -> field.name()));
			}
			Seq<Pattern> productArgs = row.fields().map(shapeField ->
					toPcRecordField(shapeField.name(), fields, typing.children()));
			if(productArgs.size() != row.fields().size()) {
				throw new IllegalStateException(
						"record product arity mismatch: " + row.fields().size() + " vs " + productArgs.size());
			}
			yield new Pattern.Ctor(productId, productId, productArgs);
		}
		};
	}

	private Pattern toPcRecordField(
			String name,
			Seq<IcPattern.RecordField> fields,
			Seq<PatternTyping<Type>> children) {
		for(int i = 0; i < fields.size(); i++) {
			if(fields.at(i).name().equals(name)) {
				return toPcPattern(children.at(i));
			}
		}
		return Pattern.Anything.SINGLETON;
	}

	private PatternWitness toWitness(Pattern pattern) {
		return switch(pattern) {
		case Pattern.Anything _ -> PatternWitness.Anything.SINGLETON;
		case Pattern.Ctor(Id _, Id ctorId, Seq<Pattern> args) -> {
			Seq<String> names = recordFields.getOrNull(ctorId);
			if(names == null) {
				yield new PatternWitness.Ctor(ctorId.canonicalName(), args.map(this::toWitness));
			}
			if(names.size() != args.size()) {
				throw new IllegalStateException(
						"record witness arity mismatch: " + names.size() + " vs " + args.size());
			}
			SeqBuffer<PatternWitness.Field> fields = new SeqBuffer<>();
			for(int i = 0; i < names.size(); i++) {
				fields.add(new PatternWitness.Field(names.at(i), toWitness(args.at(i))));
			}
			yield new PatternWitness.Record(fields.toSeq());
		}
		};
	}

	/**
	 * {@code vector}に含まれるが{@code matrix}に含まれないwitnessがあれば返す
	 * @param vector
	 * @param matrix
	 * @return
	 */
	private Optional<Seq<Pattern>> findWitness(
			Seq<Pattern> vector,
			SeqBuffer<Seq<Pattern>> matrix
	) {
		if (matrix.isEmpty()) {
			return Optional.of(vector);
		}

		if (vector.isEmpty()) {
			return Optional.empty();
		}

		return switch (vector.head()) {
		case Pattern.Anything _ -> findWitnessForAnything(vector.tail(), matrix);

		case Pattern.Ctor ctor -> {
			SeqBuffer<Seq<Pattern>> specializedMatrix = specializeByCtor(matrix, ctor);

			Seq<Pattern> specializedVector = Seq.concat(ctor.args(), vector.tail());

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

	private Optional<Seq<Pattern>> findWitnessForAnything(
			Seq<Pattern> tail,
			SeqBuffer<Seq<Pattern>> matrix
		) {
		Seq<Pattern.Ctor> seenCtors = seenConstructors(matrix);

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
			Optional<Seq<Pattern>> tailWitness = findWitness(tail, specializeByDefault(matrix));
			if (tailWitness.isEmpty()) {
				return Optional.empty();
			}

			Seq<Id> seenCtorIds = seenCtors.map(ctor -> ctor.ctorId());
			CtorInfo missingCtor = unionInfo.ctors()
					.findFirst(ctor -> !seenCtorIds.contains(ctor.id()))
					.get();

			return Optional.of(prepend(
					new Pattern.Ctor(
							missingCtor.unionId(),
							missingCtor.id(),
							anythings(missingCtor.arity())),
					tailWitness.get()));
		}

		// 全Ctorが出現済み
		for (CtorInfo ctor : unionInfo.ctors()) {
			SeqBuffer<Seq<Pattern>> specializedMatrix = specializeByCtor(matrix, ctor);

			Seq<Pattern> specializedVector = Seq.concat(anythings(ctor.arity()), tail);

			Optional<Seq<Pattern>> witness = findWitness(specializedVector, specializedMatrix);

			if (witness.isPresent()) {
				return Optional.of(recoverCtor(ctor, witness.get()));
			}
		}
		return Optional.empty();
	}

	private static SeqBuffer<Seq<Pattern>> specializeByCtor(
			SeqBuffer<Seq<Pattern>> matrix,
			CtorInfo ctor
	) {
		SeqBuffer<Seq<Pattern>> result = new SeqBuffer<>();

		for (Seq<Pattern> row : matrix) {
			Optional<Seq<Pattern>> specialized = specializeRowByCtor(row, ctor);
			specialized.ifPresent(result::add);
		}

		return result;
	}
	private static SeqBuffer<Seq<Pattern>> specializeByCtor(
			SeqBuffer<Seq<Pattern>> matrix,
			Pattern.Ctor ctor
	) {
		return specializeByCtor(matrix, new CtorInfo(ctor.unionId(), ctor.ctorId(), ctor.args().size()));
	}

	private static Optional<Seq<Pattern>> specializeRowByCtor(Seq<Pattern> row, CtorInfo ctor) {
		if (row.isEmpty()) {
			throw new IllegalStateException("cannot specialize an empty row");
		}

		return switch (row.head()) {

		case Pattern.Anything _ -> Optional.of(Seq.concat(anythings(ctor.arity()), row.tail()));

		case Pattern.Ctor(Id _, Id ctorId, Seq<Pattern> args) -> {
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

	private static SeqBuffer<Seq<Pattern>> specializeByDefault(SeqBuffer<Seq<Pattern>> matrix) {
		SeqBuffer<Seq<Pattern>> result = new SeqBuffer<>();

		for (Seq<Pattern> row : matrix) {
			Optional<Seq<Pattern>> specialized = specializeRowByDefault(row);
			specialized.ifPresent(result::add);
		}

		return result;
	}

	private static Optional<Seq<Pattern>> specializeRowByDefault(Seq<Pattern> row) {
		if (row.isEmpty()) {
			return Optional.empty();
		}

		return switch (row.head()) {
		case Pattern.Anything _ -> Optional.of(row.tail());
		case Pattern.Ctor _ -> Optional.empty();

		// TODO: リテラル
		// コンストラクタと同様にデフォルトはない
		};
	}

	private static Seq<Pattern.Ctor> seenConstructors(SeqBuffer<Seq<Pattern>> matrix) {
		SeqBuffer<Pattern.Ctor> result = new SeqBuffer<>();

		matrix.forEach(row -> {
			if(!row.isEmpty() && row.head() instanceof Pattern.Ctor ctor) {
				result.add(ctor);
			}
		});

		return result.toSeq();
	}

	private static Seq<Pattern> recoverCtor(CtorInfo ctor, Seq<Pattern> specializedWitness) {
		if (specializedWitness.size() < ctor.arity()) {
			throw new IllegalStateException(
					"witness is shorter than constructor arity: "
							+ ctor.id() + " requires " + ctor.arity()
							+ " but got " + specializedWitness.size()
							+ ": " + specializedWitness);
		}

		Seq<Pattern> args = specializedWitness.take(ctor.arity());
		Seq<Pattern> rest = specializedWitness.drop(ctor.arity());

		return prepend(new Pattern.Ctor(ctor.unionId(), ctor.id(), args), rest);
	}
	private static Seq<Pattern> recoverCtor(
			Pattern.Ctor ctor,
			Seq<Pattern> specializedWitness
	) {
		return recoverCtor(new CtorInfo(ctor.unionId(), ctor.ctorId(), ctor.args().size()), specializedWitness);
	}

	private static Seq<Pattern> anythings(int size) {
		return Seq.repeat(Pattern.Anything.SINGLETON, size);
	}

	// 特に速くはないが読み易さのため
	private static <E> Seq<E> prepend(E head, Seq<E> tail) {
		SeqBuffer<E> result = new SeqBuffer<>(tail.size() + 1);
		result.add(head);
		result.addAll(tail);
		return result.toSeq();
	}
}
