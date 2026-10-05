package zlk.compiler.ir.reuse.anf;

import zlk.compiler.ir.typing.TypeDecl;
import zlk.util.collection.Seq;

public record AnfModule(
		String name,
		Seq<TypeDecl> types,
		Seq<AnfFunDecl> funcs) {}
