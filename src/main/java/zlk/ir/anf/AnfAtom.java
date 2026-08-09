package zlk.ir.anf;

import zlk.common.ConstValue;
import zlk.common.Type;
import zlk.ir.anf.AnfAtom.Cnst;

public sealed interface AnfAtom permits AnfVar, Cnst {

	Type type();

	record Cnst(ConstValue value) implements AnfAtom {
		@Override
		public Type type() { return value.type(); }
	}
}

