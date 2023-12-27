package zlk.ast;

import java.util.List;

import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record PCtor(
		String name,
		List<Pattern> args,
		Location loc
) implements Pattern {
	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(name);
		for(Pattern arg : args) {
			pp.append(" ").append(arg);
		}
	}
}
