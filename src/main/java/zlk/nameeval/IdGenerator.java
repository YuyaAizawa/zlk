package zlk.nameeval;

public class IdGenerator {
	private int fresh;

	public IdGenerator() {
		fresh = 0;
	}

	public int generate() {
		return fresh++;
	}
}
