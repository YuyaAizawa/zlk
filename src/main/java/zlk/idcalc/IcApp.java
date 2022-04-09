package zlk.idcalc;

import java.util.List;

public record IcApp(
		IcExp fun,
		List<IcExp> args) implements IcExp {

	@Override
	public void mkString(StringBuilder sb) {
		fun.mkString(sb);
		args.forEach(arg -> {
			sb.append(" ");
			arg.match(
					cnst -> cnst.mkString(sb),
					var  -> var.mkString(sb),
					app  -> app.mkStringEnclosed(sb),
					if_  -> if_.mkStringEnclosed(sb),
					let  -> let.mkStringEnclosed(sb));
		});
	}

}
