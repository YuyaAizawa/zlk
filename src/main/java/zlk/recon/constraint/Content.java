package zlk.recon.constraint;

import java.util.List;
import java.util.Optional;

import zlk.common.id.Id;
import zlk.recon.Variable;
import zlk.recon.constraint.Content.FlexVar;
import zlk.recon.constraint.Content.Structure;
import zlk.recon.constraint.FlatType.App1;
import zlk.recon.constraint.FlatType.Fun1;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 推論中の型
 *
 * @author YuyaAizawa
 */
public sealed interface Content extends PrettyPrintable
permits FlexVar, Structure {
	record FlexVar(int id, Optional<String> maybeName) implements Content {
		public FlexVar(int id, String name) {
			this(id, Optional.of(name));
		}
		public FlexVar(int id) {
			this(id, Optional.empty());
		}
	}
	record Structure(FlatType flatType) implements Content {}

	public static Content app(Id id, List<Variable> args) {
		return new Structure(new App1(id, args));
	}

	public static Content fun(Variable arg, Variable ret) {
		return new Structure(new Fun1(arg, ret));
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case FlexVar(int id, Optional<String> name) ->
			pp.append("[").append(name.orElseGet(() -> String.valueOf(id))).append("]");
		case Structure(FlatType flatType) ->
			pp.append(flatType);
		}
	}
}
