package zlk.recon;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import zlk.common.Type;
import zlk.common.id.Id;
import zlk.recon.constraint.Content;
import zlk.recon.constraint.Content.FlexVar;
import zlk.recon.constraint.Content.RigidVar;
import zlk.recon.constraint.Content.Structure;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public class Variable extends UnionFind<VariableState, Variable> implements PrettyPrintable {

	public Variable(VariableState state) {
		super(state);
	}

	public static Variable ofFlex(int flexId, Optional<String> maybeName, int letRank) {
		return new Variable(new VariableState(new FlexVar(flexId, maybeName), letRank));
	}

	public Variable(Content con, int letRank) {
		this(new VariableState(con, letRank));
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
	private Type toType(IntFunction<String> namer) {
		return switch(get().content) {
		case FlexVar(int id, Optional<String> _) -> new Type.Var(namer.apply(id));
		case RigidVar(String name) -> new Type.Var(name);
		case Structure(FlatType.CtorApp1(Id id, Seq<Variable> args)) ->
			new Type.CtorApp(id, args.map(arg -> arg.toType(namer)));
		case Structure(FlatType.Fun1(Variable arg, Variable ret)) ->
			Type.arrow(Seq.of(arg.toType(namer), ret.toType(namer)));
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
 */
class VariableState {
	final Content content;
	int rank;
	Variable cacheOnCopy;
	int gMark;

	public VariableState(Content content, int letRank) {
		this.content = content;
		this.rank = letRank;
		this.cacheOnCopy = null;
		this.gMark = 0;
	}

	boolean isQuantified() {
		return rank == 0;
	}
}
