package zlk.ir.reuse.anf;

import zlk.common.TypeDecl;
import zlk.util.collection.Seq;

public record AnfModule(
		String name,
		Seq<TypeDecl> types,
		Seq<AnfFunDecl> funcs) {}
