package zlk.clcalc;

import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.common.id.Id;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record CcTypeDecl(
		Id id,
		Seq<CcCtor> ctors,
		Location loc
	) implements PrettyPrintable, LocationHolder {

		@Override
		public void mkString(PrettyPrinter pp) {
			pp.append("type ").append(id).endl();
			pp.indent(() -> {
				pp.append("= ").append(ctors.head());
				ctors.tail().forEach(ctor -> pp.endl().append("| ").append(ctor));
			});
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			pp(sb);
			return sb.toString();
		}
	}