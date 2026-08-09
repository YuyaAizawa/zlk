package zlk.ir.anf;

import zlk.util.collection.Seq;

public record AnfBlock(
		Seq<AnfBind> binds,
		AnfAtom result
) {}
