package zlk.recon;

import java.util.concurrent.atomic.AtomicInteger;

import zlk.util.pp.PrettyPrinter;

public record TsVar(
		int id
) implements TypeSchema {

	private static AtomicInteger counter = new AtomicInteger();

	public TsVar() {
		this(counter.getAndIncrement());
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("[").append(id).append("]");
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		pp(sb);
		return sb.toString();
	}

	public boolean equals(TsVar other) {
		return other.id == id;
	}
}
