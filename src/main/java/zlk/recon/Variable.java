package zlk.recon;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import zlk.common.RecordField;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.recon.FlatType.CtorApp1;
import zlk.recon.FlatType.Fun1;
import zlk.recon.FlatType.Record1;
import zlk.recon.FlatType.Row1;
import zlk.recon.constraint.Content;
import zlk.recon.constraint.Content.FlexVar;
import zlk.recon.constraint.Content.RigidVar;
import zlk.recon.constraint.Content.Structure;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public class Variable extends UnionFind<VariableState, Variable> implements PrettyPrintable {

	/** 型変数の種．TYPEは通常の型，ROWはrow変数． */
	public enum Kind { TYPE, ROW }

	public Variable(VariableState state) {
		super(state);
	}

	public static Variable ofFlex(int flexId, Optional<String> maybeName, int letRank) {
		return new Variable(new VariableState(new FlexVar(flexId, maybeName), letRank, Kind.TYPE));
	}

	public static Variable ofFlex(int flexId, Optional<String> maybeName, int letRank, Kind kind) {
		return new Variable(new VariableState(new FlexVar(flexId, maybeName), letRank, kind));
	}

	public static Variable ofRigid(String name) {
		return new Variable(new VariableState(new RigidVar(name), 0, Kind.TYPE));
	}

	public static Variable ofRigid(String name, Kind kind) {
		return new Variable(new VariableState(new RigidVar(name), 0, kind));
	}

	public Variable(Content con, int letRank) {
		this(new VariableState(con, letRank, Kind.TYPE));
	}

	public Variable(Content con, int letRank, Kind kind) {
		this(new VariableState(con, letRank, kind));
	}

	/** この変数の種を返す． */
	public Kind kind() {
		return get().kind;
	}

	/**
	 * この変数の推奨表示名を返す．flexなら注釈名，rigidならその名前．
	 * structureやerrorでは空．
	 */
	public Optional<String> getPreferredName() {
		return switch(get().content) {
		case FlexVar(int _, Optional<String> name) -> name;
		case RigidVar(String name) -> Optional.of(name);
		default -> Optional.empty();
		};
	}

	/**
	 * ROW-kind rootの変数をType.Rowへ変換する．
	 * 未解決flex/rigidはType.RowVarへ，解決済みRow1はfields+extensionのType.Rowへ．
	 */
	public Type.Row toRow(IntFunction<String> namer) {
		return toRow(namer, new SeqBuffer<>());
	}

	private Type.Row toRow(IntFunction<String> namer, SeqBuffer<Variable> seen) {
		if(kind() != Kind.ROW) {
			throw new IllegalStateException("only ROW-kind variables can be converted to Type.Row");
		}
		if(seen.contains(this)) {
			throw new IllegalStateException("recursive row cannot be converted to a stable type");
		}
		seen.add(this);
		return switch(get().content) {
		case FlexVar(int id, Optional<String> name) ->
			new Type.Row(Seq.of(), Optional.of(new Type.RowVar(name.orElse(namer.apply(id)))));
		case RigidVar(String name) ->
			new Type.Row(Seq.of(), Optional.of(new Type.RowVar(name)));
		case Structure(FlatType.Row1 row1) -> {
			Seq<RecordField<Type>> fields = row1.fields().map(
					field -> new RecordField<>(field.name(), field.value().toType(namer)));
			if(row1.extension().isEmpty()) {
				yield new Type.Row(fields, Optional.empty());
			}
			Type.Row tail = row1.extension().get().toRow(namer, new SeqBuffer<>(seen));
			yield new Type.Row(
					RecordField.canonicalize(Seq.concat(fields, tail.fields())),
					tail.extension());
		}
		default ->
			throw new RuntimeException("ROW-kind variable does not hold a Row1: " + get().content);
		};
	}

	/**
	 * この変数の注釈用の型を生成する
	 * @return 型
	 */
	public Type toType() {
		// TODO: flexの推奨名を反映する
		// TODO: rigidとの被り防止
		// TODO: 自動の命名アルゴリズム含めもう少し何とか
		return this.toType(new IntFunction<String>() {
			Map<Integer, String> named = new HashMap<>();
			@Override
			public String apply(int value) {
				return named.computeIfAbsent(value, _ -> named.size() > 'z' - 'a'
						? "ty" + (named.size() - ('z' - 'a'))
						: "" + (char)('a' + named.size()));
			}
		});
	}

	/**
	 * namerでflex idから名前を生成しつつTypeへ変換する．
	 * ROW-kind rootのVariable（Row1を保持）はTypeとして直接返せないため例外．
	 * Record1の場合は内包するrow変数のtoRow(namer)でType.Recordを構築する．
	 */
	Type toType(IntFunction<String> namer) {
		return switch(get().content) {
		case FlexVar(int id, Optional<String> _) -> new Type.Var(namer.apply(id));
		case RigidVar(String name) -> new Type.Var(name);
		case Structure(FlatType.CtorApp1(Id id, Seq<Variable> args)) -> {
			if(id.equals(Type.BOOL.id())) {
				yield Type.BOOL;
			}
			if(id.equals(Type.I32.id())) {
				yield Type.I32;
			}
			yield new Type.CtorApp(id, args.map(arg -> arg.toType(namer)));
		}
		case Structure(FlatType.Fun1(Variable arg, Variable ret)) ->
			Type.arrow(Seq.of(arg.toType(namer), ret.toType(namer)));
		case Structure(FlatType.Record1(Variable row)) ->
			new Type.Record(row.toRow(namer));
		case Structure(FlatType.Row1 _) ->
			throw new RuntimeException("ROW-kind variable cannot be converted to Type directly; use toRow()");
		case Content.Error _ ->
			throw new RuntimeException("error type cannot convert");
		};
	}

	/**
	 * 無限型の出現をエラーにまとめる
	 */
	public boolean occurs() {
		return occursHelp(new SeqBuffer<>(), false);
	}
	private boolean occursHelp(SeqBuffer<Variable> seen, boolean foundCycle) {
		if(seen.contains(this)) {  // 再出現を確認するのは内部の等価性ではない
			return true;
		}

		seen.add(this);
		return switch(get().content) {
		case FlexVar _ -> foundCycle;
		case RigidVar _ -> foundCycle;
		case Structure(FlatType.CtorApp1(_, Seq<Variable> args)) ->
			args.anyMatch(arg -> arg.occursHelp(seen, foundCycle));
		case Structure(FlatType.Fun1(Variable arg, Variable ret)) ->
			arg.occursHelp(seen, ret.occursHelp(new SeqBuffer<>(seen), foundCycle));
		case Structure(FlatType.Record1(Variable row)) ->
			row.occursHelp(seen, foundCycle);
		case Structure(FlatType.Row1(Seq<RecordField<Variable>> fields, Optional<Variable> extension)) ->
			fields.anyMatch(field -> field.value().occursHelp(seen, foundCycle))
			|| (extension.isPresent() && extension.get().occursHelp(seen, foundCycle));
		case Content.Error() -> foundCycle;
		};
	}

	/**
	 * この変数の内部を再帰的に訪問し，各部分に対してactionを行う．
	 * 訪問の判定にgMarkをmarkする．
	 *
	 * @param mark
	 * @param action
	 */
	public void markAndWalk(int mark, Consumer<Variable> action) {
		VariableState s = get();
		if(s.gMark == mark) {
			return;
		}
		action.accept(this);
		s.gMark = mark;
		switch(s.content) {
		case Content.Structure(FlatType.CtorApp1(_, Seq<Variable> args)) -> {
			args.forEach(arg -> arg.markAndWalk(mark, action));
		}
		case Content.Structure(FlatType.Fun1(Variable a, Variable b)) -> {
			a.markAndWalk(mark, action);
			b.markAndWalk(mark, action);
		}
		case Content.Structure(FlatType.Record1(Variable row)) -> {
			row.markAndWalk(mark, action);
		}
		case Content.Structure(FlatType.Row1(Seq<RecordField<Variable>> fields, Optional<Variable> extension)) -> {
			fields.forEach(field -> field.value().markAndWalk(mark, action));
			extension.ifPresent(ext -> ext.markAndWalk(mark, action));
		}
		default -> {}
		}
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(get().content);
	}

	@Override
	public String toString() {
		return buildString();
	}
}

/**
 * 推論中の型変数が指す状態．
 *
 * @param content 現在の構造や束縛（自由変数や関数型など）
 * @param rank let多相のネストレベル 0であれば汎化された型
 * @param cacheOnCopy 具体化時のキャッシュ用 普段はnull
 * @param gMark 汎化時に外部から参照されているかを記録する用
 * @param kind この変数の種（TYPE または ROW）
 */
class VariableState {
	final Content content;
	int rank;
	Variable cacheOnCopy;
	int gMark;
	final Variable.Kind kind;
	/**
	 * row変数(root)が含んではならないlabel集合．
	 * TYPE-kindでは常に空．ROW-kindでは前方参照を許さないlabelを保持する．
	 * mutableだが，package-private API経由でのみ更新する．
	 */
	Set<String> forbiddenLabels;

	public VariableState(Content content, int letRank, Variable.Kind kind) {
		this(content, letRank, kind, Collections.emptySet());
	}

	/**
	 * forbidden labelsを明示的に指定するconstructor．
	 * Row1をROW rootへ構築する場合，known field namesとroot既存forbiddenの衝突を検査し，
	 * tailへfield namesを伝播する．
	 */
	public VariableState(Content content, int letRank, Variable.Kind kind, Set<String> forbiddenLabels) {
		this.content = content;
		this.rank = letRank;
		this.cacheOnCopy = null;
		this.gMark = 0;
		this.kind = Objects.requireNonNull(kind);
		validateStructureKind(content, kind);
		Set<String> baseForbidden = new HashSet<>(
				forbiddenLabels == null ? Collections.emptySet() : forbiddenLabels);
		if(kind == Variable.Kind.ROW && content instanceof Structure s
				&& s.flatType() instanceof FlatType.Row1 row1) {
			// 既知field namesとroot既存forbiddenの衝突をreject
			for(RecordField<Variable> field : row1.fields()) {
				if(baseForbidden.contains(field.name())) {
					throw new Mismatch(
							"row field '" + field.name() + "' violates lacks constraint on tail");
				}
			}
			// 既知field namesに加えてrootのforbiddenをnested tailへ伝播する．
			Set<String> propagated = new HashSet<>(baseForbidden);
			for(RecordField<Variable> field : row1.fields()) {
				propagated.add(field.name());
			}
			row1.extension().ifPresent(ext -> propagateForbiddens(ext, propagated));
		} else if(kind != Variable.Kind.ROW && !baseForbidden.isEmpty()) {
			// TYPE-kindではforbiddenを保持しない
			baseForbidden = new HashSet<>();
		}
		this.forbiddenLabels = Collections.unmodifiableSet(baseForbidden);
	}

	/** tail変数rootへforbidden labelsを伝播する．tailがすでにRow1 structureを持つ場合は再検査する． */
	private static void propagateForbiddens(Variable tail, Set<String> forbidden) {
		VariableState ts = tail.get();
		Set<String> merged = new HashSet<>(ts.forbiddenLabels);
		merged.addAll(forbidden);
		// tailがすでにStructure(Row1)の場合は，そのfieldsとも衝突検査
		if(ts.content instanceof Structure s && s.flatType() instanceof FlatType.Row1 tailRow) {
			for(RecordField<Variable> field : tailRow.fields()) {
				if(merged.contains(field.name())) {
					throw new Mismatch(
							"row field '" + field.name() + "' violates lacks constraint on parent tail");
				}
			}
			Set<String> nestedForbidden = new HashSet<>(merged);
			for(RecordField<Variable> field : tailRow.fields()) {
				nestedForbidden.add(field.name());
			}
			// nested tailにはこのrootの既知fieldも追加して伝播する．
			tailRow.extension().ifPresent(nestedExt ->
					propagateForbiddens(nestedExt, nestedForbidden));
		}
		ts.forbiddenLabels = Collections.unmodifiableSet(merged);
	}

	/** この変数のforbidden label集合．immutable view． */
	public Set<String> forbiddenLabels() {
		return forbiddenLabels;
	}

	/**
	 * rootのkindとContent.Structure の整合性，および子変数のkindを検証する．
	 * - TYPE root + Row1 はreject
	 * - ROW  root + CtorApp1/Fun1/Record1 はreject
	 * - Record1.row はROW
	 * - Row1.field value はTYPE，Row1.extension はROW
	 * - CtorApp1.args はTYPE，Fun1.arg/ret はTYPE
	 * - FlexVar/RigidVar/Error はroot kindどちらも可
	 */
	private static void validateStructureKind(Content content, Variable.Kind kind) {
		if(!(content instanceof Structure s)) {
			return;
		}
		FlatType ft = s.flatType();
		switch(ft) {
		case Row1 _ -> {
			if(kind != Variable.Kind.ROW) {
				throw new IllegalArgumentException(
						"Row1 must be wrapped in ROW-kind root: " + kind);
			}
		}
		case CtorApp1 _, Fun1 _, Record1 _ -> {
			if(kind != Variable.Kind.TYPE) {
				throw new IllegalArgumentException(
						"CtorApp1/Fun1/Record1 must be wrapped in TYPE-kind root: " + kind);
			}
		}
		}
		switch(ft) {
		case CtorApp1(Id _, Seq<Variable> args) -> {
			for(Variable arg : args) {
				assertKind(arg, Variable.Kind.TYPE, "CtorApp1 arg");
			}
		}
		case Fun1(Variable arg, Variable ret) -> {
			assertKind(arg, Variable.Kind.TYPE, "Fun1 arg");
			assertKind(ret, Variable.Kind.TYPE, "Fun1 ret");
		}
		case Record1(Variable row) -> {
			assertKind(row, Variable.Kind.ROW, "Record1 row");
		}
		case Row1(Seq<RecordField<Variable>> fields, Optional<Variable> extension) -> {
			for(RecordField<Variable> f : fields) {
				assertKind(f.value(), Variable.Kind.TYPE, "Row1 field value");
			}
			extension.ifPresent(ext -> assertKind(ext, Variable.Kind.ROW, "Row1 extension"));
		}
		}
	}

	private static void assertKind(Variable v, Variable.Kind expected, String ctx) {
		if(v.kind() != expected) {
			throw new IllegalArgumentException(
					ctx + " must be " + expected + " but was " + v.kind());
		}
	}

	/** 互換性のためのTYPE既定overload． */
	public VariableState(Content content, int letRank) {
		this(content, letRank, Variable.Kind.TYPE);
	}

	boolean isQuantified() {
		return rank == 0;
	}
}