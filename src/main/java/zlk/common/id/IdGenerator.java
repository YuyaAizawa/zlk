package zlk.common.id;

import zlk.common.type.Type;

public class IdGenerator {
	private int fresh;

	public IdGenerator() {
		fresh = 0;
	}

	public Id generate(String name, Type ty) {
		return new Id(fresh++, name, ty);
	}
}
