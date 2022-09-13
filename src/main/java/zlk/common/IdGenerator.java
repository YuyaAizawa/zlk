package zlk.common;

public class IdGenerator {
	private int fresh;

	public IdGenerator() {
		fresh = 0;
	}

	public Id generate(String name, Type ty) {
		return new Id(fresh++, name, ty);
	}
}
