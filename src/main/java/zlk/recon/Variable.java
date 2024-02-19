package zlk.recon;

import static zlk.util.ErrorUtils.todo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import zlk.common.type.TyAtom;
import zlk.common.type.TyVar;
import zlk.common.type.Type;
import zlk.recon.content.Content;
import zlk.recon.content.FlexVar;
import zlk.recon.flattype.FlatType;
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
		return new Variable(new Descriptor(new FlexVar(idCounter.getAndIncrement()), NO_RANK, NO_MARK));
	}

	public static Content mkFlexVar(String name) {
		return new FlexVar(idCounter.getAndIncrement(), name);
	}

	public Variable(Descriptor root) {
		super(root);
	}

	public Type toType() {
		return get().content.fold(
				var ->
					// TODO 別の場所へ
					new TyVar(var.maybeName() == null
							? "?"+var.id()+"?"
							: var.maybeName())
				,
				struct -> struct.flatType().toType());
	}

	public zlk.common.type.Type toAnnotation() {
		Map<String, Variable> userNames = new HashMap<>();
		accumlateVarNames(userNames);
		return toAnnotation(userNames);
	}

	private zlk.common.type.Type toAnnotation(Map<String, Variable> userNames) {
		Descriptor dtor = get();
		return dtor.content.fold(
				flex -> {
					String name = flex.maybeName();
					if(name == null) {
						System.out.println(this);
						name = todo();
						set(new Descriptor(new FlexVar(flex.id(), name), dtor.rank, dtor.mark));
					}
					return new TyVar(name);
				},
				sture -> termToAnnotation(sture.flatType(), userNames));
	}
	private static zlk.common.type.Type termToAnnotation(FlatType flatType, Map<String, Variable> userNames) {
		return flatType.fold(
				app -> new TyAtom(app.id()),
				fun -> Type.arrow(List.of(fun.arg().toAnnotation(), fun.ret().toAnnotation())));
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
		dtor.content.match(
				flex -> {
					String maybeName = flex.maybeName();
					if(maybeName == null) {
						return;
					} else {
						if(takenNames.containsKey(maybeName)) {
							todo();
						} else {
							takenNames.put(maybeName, this);
						}
					}
				},
				sture -> sture.flatType().match(
						app ->
							app.args().forEach(arg -> arg.accumlateVarNames(takenNames)),
						fun -> {
							fun.arg().accumlateVarNames(takenNames);
							fun.ret().accumlateVarNames(takenNames);
						}
				)
		);
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
		return get().content.fold(
				flex -> foundCycle,
				cture -> cture.flatType().fold(
						app -> {
							return app.args().stream()
									.anyMatch(arg -> arg.occursHelp(seen, foundCycle));
						},
						fun -> {
							return fun.arg().occursHelp(seen,
									fun.ret().occursHelp(new ArrayList<>(seen), foundCycle));
						}));
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