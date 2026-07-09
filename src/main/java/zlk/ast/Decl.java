package zlk.ast;

import java.util.Optional;

import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface Decl extends PrettyPrintable, LocationHolder
permits Decl.Value, Decl.Type {
	public sealed interface Value extends Decl permits ValDecl, ValErr {}
	public sealed interface Type extends Decl permits TypeDecl, TypeErr {}

	record ValDecl(String name, Optional<AnType> anno, Seq<Pattern> args, Exp body, Location loc) implements Value {}
	record ValErr(Location loc) implements Value {}
	record TypeDecl(String name, Seq<AnType.Var> vars, Seq<Constructor> ctors, Location loc) implements Type {}
	record TypeErr(Location loc) implements Type {}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case ValDecl(String name, Optional<AnType> anno, Seq<Pattern> args, Exp body, _) -> {
			anno.ifPresent(ty -> pp.append(name).append(" : ").append(ty).endl());
			pp.append(name);
			args.forEach(arg -> {
				pp.append(" ").append(arg);
			});
			pp.append(" =").endl();
			pp.indent(() -> {
				pp.append(body);
			});
		}
		case ValErr(Location loc) -> {
			pp.append("<parse-error ").append(loc).append(">");
		}
		case TypeDecl(String name, Seq<AnType.Var> tyArgs, Seq<Constructor> ctors, _) -> {
			pp.append("type ").append(name).append(" ");
			tyArgs.forEach(arg -> pp.append(arg).append(" "));
			pp.append("=");
			if(ctors.size() == 1) {
				pp.append(" ").append(ctors.head());
			} else {
				pp.indent(() -> {
					ctors.forEach(ctor -> pp.endl().append("| ").append(ctor));
				});
			}
		}
		case TypeErr(Location loc) -> {
			pp.append("<parse-error ").append(loc).append(">");
		}
		}
	}
}
