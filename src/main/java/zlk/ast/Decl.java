package zlk.ast;

import java.util.List;
import java.util.Optional;

import zlk.ast.Decl.TypeDecl;
import zlk.ast.Decl.ValDecl;
import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface Decl extends PrettyPrintable, LocationHolder
permits ValDecl, TypeDecl {
	record ValDecl(String name, Optional<AType> anno, List<Exp.Var> args, Exp body, Location loc) implements Decl {}
	record TypeDecl(String name, List<Constructor> ctors, Location loc) implements Decl {}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case ValDecl(String name, Optional<AType> anno, List<Exp.Var> args, Exp body, _) -> {
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
		case TypeDecl(String name, List<Constructor> ctors, _) -> {
			pp.append("type ").append(name).append(" =");
			if(ctors.size() == 1) {
				pp.indent(() -> {
					pp.endl().append(ctors.get(0));
				});
			} else {
				ctors.forEach(ctor -> pp.endl().append("| ").append(ctor));
			}
		}
		}
	}
}
