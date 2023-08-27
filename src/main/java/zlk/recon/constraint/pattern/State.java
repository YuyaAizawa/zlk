package zlk.recon.constraint.pattern;

import java.util.ArrayList;
import java.util.List;

import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.idcalc.IcPattern;
import zlk.recon.Variable;
import zlk.recon.constraint.Constraint;
import zlk.recon.constraint.type.Type;
import zlk.util.Stack;

public final class State {
	public IdMap<Type> headers;
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

	public State add(IcPattern pattern, Type expected) {
		pattern.match(
				var ->
					addToHeaders(var.id(), expected));
		return this;
	}

	private void addToHeaders(Id id, Type expected) {
		headers.put(id, expected);
	}
}
