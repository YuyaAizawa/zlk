package zlk.recon;

import java.util.IdentityHashMap;

import zlk.common.RecordField;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.idcalc.ExpOrPattern;
import zlk.idcalc.IcExp.IcVarCtor;
import zlk.idcalc.IcPattern;
import zlk.idcalc.IcPattern.Arg;
import zlk.recon.constraint.Constraint;
import zlk.recon.constraint.Constraint.CEqual;
import zlk.recon.constraint.RcType;
import zlk.util.collection.Seq;
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
		case IcPattern.Wildcard(_) -> {
			// 型は nodeTypes に記録するが名前は導入しない
		}
		// TODO: リテラル
		case IcPattern.Var(Id id, _) -> {
			headers.put(id, expected);
		}

		case IcPattern.Dector(IcVarCtor ctor, Seq<Arg> args, _) -> {
			RcType.Inst ctorInfo = RcType.instantiate(ctor.type(), freshFlex);
			vars.addAll(ctorInfo.flexes());

			if (args.size() != ctorInfo.argTys().size()) {
				throw new RuntimeException("arity missmatch");  // TODO: コンパイルエラーに
			}
			cons.add(new CEqual(ctorInfo.resultTy(), expected));

			for (int i = 0; i < args.size(); i++) {
				bind(args.at(i).pattern(), ctorInfo.argTys().at(i), freshFlex);  // TODO: Arg型にtype (for cache)とかあるけどそれを使うべきか？
			}
		}
		case IcPattern.Record(Seq<IcPattern.RecordField> fields, _) -> {
			SeqBuffer<RecordField<RcType>> fieldTypes = new SeqBuffer<>(fields.size());
			for(IcPattern.RecordField field : fields) {
				Variable fieldVar = freshFlex.getVariable();
				RcType fieldType = new RcType.VarN(fieldVar);
				vars.add(fieldVar);
				bind(field.pattern(), fieldType, freshFlex);
				fieldTypes.add(new RecordField<>(field.name(), fieldType));
			}
			cons.add(new Constraint.CRecordPattern(expected, fieldTypes.toSeq()));
		}
		}
	}
}
