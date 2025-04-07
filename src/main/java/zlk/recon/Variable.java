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
import zlk.recon.constraint.FlatType;
import zlk.recon.constraint.Content.FlexVar;
import zlk.recon.constraint.Content.Structure;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public class Variable extends UnionFind<Descriptor, Variable> implements PrettyPrintable {
	public static final int NO_RANK = 0;
	public static final int OUTERMOST_RANK = 1;

	public static final int NO_MARK = 2;
	public static final int OCCUERS_MARK = 1;
	public static final int GET_VER_NAMES_MARK = 0;

	private static final AtomicInteger idCounter = new AtomicInteger();
	public static Variable mkFlexVar() {
		return new Variable(new Descriptor(
				new FlexVar(idCounter.getAndIncrement()),
				NO_RANK,
				NO_MARK
		));
	}

	public static Content mkFlexVar(String name) {
		return new FlexVar(idCounter.getAndIncrement(), name);
	}

	public Variable(Descriptor root) {
		super(root);
	}

	public Type toType() {
		return switch(get().content) {
		case FlexVar(int id, Optional<String> maybeName) ->
			new Type.Var(maybeName.orElseGet(() -> "?"+id+"?"));
		case Structure(FlatType flatType) ->
				flatType.toType();
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
		case Structure(FlatType.App1(Id id, _)) ->
			new Type.Atom(id);
		case Structure(FlatType.Fun1(Variable arg, Variable ret)) ->
			Type.arrow(List.of(arg.toAnnotation(), ret.toAnnotation()));
		};
	}

	/**
	 * この変数のIdを指定されたIdMapに登録する
	 * @param takenNames
	 * @return
	 */
	private void accumlateVarNames(Map<String, Variable> takenNames) {
		Descriptor dtor = get();
		if(dtor.mark == GET_VER_NAMES_MARK) {
			return;
		}
		set(new Descriptor(dtor.content, dtor.rank, GET_VER_NAMES_MARK));
		switch(dtor.content) {
		case FlexVar(int id, Optional<String> maybeName) -> {
			maybeName.ifPresent(name -> {
				if(takenNames.containsKey(name)) {
					todo();
				} else {
					takenNames.put(name, this);
				}
			});
		}
		case Structure(FlatType.App1(_, List<Variable> args)) ->
			args.forEach(arg -> arg.accumlateVarNames(takenNames));
		case Structure(FlatType.Fun1(Variable arg, Variable ret)) -> {
			arg.accumlateVarNames(takenNames);
			ret.accumlateVarNames(takenNames);
		}
		}
	}

	public boolean occurs() {
		return occursHelp(new ArrayList<>(), false);
	}
	private boolean occursHelp(List<Variable> seen, boolean foundCycle) {
		// 等価性判定にisSameを使っていないが，元のコードもそうなので多分参照等価をみてる
		if(seen.contains(this)) {
			return true;
		}

		seen.add(this);
		return switch(get().content) {
		case FlexVar _ ->
			foundCycle;
		case Structure(FlatType.App1(_, List<Variable> args)) ->
			args.stream().anyMatch(arg -> arg.occursHelp(seen, foundCycle));
		case Structure(FlatType.Fun1(Variable arg, Variable ret)) ->
			arg.occursHelp(seen, ret.occursHelp(new ArrayList<>(seen), foundCycle));
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

class Descriptor {
	public Content content;
	public int rank;
	public int mark;

	public Descriptor(Content content, int rank, int mark) {
		this.content = content;
		this.rank = rank;
		this.mark = mark;
	}
}