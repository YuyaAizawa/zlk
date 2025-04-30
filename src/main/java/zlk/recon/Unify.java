package zlk.recon;

import java.util.List;

import zlk.recon.FlatType.CtorApp1;
import zlk.recon.FlatType.Fun1;
import zlk.recon.constraint.Content;
import zlk.recon.constraint.Content.FlexVar;
import zlk.recon.constraint.Content.Structure;

public final class Unify {

	private static void merge(
			Variable var1, TypeVarState state1,
			Variable var2, TypeVarState state2,
			Content content
	) {
		TypeVarState state = new TypeVarState(content, Math.min(state1.rank, state2.rank));
		var1.unite(var2, state);
	}

	// TODO 単一化中に生成した型変数(恐らくレコード多相などの推論で出てくる)を返すように
	public static void unify(Variable u, Variable v) {
		if(u.isSame(v)) {
			return;
		}

		TypeVarState uState = u.get();
		TypeVarState vState = v.get();
		switch(uState.content) {
		case FlexVar _ -> {
			switch(vState.content) {
			case FlexVar v_ -> merge(u, uState, v, vState, v_.name().isEmpty() ? uState.content : vState.content);
			case Structure _ -> merge(u, uState, v, vState, vState.content);
			case Content.Error e -> merge(u, uState, v, uState, e);
			}
		}
		case Structure u_ -> {
			switch(vState.content) {
			case FlexVar _ -> merge(u, uState, v, vState, uState.content);
			case Structure v_ -> {
				if(u_.flatType() instanceof CtorApp1 u__ && v_.flatType() instanceof CtorApp1 v__) {
					if(u__.id().equals(v__.id())) {
						List<Variable> args = u__.args();
						List<Variable> otherArgs = v__.args();
						if(args.size() != otherArgs.size()) {
							throw new Missmatch();
						}
						for(int i = 0;i < args.size();i++) {
							unify(args.get(i), otherArgs.get(i));
						}
					} else {
						throw new Missmatch();
					}
				} else if(u_.flatType() instanceof Fun1 u__ && v_.flatType() instanceof Fun1 v__) {
					unify(u__.arg(), v__.arg());
					unify(u__.ret(), v__.ret());
					merge(u, uState, v, vState, vState.content);
				} else {
					throw new Missmatch();
				}
			}
			case Content.Error e -> merge(v, uState, v, vState, e);
			}
		}
		case Content.Error e -> merge(v, uState, v, vState, e);
		}
	}
}

@SuppressWarnings("serial")
class Missmatch extends RuntimeException {}

