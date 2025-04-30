package zlk.util.pp;

import java.util.Map;
import java.util.function.Consumer;

public interface PrettyPrinter extends UncheckedAppendable {

	/**
	 * Prints a CharSequence. At the beginning of the line, the indentation is compensated.
	 *
	 * @param cs The CharSequence to be printed
	 *
	 * @return This PrettyPrinter for fluent interface
	 */
	@Override
	PrettyPrinter append(CharSequence cs);

	/**
	 * Breaks the current line.
	 *
	 * @return This PrettyPrinter for fluent interface
	 */
	PrettyPrinter endl();

	/**
	 * Increments the depth of indent. The indentation is reflected when some character is printed
	 * at the beginning of the line.
	 *
	 * @return This PrettyPrinter for fluent interface
	 */
	PrettyPrinter inc();

	/**
	 * Decrements the depth of indent. The indentation is reflected when some character is printed
	 * at the beginning of the line.
	 *
	 * @return This PrettyPrinter for fluent interface
	 *
	 * @throws IllegalStateException If the indent go below zero
	 */
	PrettyPrinter dec();

	/**
	 * Prints a {@link PrettyPrintable} Object. If it contains several line breaks, indentation is
	 * inserted accordingly.
	 *
	 * @param target The PrettyPrintable to be printed
	 *
	 * @return This PrettyPrinter for fluent interface
	 */
	default PrettyPrinter append(PrettyPrintable target) {
		target.mkString(this);
		return this;
	}

	default PrettyPrinter append(int i) {
		return append(Integer.toString(i));
	}

	/**
	 * Performs the specified print action with indentation.
	 *
	 * A utility method for easily matching the number of inc() and of dec().
	 *
	 * @param printAction Print commands to be executed in an indented state.
	 * @return This PrettyPrinter for fluent interface
	 */
	default PrettyPrinter indent(Runnable printAction) {
		inc();
		printAction.run();
		dec();
		return this;
	}

	default <EX extends Wrapper> EX wrap(EX wrapper) {
		wrapper.parent = this;
		return wrapper;
	}

	/**
	 * Prints a {@link PrettyPrintable} Object in key-value style. The key and value are separated
	 * by a colon, and if the value requires at least one line break, the line is broken after the
	 * colon and the value is printed one level deeper.
	 *
	 * @param key
	 * @param value
	 *
	 * @return This PrettyPrinter for fluent interface
	 */
	default PrettyPrinter field(CharSequence key, PrettyPrintable value) {

		BufferPrettyPrinter buffer = new BufferPrettyPrinter();
		Wrapper wrapper = buffer.wrap(new Wrapper() {

			@Override
			public PrettyPrinter endl() {
				if(parent == buffer) {
					PrettyPrinter.this.endl();
					buffer.flush(PrettyPrinter.this);
					parent = PrettyPrinter.this;
				}
				return super.endl();
			}

			@Override
			public PrettyPrinter field(CharSequence key, PrettyPrintable value) {
				if(parent == buffer) {
					PrettyPrinter.this.endl();
					buffer.flush(PrettyPrinter.this);
					parent = PrettyPrinter.this;
				}
				return super.field(key, value);
			}
		});

		this.append(key).append(":");

		wrapper.inc().append(value).dec();

		if(wrapper.parent == buffer) {
			this.append(" ");
			buffer.flush(this);
			wrapper.parent = this;
			wrapper.endl();
		}

		return this;
	}

	default PrettyPrinter withoutLineBreak(Consumer<PrettyPrinter> printAction) {
		PrettyPrinter wolbPrinter = wrap(Wrapper.oneLine());
		printAction.accept(wolbPrinter);
		return this;
	}

	default <V> PrettyPrinter appendStyled(Map<? extends PrettyPrintable, V> map) {
		switch(map.size()) {
		case 0:
			append("{}");
			break;
		case 1:
			append("{ ").appendStyled(map.entrySet().iterator().next()).append(" }");
			break;
		default:
			append("{").indent(() -> {
				map.entrySet().forEach(entry -> endl().appendStyled(entry).append(","));
			}).endl().append("}");
		}
		return this;
	}

	default <V> PrettyPrinter appendStyled(Map.Entry<? extends PrettyPrintable, V> entry) {
		append(entry.getKey()).append(": ");
		if(entry.getValue() instanceof PrettyPrintable value) {
			append(value);
		} else {
			append("--cannot print--");
		}
		return this;
	}
}

/**
 * Wrapper overrides {@link PrettyPrinter}'s behavior by passing
 * {@link PrettyPrinter#wrap(Wrapper)}.
 */
abstract class Wrapper implements PrettyPrinter {
	PrettyPrinter parent;

	@Override
	public PrettyPrinter append(CharSequence cs) {
		parent.append(cs);
		return this;
	}

	@Override
	public PrettyPrinter endl() {
		parent.endl();
		return this;
	}

	@Override
	public PrettyPrinter inc() {
		parent.inc();
		return this;
	}

	@Override
	public PrettyPrinter dec() {
		parent.dec();
		return this;
	}

	/**
	 * Returns {@link Wrapper} that prints objects in one-line.
	 *
	 * It calls {@link PrettyPrintable#mkStringWithoutLineBreak(PrettyPrinter)} instead of
	 * {@link PrettyPrintable#mkString(PrettyPrinter)}.
	 *
	 * @return One-line wrapper
	 */
	public static Wrapper oneLine() {
		return ONE_LINE;
	}

	/**
	 * Returns {@link Wrapper} that prints {@link PrettyPrinter#endl()} as one space.
	 *
	 * @return Wrapper
	 * @see #oneLine()
	 */
	public static Wrapper spaceEndl() {
		return SPACE_ENDL;
	}

	private static final Wrapper ONE_LINE = new Wrapper() {
		@Override
		public PrettyPrinter append(PrettyPrintable target) {
			target.mkStringWithoutLineBreak(this);
			return this;
		}
	};

	private static final Wrapper SPACE_ENDL = new Wrapper() {
		@Override
		public PrettyPrinter endl() {
			return append(" ");
		}
	};
}
