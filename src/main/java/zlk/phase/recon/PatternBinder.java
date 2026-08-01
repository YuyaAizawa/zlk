package zlk.phase.recon;

import java.util.Optional;

import zlk.common.RecordField;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.ir.idcalc.IcPattern;
import zlk.ir.idcalc.IcExp.IcVarCtor;
import zlk.ir.idcalc.IcPattern.Arg;
import zlk.ir.typing.PatternTyping;
import zlk.phase.recon.constraint.Constraint;
import zlk.phase.recon.constraint.RcType;
import zlk.phase.recon.constraint.Constraint.CEqual;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

final class PatternBinder {
	final SeqBuffer<Variable> vars = new SeqBuffer<>();
	final IdMap<RcType> headers = new IdMap<>();
	final SeqBuffer<Constraint> cons = new SeqBuffer<>();

	PatternTyping<RcType> bind(IcPattern pat, RcType expected, FreshFlex freshFlex) {
		Seq<PatternTyping<RcType>> children = switch(pat) {
		case IcPattern.Wildcard(_) -> Seq.of();
		// TODO: リテラル
		case IcPattern.Var(Id id, _) -> {
			headers.put(id, expected);
			yield Seq.of();
		}

		case IcPattern.Dector(IcVarCtor ctor, Seq<Arg> args, _) -> {
			RcType.Inst ctorInfo = RcType.instantiate(ctor.type(), freshFlex);
			vars.addAll(ctorInfo.flexes());

			if (args.size() != ctorInfo.argTys().size()) {
				throw new RuntimeException("arity missmatch");  // TODO: コンパイルエラーに
			}
			cons.add(new CEqual(ctorInfo.resultTy(), expected));

			yield Seq.zip(args, ctorInfo.argTys()).map(
					(arg, argTy) -> bind(arg.pattern(), argTy, freshFlex));
		}
		case IcPattern.Record(Seq<IcPattern.RecordField> fields, _) -> {
			// fresh TYPE fieldsとfresh ROW tail rを生成．
			// expected = RecordN(RowN([fields..., tail r])) のCEqualを生成．
			// 空patternもRecordN(RowN([], r))で任意recordを要求．
			// tailをvarsへ含める．各subpattern bindはfield型へ．
			Variable tailVar = freshFlex.getVariable(Variable.Kind.ROW);
			vars.add(tailVar);
			SeqBuffer<RecordField<RcType>> fieldTypes = new SeqBuffer<>(fields.size());
			SeqBuffer<PatternTyping<RcType>> fieldPatterns = new SeqBuffer<>(fields.size());
			for(IcPattern.RecordField field : fields) {
				Variable fieldVar = freshFlex.getVariable();
				RcType fieldType = new RcType.VarN(fieldVar);
				vars.add(fieldVar);
				fieldPatterns.add(bind(field.pattern(), fieldType, freshFlex));
				fieldTypes.add(new RecordField<>(field.name(), fieldType));
			}
			RcType requiredRecord = new RcType.RecordN(
					new RcType.RowN(fieldTypes.toSeq(), Optional.of(tailVar)));
			cons.add(new CEqual(expected, requiredRecord));
			yield fieldPatterns.toSeq();
		}
		};
		return new PatternTyping<>(pat, expected, children);
	}
}
