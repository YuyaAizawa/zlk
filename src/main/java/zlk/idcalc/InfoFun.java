package zlk.idcalc;

import zlk.common.Type;

public final class InfoFun extends Info {

	private String module;

	public InfoFun(String module, String name, Type type) {
		super(name, type);
		this.module = module;
	}

	public String module() {
		return module;
	}
}
