package zlk.common;

import zlk.common.ConstValue.Bool;
import zlk.common.ConstValue.I32;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface ConstValue extends PrettyPrintable
permits Bool, I32 {
	record Bool(boolean value) implements ConstValue {}
	record I32(int value) implements ConstValue {}

	public static final ConstValue TRUE = new Bool(true);
	public static final ConstValue FALSE = new Bool(false);

	default Type type() {
		return switch(this) {
		case Bool _ -> Type.BOOL;
		case I32 _ -> Type.I32;
		};
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case Bool(boolean value) -> pp.append(value ? "Ture" : "False");
		case I32(int value) -> pp.append(value);
		}
	}
}
