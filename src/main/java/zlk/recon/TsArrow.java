package zlk.recon;

import zlk.util.pp.PrettyPrinter;

public record TsArrow(
		TypeSchema arg,
		TypeSchema ret
) implements TypeSchema {

	@Override
	public void mkString(PrettyPrinter pp) {
		arg.fold(
				var   -> pp.append(var),
				base  -> pp.append(base),
				arrow -> pp.append("(").append(arrow).append(")")
		).append(" -> ").append(ret);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		pp(sb);
		return sb.toString();
	}
}
