package zlk.recon.constraint.pattern;

import java.util.ArrayList;
import java.util.List;

import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.idcalc.IcPCtorArg;
import zlk.idcalc.IcPattern;
import zlk.recon.Variable;
import zlk.recon.constraint.Constraint;
import zlk.util.Stack;

public final class State {
	public IdMap<zlk.recon.constraint.Type> headers;
	public Stack<Variable> vars;
	public List<Constraint> cons; // revCon but not reversed

	public State() {
		headers = new IdMap<>();
		vars = new Stack<>();
		cons = new ArrayList<>();
	}

	public State(List<Variable> flexes) {
		headers = new IdMap<>();
		vars = new Stack<>(flexes);
		cons = new ArrayList<>();
	}

	public State add(IcPattern pattern, zlk.recon.constraint.Type expected) {
		pattern.match(
				var ->
					addToHeaders(var.id(), expected),
				ctor ->
					// TODO 型変数に対応
					addCtor(
							ctor.ctor().type().apply(ctor.ctor().type().flatten().size()-1),  // TODO 結果の型を取る方法を作る
							ctor.ctor().id(),
							ctor.args(),
							expected)
				);
		return this;
	}

	private void addToHeaders(Id id, zlk.recon.constraint.Type expected) {
		headers.put(id, expected);
	}

	private void addCtor(zlk.common.Type ty, Id ctorId, List<IcPCtorArg> args, zlk.recon.constraint.Type expected) {
		args.forEach(arg ->
			add(arg.pattern(), zlk.recon.constraint.Type.from(arg.type())));

		zlk.recon.constraint.Type ctorType = zlk.recon.constraint.Type.from(ty);
		System.out.println(ctorType);
		cons.add(new Constraint.CPattern(ctorType, expected));
	}
}
