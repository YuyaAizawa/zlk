package zlk.ast;

import java.util.Optional;

import zlk.ast.Decl.TypeDecl;
import zlk.ast.Decl.ValDecl;
import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface Decl extends PrettyPrintable, LocationHolder
permits ValDecl, TypeDecl {
	record ValDecl(String name, Optional<AnType> anno, Seq<Pattern> args, Exp body, Location loc) implements Decl {}
	record TypeDecl(String name, Seq<AnType.Var> vars, Seq<Constructor> ctors, Location loc) implements Decl {}

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
		}
	}
}
