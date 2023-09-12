package zlk.idcalc;

import static zlk.util.ErrorUtils.neverHappen;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import zlk.common.id.Id;
import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public final class IcPattern implements PrettyPrintable, LocationHolder {
	private final IcPVar asVar;

	public static IcPattern var(IcPVar var) {
		return new IcPattern(Objects.requireNonNull(var));
	}
	private IcPattern(IcPVar asVar) {
		this.asVar = asVar;
	}

	public <R> R fold(Function<? super IcPVar, ? extends R> forVar) {
		if(asVar != null) { return forVar.apply(asVar); }
		return neverHappen("");
	}
	public <R> void match(Consumer<? super IcPVar> forVar) {
		if(asVar != null) { forVar.accept(asVar); return; }
		neverHappen("");
	}

	public void fv(List<IcVarLocal> acc, Set<Id> known) {
		match(
				var -> known.add(var.id()));
	}
	@Override
	public void mkString(PrettyPrinter pp) {
		match(
			var -> pp.append(var.id()));
	}
	@Override
	public Location loc() {
		return fold(
				var -> var.loc());
	}
}
