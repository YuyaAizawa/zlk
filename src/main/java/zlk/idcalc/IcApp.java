package zlk.idcalc;

import java.util.List;

public record IcApp(
		IcExp fun,
		List<IcExp> args) implements IcExp {

	@Override
	public void mkString(StringBuilder sb) {
		fun.mkString(sb);
		sb.append("(");
		args.forEach(arg -> {
			arg.mkString(sb);
			sb.append(", ");
		});
		sb.setLength(sb.length()-2);
		sb.append(")");
	}

}
