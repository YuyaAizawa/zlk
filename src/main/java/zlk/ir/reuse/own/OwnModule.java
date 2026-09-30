package zlk.ir.reuse.own;

import zlk.common.TypeDecl;
import zlk.util.collection.Seq;

public record OwnModule(
		String name,
		Seq<TypeDecl> types,
		Seq<OwnFunDecl> funcs) {}
