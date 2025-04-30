package zlk.recon.constraint;

import static zlk.util.ErrorUtils.todo;

import java.util.List;

import zlk.common.Type.Arrow;
import zlk.common.Type.Atom;
import zlk.common.Type.Var;
import zlk.common.id.Id;
import zlk.recon.Variable;
import zlk.recon.constraint.RcType.AppN;
import zlk.recon.constraint.RcType.FunN;
import zlk.recon.constraint.RcType.VarN;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;


public sealed interface RcType extends PrettyPrintable
permits VarN, AppN, FunN {
	record VarN(Variable var) implements RcType {}
	record AppN(Id id, List<RcType> args) implements RcType {}
	record FunN(RcType arg, RcType ret) implements RcType {}

	public static final RcType BOOL = new AppN(zlk.common.Type.BOOL.ctor(), List.of());
	public static final RcType I32  = new AppN(zlk.common.Type.I32.ctor() , List.of());

	public static RcType unbounded() {
		return new VarN(Variable.unbounded());
	}

	public static RcType from(zlk.common.Type ty) {
		if(ty == zlk.common.Type.BOOL)
			return BOOL;
		if(ty == zlk.common.Type.I32)
			return I32;

		return switch(ty) {
		case Atom(Id id, _) ->
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
		case FunN(RcType arg, RcType ret) -> {
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

