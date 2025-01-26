package zlk.recon.constraint;

import static zlk.util.ErrorUtils.todo;

import java.util.List;

import zlk.common.Type.Arrow;
import zlk.common.Type.Atom;
import zlk.common.Type.Var;
import zlk.common.id.Id;
import zlk.recon.Variable;
import zlk.recon.constraint.Type.AppN;
import zlk.recon.constraint.Type.FunN;
import zlk.recon.constraint.Type.VarN;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;


public sealed interface Type extends PrettyPrintable
permits VarN, AppN, FunN {
	record VarN(Variable var) implements Type {}
	record AppN(Id id, List<Type> args) implements Type {}
	record FunN(Type arg, Type ret) implements Type {}

	public static final Type BOOL = datatype(zlk.common.Type.BOOL.id(), List.of());
	public static final Type I32  = datatype(zlk.common.Type.I32.id() , List.of());

	public static Type datatype(Id id, List<Type> args) {
		return new AppN(id, args);
	}

	public static Type from(zlk.common.Type ty) {
		if(ty == zlk.common.Type.BOOL)
			return BOOL;
		if(ty == zlk.common.Type.I32)
			return I32;

		return switch(ty) {
		case Atom(Id id) ->
			new AppN(id, List.of());
		case Arrow(var arg, var ret) ->
			new FunN(from(arg), from(ret));
		case Var _ ->
			todo();
		};
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case VarN(Variable var) -> {
			pp.append(var);
		}
		case AppN(Id id, _) -> {
			pp.append(id);
		}
		case FunN(Type arg, Type ret) -> {
			switch(arg) {
			case FunN _ -> pp.append("(").append(arg).append(")");
			default -> pp.append(arg);
			}
			pp.append(" -> ");
			pp.append(ret);
		}
		}
	}
}
