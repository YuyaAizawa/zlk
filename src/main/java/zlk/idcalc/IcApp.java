package zlk.idcalc;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public record IcApp(
		IcExp fun,
		List<IcExp> args) implements IcExp {

	@Override
	public <R> R map(
			Function<IcConst, R> forConst,
			Function<IcVar, R> forVar,
			Function<IcApp, R> forApp,
			Function<IcIf, R> forIf) {
		return forApp.apply(this);
	}

	@Override
	public void match(
			Consumer<IcConst> forConst,
			Consumer<IcVar> forVar,
			Consumer<IcApp> forApp,
			Consumer<IcIf> forIf) {
		forApp.accept(this);
	}

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
