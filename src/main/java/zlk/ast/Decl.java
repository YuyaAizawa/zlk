package zlk.ast;

import java.util.List;

import zlk.common.Type;
import zlk.util.MkString;

public record Decl(
		String name,
		List<String> args,
		Type type,
		Exp body)
implements MkString {

	@Override
	public void mkString(StringBuilder sb) {
		sb.append(name);
		args.forEach(arg -> {
			sb.append(" ").append(arg);
		});
		sb.append(" : ");
		type.mkString(sb);
		sb.append(" = ");
		body.mkString(sb);
		sb.append(";");
	}
}
