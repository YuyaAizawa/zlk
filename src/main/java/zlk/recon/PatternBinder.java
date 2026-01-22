package zlk.recon;

import java.util.ArrayList;
import java.util.List;

import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.idcalc.IcExp.IcVarCtor;
import zlk.idcalc.IcPattern;
import zlk.idcalc.IcPattern.Arg;
import zlk.recon.constraint.Constraint;
import zlk.recon.constraint.Constraint.CEqual;
import zlk.recon.constraint.RcType;

final class PatternBinder {
	final List<Variable> vars = new ArrayList<>();
	final IdMap<RcType> headers = new IdMap<>();
	final List<Constraint> cons = new ArrayList<>();

	void bind(IcPattern pat, RcType expected, FreshFlex freshFlex) {
		switch(pat) {
		// TODO: リテラル
		case IcPattern.Var(Id id, _) -> {
			headers.put(id, expected);
		}
		case IcPattern.Dector(IcVarCtor ctor, List<Arg> args, _) -> {
			RcType.FromType ctorInfo = RcType.from(ctor.type(), freshFlex);
			cons.add(new CEqual(ctorInfo.resultTy(), expected));
			vars.addAll(ctorInfo.flexes());
			if (args.size() != ctorInfo.argTys().size()) {
				throw new RuntimeException("arity missmatch");  // TODO: コンパイルエラーに
			}
			for (int i = 0; i < args.size(); i++) {
				bind(args.get(i).pattern(), ctorInfo.argTys().get(i), freshFlex);  // TODO: Arg型にtype (for cache)とかあるけどそれを使うべきか？
			}
		}
		}
	}
}
