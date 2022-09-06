package zlk.util.pp;

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
		}

		return this;
	}

	default PrettyPrinter singleline(Iterable<PrettyPrintable> targets, CharSequence delimiter) {

		PrettyPrinter impl = wrap(new Wrapper() {
			boolean appended = false;

			@Override
			public PrettyPrinter append(CharSequence cs) {
				if(cs.isEmpty()) {
					return this;
				}
				appended = true;
				return super.append(cs);
			}

			@Override
			public PrettyPrinter endl() {
				if(appended) {
					append(" ");
					appended = false;
				}
				return this;
			}
		});

		boolean first = true;
		for(PrettyPrintable target : targets) {
			if(!first) {
				impl.append(delimiter);
			}
			impl.append(target);
			first = false;
		}

		return this;
	}
}

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
}
