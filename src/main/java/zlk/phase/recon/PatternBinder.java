package zlk.phase.recon;

import java.util.Optional;

import zlk.common.RecordField;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.ir.idcalc.IcExp.IcVarCtor;
import zlk.ir.idcalc.IcPattern;
import zlk.phase.recon.constraint.Constraint;
import zlk.phase.recon.constraint.Constraint.CEqual;
import zlk.phase.recon.constraint.RcType;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

final class PatternBinder {
	final SeqBuffer<Variable> vars = new SeqBuffer<>();
	final IdMap<RcType> headers = new IdMap<>();
	final SeqBuffer<Constraint> cons = new SeqBuffer<>();

	void bind(
			IcPattern pat,
			RcType expected,
			FreshFlex freshFlex,
			ExpOrPatternMap<RcType> patternBinds
	) {
		switch(pat) {
		case IcPattern.Wildcard(_) -> Seq.of();
		// TODO: リテラル
		case IcPattern.Var(Id id, _) -> headers.put(id, expected);

		case IcPattern.Dector(IcVarCtor ctor, Seq<IcPattern> args, _) -> {
			RcType.Inst ctorInfo = RcType.instantiate(ctor.type(), freshFlex);
			vars.addAll(ctorInfo.flexes());

			if (args.size() != ctorInfo.argTys().size()) {
				throw new IllegalStateException("constructor arity must be validated during name evaluation");
			}
			cons.add(new CEqual(ctorInfo.resultTy(), expected));

			Seq.zip(args, ctorInfo.argTys()).forEach(
				(arg, argTy) -> bind(arg, argTy, freshFlex, patternBinds));
		}
		case IcPattern.Record(Seq<IcPattern.Var> fields, _) -> {
			Variable tailVar = freshFlex.getVariable(Variable.Kind.ROW);
			vars.add(tailVar);
			SeqBuffer<RecordField<RcType>> fieldTypes = new SeqBuffer<>(fields.size());
			for(IcPattern.Var field : fields) {
				Variable fieldVar = freshFlex.getVariable();
				RcType fieldType = new RcType.VarN(fieldVar);
				vars.add(fieldVar);
				headers.put(field.id(), fieldType);
				patternBinds.put(field, fieldType);
				fieldTypes.add(new RecordField<>(field.id().simpleName(), fieldType));
			}
			RcType requiredRecord = new RcType.RecordN(
					new RcType.RowN(fieldTypes.toSeq(), Optional.of(tailVar)));
			cons.add(new CEqual(expected, requiredRecord));
		}
		};
		patternBinds.put(pat, expected);
	}
}
