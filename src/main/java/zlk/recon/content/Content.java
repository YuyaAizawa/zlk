package zlk.recon.content;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import zlk.common.id.Id;
import zlk.recon.Variable;
import zlk.recon.flattype.App1;
import zlk.recon.flattype.Fun1;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 推論中の型
 *
 * @author YuyaAizawa
 */
public sealed interface Content extends PrettyPrintable
permits FlexVar, Structure {

	public static Content app(Id id, List<Variable> args) {
		return new Structure(new App1(id, args));
	}

	public static Content fun(Variable arg, Variable ret) {
		return new Structure(new Fun1(arg, ret));
	}

	default <R> R fold(
			Function<? super FlexVar, ? extends R> forFlex,
			Function<? super Structure, ? extends R> forCture) {
		if(this instanceof FlexVar flex) {
			return forFlex.apply(flex);
		} else if(this instanceof Structure cture) {
			return forCture.apply(cture);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	default void match(
			Consumer<? super FlexVar> forFlex,
			Consumer<? super Structure> forCture) {
		if(this instanceof FlexVar flex) {
			forFlex.accept(flex);
		} else if(this instanceof Structure cture) {
			forCture.accept(cture);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		match(
				flex -> {
					pp.append("[");
					if(flex.maybeName() == null) {
						pp.append(flex.id());
					} else {
						pp.append(flex.maybeName());
					}
					pp.append("]");
				}
				,sture -> {
					sture.flatType().mkString(pp);
				});
	}
}
