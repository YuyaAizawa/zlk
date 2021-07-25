package zlk.idcalc;

import java.util.List;

import zlk.common.Type;

public record IcDecl(
		IcVar fun,
		List<IcVar> args,
		Type type,
		IcExp body) {

	public void mkString(StringBuilder sb) {
		fun.mkString(sb);
		args.forEach(arg -> {
			sb.append(" ");
			arg.mkString(sb);
		});
		sb.append(" : ");
		type.mkString(sb);
		sb.append(" = ");
		body.mkString(sb);
	}
}
