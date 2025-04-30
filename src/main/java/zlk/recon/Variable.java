package zlk.recon;

import static zlk.util.ErrorUtils.todo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import zlk.common.Type;
import zlk.common.id.Id;
import zlk.recon.constraint.Content;
import zlk.recon.constraint.Content.FlexVar;
import zlk.recon.constraint.Content.Structure;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public class Variable extends UnionFind<TypeVarState, Variable> implements PrettyPrintable {

	private static final AtomicInteger idCounter = new AtomicInteger();

	public Variable(TypeVarState state) {
		super(state);
	}



	public static Variable unbounded() {
		return new Variable(
				new TypeVarState(
						new FlexVar(idCounter.getAndIncrement(), Optional.empty()),
						0));
	}

	public Variable(Content con, int letRank) {
		this(new TypeVarState(con, letRank));
	}

	public Variable(String name, int letRank) {
		this(new TypeVarState(
				new FlexVar(idCounter.getAndIncrement(), Optional.of(name)),
				letRank));
	}

//	public Variable instanciate(int letRank) {
//		Variable result = makeCopy(letRank);
//		clearCache();
//		return result;
//	}
//	private Variable makeCopy(int maxRank) {
//		TypeVarState s = get();
//
//		if(s.cacheOnCopy != null) {
//			return s.cacheOnCopy;
//		}
//		if(!s.isQuantified()) {
//			return this;
//		}
//
//		Function<Co
//	}

	public Type toType() {
		return switch(get().content) {
		case FlexVar(int id, Optional<String> maybeName) ->
			new Type.Var(maybeName.orElseGet(() -> "?"+id+"?"));
		case Structure(FlatType flatType) ->
			flatType.toType();
		case Content.Error _ ->
			throw new RuntimeException("error type cannot convert");
		};
	}

	public Type toAnnotation() {
		Map<String, Variable> userNames = new HashMap<>();
		accumlateVarNames(userNames);
		return toAnnotation(userNames);
	}

	private Type toAnnotation(Map<String, Variable> userNames) {
		return switch(get().content) {
		case FlexVar(int id, Optional<String> maybeName) ->
			maybeName.map(name -> new Type.Var(name))
					.orElseGet(() -> todo());
//			if(name == null) {
//				System.out.println(this);
//				name = todo();
//				set(new Descriptor(new FlexVar(flex.id(), name), dtor.rank, dtor.mark));
//			}
//			return new TyVar(name);
		case Structure(FlatType.CtorApp1(Id id, _)) ->
			new Type.Atom(id);
		case Structure(FlatType.Fun1(Variable arg, Variable ret)) ->
			Type.arrow(List.of(arg.toAnnotation(), ret.toAnnotation()));
		case Content.Error _ ->
			throw new RuntimeException("error type cannot convert");
		};
	}

	/**
	 * この変数の名前を指定されたMapに登録する
	 * @param takenNames
	 * @return
	 */
	private void accumlateVarNames(Map<String, Variable> takenNames) {
		switch(get().content) {
		case FlexVar(int _, Optional<String> maybeName) -> {
			maybeName.ifPresent(name -> {
				if(!takenNames.containsKey(name)) {
					takenNames.put(name, this);
				}
			});
		}
		case Structure(FlatType.CtorApp1(_, List<Variable> args)) ->
			args.forEach(arg -> arg.accumlateVarNames(takenNames));
		case Structure(FlatType.Fun1(Variable arg, Variable ret)) -> {
			arg.accumlateVarNames(takenNames);
			ret.accumlateVarNames(takenNames);
		}
		case Content.Error() -> {}
		}
	}

	/**
	 * 無限型の出現をエラーにまとめる
	 */
	public boolean occurs() {
		return occursHelp(new ArrayList<>(), false);
	}
	private boolean occursHelp(List<Variable> seen, boolean foundCycle) {
		if(seen.contains(this)) {  // 再出現を確認するのは内部の等価性ではない
			return true;
		}

		seen.add(this);
		return switch(get().content) {
		case FlexVar _ ->
			foundCycle;
		case Structure(FlatType.CtorApp1(_, List<Variable> args)) ->
			args.stream().anyMatch(arg -> arg.occursHelp(seen, foundCycle));
		case Structure(FlatType.Fun1(Variable arg, Variable ret)) ->
			arg.occursHelp(seen, ret.occursHelp(new ArrayList<>(seen), foundCycle));
		case Content.Error() ->
			foundCycle;
		};
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(get().content);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		pp(sb);
		return sb.toString();
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
class TypeVarState {
	final Content content;
	int rank;
	Variable cacheOnCopy;
	int gMark;

	public TypeVarState(Content content, int letRank) {
		this.content = content;
		this.rank = letRank;
		this.cacheOnCopy = null;
		this.gMark = 0;
	}

	boolean isQuantified() {
		return rank == 0;
	}
}
