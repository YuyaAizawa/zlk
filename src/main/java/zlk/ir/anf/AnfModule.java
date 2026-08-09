package zlk.ir.anf;

import zlk.util.collection.Seq;

public record AnfModule(
		String name,
		Seq<AnfTypeDecl> types,
		Seq<AnfFunDecl> funcs) {}
