package zlk.recon.constraint;

import java.util.List;

import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.recon.Variable;
import zlk.recon.constraint.Constraint.CAnd;
import zlk.recon.constraint.Constraint.CEqual;
import zlk.recon.constraint.Constraint.CForeign;
import zlk.recon.constraint.Constraint.CLet;
import zlk.recon.constraint.Constraint.CLocal;
import zlk.recon.constraint.Constraint.CPattern;
import zlk.recon.constraint.Constraint.CSaveTheEnvironment;
import zlk.recon.constraint.Constraint.CTrue;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface Constraint extends PrettyPrintable
permits CTrue, CEqual, CLocal, CForeign, CPattern, CAnd, CLet, CSaveTheEnvironment {

	record CTrue() implements Constraint {}

	record CEqual(
			RcType type,
			RcType expected) implements Constraint {}

	record CLocal(
			Id id,
			RcType expected) implements Constraint {}

	record CForeign(
			Id id,
			zlk.common.Type anno,
			RcType expected) implements Constraint {}

	record CPattern(
			RcType type,
			RcType expected) implements Constraint {}

	record CAnd(  // TODO: これ出現個所決まってたらList<Constraint>にならない？
			List<Constraint> constraints) implements Constraint {}

	record CLet(
			List<Variable> ridids,
			List<Variable> flexes,
			IdMap<RcType> headerAnno,
			Constraint headerCon,
			Constraint bodyCon) implements Constraint {}

	record CSaveTheEnvironment() implements Constraint {}  // TODO: 手続きなのでこれ消せるかも

	@Override
	default void mkString(PrettyPrinter pp) {
		switch (this) {
		case CTrue() -> {
			pp.append("True");
		}
		case CEqual(RcType type, RcType expected) -> {
			pp.append("Equal: ").append(type).append(", ").append(expected);
		}
		case CLocal(Id id, RcType expected) -> {
			pp.append("Local: ").append(id).append(", ").append(expected);
		}
		case CForeign(Id id, zlk.common.Type anno, RcType expected) -> {
			pp.append("Foreign: ").append(id).append("::").append(anno).append(", ").append(expected);
		}
		case CPattern(RcType type, RcType expected) -> {
			pp.append("Pattern: ").append(type).append(", ").append(expected);
		}
		case CAnd(List<Constraint> constraints) -> {
			pp.append("And:");
			pp.indent(() -> {
				constraints.forEach(c -> pp.endl().append(c));
			});
		}
		case CLet(List<Variable> ridids, List<Variable> flexes, IdMap<RcType> headerAnno, Constraint headerCon, Constraint bodyCon) -> {
			pp.append("Let:").endl();
			pp.indent(() -> {
				pp.append("ridids:");
				ridids.forEach(v -> pp.append(" ").append(v));
				pp.endl();

				pp.append("flexes:");
				flexes.forEach(v -> pp.append(" ").append(v));
				pp.endl();

				pp.append("headers:");
				if(headerAnno.isEmpty()) {
					pp.append(" []");
				} else {
					pp.endl();
					pp.indent(() -> {
						pp.append(headerAnno);
					});
				}
				pp.endl();

				pp.append("headerCons:").endl();
				pp.indent(() -> {
					pp.append(headerCon);
				});
				pp.endl();

				pp.append("bodyCon:").endl();
				pp.indent(() -> {
					pp.append(bodyCon);
				});
			});
		}
		case CSaveTheEnvironment() -> {
			pp.append("SaveTheEnvironment");
		}
		}
	}
}
