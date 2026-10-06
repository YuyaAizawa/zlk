package zlk.compiler.ir.reuse.own;

import zlk.compiler.ir.typing.TypeDecl;
import zlk.util.collection.Seq;

public record OwnModule(
		String name,
		Seq<TypeDecl> types,
		Seq<OwnFunDecl> funcs) {}
