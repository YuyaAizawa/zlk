package zlk.idcalc;

import java.util.Optional;
import java.util.function.Consumer;

import zlk.common.ConstValue;
import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.idcalc.IcExp.IcApp;
import zlk.idcalc.IcExp.IcCase;
import zlk.idcalc.IcExp.IcCnst;
import zlk.idcalc.IcExp.IcIf;
import zlk.idcalc.IcExp.IcLamb;
import zlk.idcalc.IcExp.IcLet;
import zlk.idcalc.IcExp.IcRecord;
import zlk.idcalc.IcExp.IcRecordAccess;
import zlk.idcalc.IcExp.IcRecordUpdate;
import zlk.idcalc.IcExp.IcVarCtor;
import zlk.idcalc.IcExp.IcVarForeign;
import zlk.idcalc.IcExp.IcVarLocal;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface IcExp extends PrettyPrintable, LocationHolder, ExpOrPattern
permits IcCnst, IcVarLocal, IcVarForeign, IcVarCtor, IcLamb, IcApp, IcIf, IcLet, IcCase,
		IcRecord, IcRecordAccess, IcRecordUpdate {

	record IcCnst(
			ConstValue value,
			Location loc) implements IcExp {
		Type type() {
			return value.type();
		}
	}

	record IcVarLocal(
			Id id,
			Location loc) implements IcExp {}

	record IcVarForeign(
			Id id,
			Type type,
			Location loc) implements IcExp {}

	record IcVarCtor(
			Id id,
			Type type,
			Location loc) implements IcExp {}

	record IcLamb(
			Id id,
			Seq<IcPattern> args,
			IcExp body,
			Location loc) implements IcExp {}

	record IcApp(
			IcExp fun,
			Seq<IcExp> args,
			Location loc) implements IcExp {}

	record IcIf(
			IcExp cond,
			IcExp exp1,
			IcExp exp2,
			Location loc) implements IcExp {}

	record IcLet(
			Seq<IcValDecl> defs,
			IcExp body,
			Location loc) implements IcExp {}

	record IcCase(
			IcExp target,
			Seq<IcCaseBranch> branches,
			Location loc) implements IcExp {}

	record IcRecordField(String name, IcExp value, Location loc) implements LocationHolder {}

	record IcRecord(
			Seq<IcRecordField> fields,
			Location loc) implements IcExp {}

	record IcRecordAccess(IcExp target, String field, Location loc) implements IcExp {}
	record IcRecordUpdate(
			IcExp target,
			Seq<IcRecordField> fields,
			Location loc) implements IcExp {}

	public default Optional<Id> getId() {
		switch (this) {
		case IcVarLocal(Id id, Location _) -> {
			return Optional.of(id);
		}
		case IcVarForeign(Id id, Type _, Location _) -> {
			return Optional.of(id);
		}
		case IcVarCtor(Id id, Type _, Location _) -> {
			return Optional.of(id);
		}
		default -> {
			return Optional.empty();
		}
		}
	}

	/**
	 * 式を行きがけ順に走査する
	 * @param action 各 IcExp に対して実行する処理
	 */
	default void walk(Consumer<? super IcExp> action) {
		action.accept(this);
		switch (this) {
		case IcCnst _, IcVarLocal _, IcVarForeign _, IcVarCtor _ -> {}
		case IcLamb(Id _, Seq<IcPattern> _, IcExp body, Location _) ->
			body.walk(action);
		case IcApp(IcExp fun, Seq<IcExp> args, Location _) -> {
			fun.walk(action);
			args.forEach(arg -> arg.walk(action));
		}
		case IcIf(IcExp cond, IcExp thenExp, IcExp elseExp, Location _) -> {
			cond.walk(action);
			thenExp.walk(action);
			elseExp.walk(action);
		}
		case IcLet(Seq<IcValDecl> decls, IcExp body, Location _) -> {
			decls.forEach(decl -> decl.body().walk(action));
			body.walk(action);
		}
		case IcCase(IcExp target, Seq<IcCaseBranch> branches, Location _) -> {
			target.walk(action);
			branches.forEach(branch -> branch.body().walk(action));
		}
		case IcRecord(Seq<IcRecordField> fields, Location _) ->
			fields.forEach(field -> field.value().walk(action));
		case IcRecordAccess(IcExp target, String _, Location _) -> target.walk(action);
		case IcRecordUpdate(IcExp target, Seq<IcRecordField> fields, Location _) -> {
			target.walk(action);
			fields.forEach(field -> field.value().walk(action));
		}
		}
	}

	/**
	 * Appends the string representation of this expression to specified printer.
	 * It does not terminate the line.
	 * @param pp printer to append string.
	 */
	@Override
	default void mkString(PrettyPrinter pp) {
		switch (this) {
		case IcCnst(ConstValue value, Location _) -> {
			pp.append(value);
		}
		case IcVarLocal(Id id, Location _) -> {
			pp.append(id);
		}
		case IcVarForeign(Id id, Type _, Location _) -> {
			pp.append(id);
		}
		case IcVarCtor(Id id, Type _, Location _) -> {
			pp.append(id);
		}
		case IcLamb(Id _, Seq<IcPattern> args, IcExp body, Location _) -> {
			pp.append("\\");
			args.forEach(arg -> {
				pp.append(arg).append(" ");
			});
			pp.append("-> ");
			pp.append(body);
		}
		case IcApp(IcExp fun, Seq<IcExp> args, Location _) -> {
			switch(fun) {
			case IcCnst _, IcVarLocal _, IcVarForeign _, IcVarCtor _, IcApp _, IcRecord _,
					IcRecordAccess _, IcRecordUpdate _ -> {
				pp.append(fun);
			}
			case IcLamb _ -> {
				pp.append("(").append(fun).append(")");
			}
			case IcIf _, IcLet _, IcCase _ -> {
				pp.append("(").endl();
				pp.indent(() -> pp.append(fun).endl());
				pp.append(")");
			}
			}
			args.forEach(arg -> {
				pp.append(" ");
				switch(arg) {
				case IcCnst _, IcVarLocal _, IcVarForeign _, IcVarCtor _, IcRecord _,
						IcRecordAccess _, IcRecordUpdate _ -> {
					pp.append(arg);
				}
				case IcLamb _, IcApp _ -> {
					pp.append("(").append(arg).append(")");
				}
				case IcIf _, IcLet _, IcCase _ -> {
					pp.append("(").endl();
					pp.indent(() -> pp.append(arg).endl());
					pp.append(")");
				}
				}
			});
		}
		case IcIf(IcExp cond, IcExp thenExp, IcExp elseExp, Location _) -> {
			pp.append("if ").append(cond).append(" then").endl();
			pp.indent(() -> {
				pp.append(thenExp).endl();
			});
			pp.append("else");
			if(elseExp instanceof IcIf) {
				pp.append(" ").append(elseExp);
			} else {
				pp.indent(() -> {
					pp.endl().append(elseExp);
				});
			}
		}
		case IcLet(Seq<IcValDecl> decls, IcExp body, Location _) -> {
			pp.append("let").endl();
			pp.indent(() -> {
				decls.forEach(decl -> {
					pp.append(decl).endl();
				});
			});
			pp.append("in");
			pp.indent(() -> {
				pp.endl().append(body);
			});
		}
		case IcCase(IcExp target, Seq<IcCaseBranch> branches, Location _) -> {
			pp.append("case ").append(target).append(" of");
			pp.indent(() -> {
				branches.forEach(branch -> {
					pp.endl().append(branch);
				});
			});
		}
		case IcRecord(Seq<IcRecordField> fields, Location _) -> {
			if(fields.isEmpty()) {
				pp.append("{}");
			} else {
				pp.append("{ ");
				IcRecordField head = fields.head();
				pp.append(head.name).append(" = ").append(head.value);
				fields.tail().forEach(field -> {
					pp.append(", ").append(field.name).append(" = ").append(field.value);
				});
				pp.append(" }");
			}
		}
		case IcRecordAccess(IcExp target, String field, Location _) -> {
			switch(target) {
			case IcCnst _, IcVarLocal _, IcVarForeign _, IcVarCtor _, IcRecord _,
					IcRecordAccess _, IcRecordUpdate _ -> pp.append(target);
			case IcLamb _, IcApp _, IcIf _, IcLet _, IcCase _ ->
				pp.append("(").append(target).append(")");
			}
			pp.append(".").append(field);
		}
		case IcRecordUpdate(IcExp target, Seq<IcRecordField> fields, Location _) -> {
			pp.append("{ ").append(target).append(" | ");
			fields.forEachIndexed((i, field) -> {
				if(i > 0) pp.append(", ");
				pp.append(field.name()).append(" = ").append(field.value());
			});
			pp.append(" }");
		}
		}
	}
}
