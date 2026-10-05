package zlk.compiler.ir.idcalc;

import java.util.Optional;

import zlk.compiler.id.Id;
import zlk.compiler.ir.typing.Type;
import zlk.compiler.source.Location;
import zlk.compiler.source.LocationHolder;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record IcValDecl(
		Id id,
		Optional<Type> anno,
		Seq<IcPattern> args,
		IcExp body,
		Location loc)
implements PrettyPrintable, LocationHolder {

	public String name() {
		return id.canonicalName();
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(id);
		anno.ifPresent(ty -> {
			pp.append(" : ").append(ty).endl();
			pp.append(id);
		});
		args.forEach(arg -> {
			pp.append(" ").append(arg);
		});
		pp.append(" =").endl();
		pp.inc().append(body).dec();
	}
}
