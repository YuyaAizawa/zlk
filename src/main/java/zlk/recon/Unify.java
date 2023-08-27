package zlk.recon;

import java.util.List;

import zlk.recon.content.Content;
import zlk.recon.content.FlexVar;
import zlk.recon.flattype.App1;
import zlk.recon.flattype.FlatType;
import zlk.recon.flattype.Fun1;

public final class Unify {

	private static record Context(
			Variable first,
			Descriptor firstDesc,
			Variable second,
			Descriptor secondDesc) {}

	private static void merge(Context context, Content content) {
		Variable var1 = context.first;
		Variable var2 = context.second;
		int rank1 = context.firstDesc.rank;
		int rank2 = context.secondDesc.rank;

		var1.unite(var2, new Descriptor(content, Math.min(rank1, rank2), Variable.NO_MARK));
	}

	public static void unify(Variable u, Variable v) {
		if(u.isSame(v)) {
			return;
		}
		Descriptor u_ = u.get();
		Descriptor v_ = v.get();
		Context context = new Context(u, u_, v, v_);

		context.firstDesc.content.match(
				var -> unifyVar(context, u_.content, v_.content),
				cture -> unifyStructure(context, cture.flatType(), u_.content, v_.content)
				);
	}

	private static void unifyVar(Context context, Content content, Content otherContent) {
		if(otherContent instanceof FlexVar var) {
			merge(context, var.maybeName() == null ? content : otherContent);
		} else {
			merge(context, otherContent);
		}
	}

	private static void unifyStructure(Context context, FlatType flatType, Content content,
			Content otherContent) {
		otherContent.match(
				var -> merge(context, content),
				sture -> {
					FlatType otherFlatType = sture.flatType();

					if(flatType instanceof App1 app && otherFlatType instanceof App1 otherApp) {
						if(app.id().equals(otherApp.id())) {
							List<Variable> args = app.args();
							List<Variable> otherArgs = otherApp.args();
							if(args.size() != otherArgs.size()) {
								throw new Missmatch();
							}
							for(int i = 0;i < args.size();i++) {
								unify(args.get(i), otherArgs.get(i));
							}
						} else {
							throw new Missmatch();
						}
					} else if (flatType instanceof Fun1 fun && otherFlatType instanceof Fun1 otherFun) {
						unify(fun.arg(), otherFun.arg());
						unify(fun.ret(), otherFun.ret());
						merge(context, otherContent);
					} else {
						throw new Missmatch();
					}
				});
	}
}

@SuppressWarnings("serial")
class Missmatch extends RuntimeException {}

