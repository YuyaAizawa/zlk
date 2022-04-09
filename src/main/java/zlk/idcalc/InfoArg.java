package zlk.idcalc;

import zlk.common.Type;

public final class InfoArg extends Info {
	private final IdInfo fun;
	private final int index;

	public InfoArg(IdInfo fun, int index, String name, Type type) {
		super(name, type);
		this.fun = fun;
		this.index = index;
	}

	public IdInfo fun() {
		return fun;
	}

	public int index() {
		return index;
	}
}
