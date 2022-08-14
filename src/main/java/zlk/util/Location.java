package zlk.util;

public record Location(int line, int column) implements PrettyPrintable {
	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(line).append(":").append(column);
	}
}
