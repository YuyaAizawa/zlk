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

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case Bool(boolean value) -> {if(value) pp.append("True"); else pp.append("False");}
		case I32(int value) -> pp.append(value);
		}
	}
}
