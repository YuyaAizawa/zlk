package zlk.recon;

import zlk.common.id.Id;
import zlk.util.pp.PrettyPrinter;

public record TsBase(
		Id name
) implements TypeSchema {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(name);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		pp(sb);
		return sb.toString();
	}
}