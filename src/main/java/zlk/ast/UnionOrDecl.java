package zlk.ast;

import static zlk.util.ErrorUtils.neverHappen;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public final class UnionOrDecl implements PrettyPrintable, LocationHolder {
	public final Union asUnion;
	public final Decl asDecl;

	private UnionOrDecl(Union asUnion, Decl asDecl) {
		this.asUnion = asUnion;
		this.asDecl = asDecl;
	}
	public static UnionOrDecl union(Union union) {
		return new UnionOrDecl(Objects.requireNonNull(union), null);
	}
	public static UnionOrDecl decl(Decl decl) {
		return new UnionOrDecl(null, Objects.requireNonNull(decl));
	}

	public <R> R fold(
			Function<? super Union, ? extends R> forUnion,
			Function<? super Decl, ? extends R> forDecl) {
		if(asUnion != null) { return forUnion.apply(asUnion); }
		else if(asDecl != null) { return forDecl.apply(asDecl); }
		return neverHappen("any variable is not null");
	}
	public void match(
			Consumer<? super Union> forUnion,
			Consumer<? super Decl> forDecl) {
		if(asUnion != null) { forUnion.accept(asUnion); return; }
		else if(asDecl != null) { forDecl.accept(asDecl); return; }
		neverHappen("any variable is not null");
	}
	@Override
	public Location loc() {
		return fold(a -> a.loc(), a -> a.loc());
	}
	@Override
	public void mkString(PrettyPrinter pp) {
		match(a -> a.mkString(pp), a -> a.mkString(pp));
	}
}
