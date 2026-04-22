package zlk.recon;

import java.util.IdentityHashMap;
import java.util.List;

import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.idcalc.ExpOrPattern;
import zlk.idcalc.IcExp.IcVarCtor;
import zlk.idcalc.IcPattern;
import zlk.idcalc.IcPattern.Arg;
import zlk.recon.constraint.Constraint;
import zlk.recon.constraint.Constraint.CEqual;
import zlk.recon.constraint.RcType;
import zlk.util.collection.SeqBuffer;

final class PatternBinder {
	final SeqBuffer<Variable> vars = new SeqBuffer<>();
	final IdMap<RcType> headers = new IdMap<>();
	final SeqBuffer<Constraint> cons = new SeqBuffer<>();
	final IdentityHashMap<ExpOrPattern, RcType> nodeTypes;

	PatternBinder(IdentityHashMap<ExpOrPattern, RcType> nodeTypes) {
		this.nodeTypes = nodeTypes;
	}

	void bind(IcPattern pat, RcType expected, FreshFlex freshFlex) {
		nodeTypes.put(pat, expected);
		switch(pat) {
		// TODO: リテラル
		case IcPattern.Var(Id id, _) -> {
			headers.put(id, expected);
		}

		case IcPattern.Dector(IcVarCtor ctor, List<Arg> args, _) -> {
			RcType.FromType ctorInfo = RcType.from(ctor.type(), freshFlex);
			ctorInfo.flexes().forEach(vars::add);

			if (args.size() != ctorInfo.argTys().size()) {
				throw new RuntimeException("arity missmatch");  // TODO: コンパイルエラーに
			}
			cons.add(new CEqual(ctorInfo.resultTy(), expected));

			for (int i = 0; i < args.size(); i++) {
				bind(args.get(i).pattern(), ctorInfo.argTys().get(i), freshFlex);  // TODO: Arg型にtype (for cache)とかあるけどそれを使うべきか？
			}
		}
		}
	}
}
