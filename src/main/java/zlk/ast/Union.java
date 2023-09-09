package zlk.ast;

import java.util.List;

import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record Union(
		String name,
		List<Constructor> ctors,
		Location loc
) implements PrettyPrintable, LocationHolder {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("type ").append(name).endl().inc();
		pp.append("= ").append(ctors.get(0));
		ctors.subList(1, ctors.size())
				.forEach(ctor -> pp.endl().append("| ").append(ctor));
		pp.dec();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		pp(sb);
		return sb.toString();
	}
}
