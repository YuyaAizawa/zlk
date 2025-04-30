package zlk.recon.constraint;

import java.util.ArrayList;
import java.util.List;

import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcPattern;
import zlk.recon.Variable;
import zlk.util.Location;

public final class State {
	public IdMap<RcType> headers;
	public List<Variable> vars;
	public List<Constraint> cons;

	public State() {
		headers = new IdMap<>();
		vars = new ArrayList<>();
		cons = new ArrayList<>();
	}

	public State add(IcPattern pattern, RcType expected) {
		switch(pattern) {
		case IcPattern.Var(Id id, Location _) -> {
			addToHeaders(id, expected);
		}
		case IcPattern.Ctor(IcExp.IcVarCtor ctor, List<IcPattern.Arg> args, Location _) -> {
			// TODO 型変数に対応
			addCtor(
					ctor.type().apply(ctor.type().flatten().size()-1),  // TODO 結果の型を取る方法を作る
					ctor.id(),
					args,
					expected);
		}
		}
		return this;
	}

	private void addToHeaders(Id id, RcType expected) {
		headers.put(id, expected);
	}

	private void addCtor(Type ty, Id ctorId, List<IcPattern.Arg> args, RcType expected) {
		args.forEach(arg ->
			add(arg.pattern(), RcType.from(arg.type())));

		RcType ctorType = RcType.from(ty);
		cons.add(new Constraint.CPattern(ctorId, ctorType, expected));
	}
}
