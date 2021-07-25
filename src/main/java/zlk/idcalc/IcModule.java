package zlk.idcalc;

import java.util.List;

public record IcModule(
		String name,
		List<IcDecl> decls,
		String origin)
{
	public String mkString() {
		StringBuilder sb = new StringBuilder();
		mkString(sb);
		return sb.toString();
	}

	public void mkString(StringBuilder sb) {
		sb.append("module ").append(name()).append("\n");
		decls.forEach(decl -> {
			decl.mkString(sb);
			sb.append("\n");
		});
	}
}
