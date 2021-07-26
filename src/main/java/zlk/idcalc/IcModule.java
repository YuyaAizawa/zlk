package zlk.idcalc;

import java.util.List;

import zlk.util.MkString;

public record IcModule(
		String name,
		List<IcDecl> decls,
		String origin)
implements MkString{

	@Override
	public void mkString(StringBuilder sb) {
		sb.append("module ").append(name()).append("\n");
		decls.forEach(decl -> {
			decl.mkString(sb);
			sb.append("\n");
		});
	}
}
