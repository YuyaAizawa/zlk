package zlk.util.pp;

class BasicPrettyPrinter implements PrettyPrinter {
	static final String INDENT_STRING = "  ";

	final UncheckedAppendable impl;

	boolean isEmptyLine = true;
	int depth = 0;

	public BasicPrettyPrinter(Appendable appendable) {
		this(UncheckedAppendable.from(appendable));
	}

	public BasicPrettyPrinter(UncheckedAppendable uncheckAppendable) {
		impl = uncheckAppendable;
	}

	@Override
	public PrettyPrinter append(CharSequence cs) {
		if(cs.isEmpty()) {
			return this;
		}

		if(isEmptyLine) {
			for(int i = 0; i < depth; i++) {
				impl.append(INDENT_STRING);
			}
			isEmptyLine = false;
		}

		impl.append(cs);

		return this;
	}

	@Override
	public PrettyPrinter endl() {
		impl.append(System.lineSeparator());
		isEmptyLine = true;
		return this;
	}

	@Override
	public PrettyPrinter inc() {
		depth++;
		return this;
	}

	@Override
	public PrettyPrinter dec() {
		depth--;
		if(depth < 0) {
			throw new IllegalStateException();
		}
		return this;
	}
}
