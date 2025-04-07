package zlk.clcalc;

import java.util.List;

import zlk.common.id.Id;
import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record CcTypeDecl(
		Id id,
		List<CcCtor> ctors,
		Location loc
	) implements PrettyPrintable, LocationHolder {

		@Override
		public void mkString(PrettyPrinter pp) {
			pp.append("type ").append(id).endl();
			pp.indent(() -> {
				pp.append("= ").append(ctors.get(0));
				ctors.subList(1, ctors.size())
						.forEach(ctor -> pp.endl().append("| ").append(ctor));
			});
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			pp(sb);
			return sb.toString();
		}
	}