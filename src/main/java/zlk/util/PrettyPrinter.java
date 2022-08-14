package zlk.util;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class PrettyPrinter {
	private static final String INDENT_STRING = "  ";

	private final Appendable out;
	private boolean indented;
	private int depth;

	public PrettyPrinter(Appendable sa) {
		out = sa;
		indented = false;
		depth = 0;
	}

	public PrettyPrinter append(PrettyPrintable value) {
		if(!indented) {
			indent();
		}
		value.mkString(this);
		return this;
	}

	public PrettyPrinter append(int value) {
		return append(Integer.toString(value));
	}

	public PrettyPrinter append(CharSequence csq) {
		if(!indented) {
			indent();
		}
		return appendWithoutIndent(csq);
	}

	private PrettyPrinter appendWithoutIndent(CharSequence csq) {
		try {
			out.append(csq);
			return this;
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public PrettyPrinter endl() {
		appendWithoutIndent(System.lineSeparator());
		indented = false;
		return this;
	}

	private PrettyPrinter indent() {
		for(int i = 0;i < depth;i++) {
			appendWithoutIndent(INDENT_STRING);
		}
		indented = true;
		return this;
	}

	public PrettyPrinter inc() {
		depth++;
		return this;
	}

	public PrettyPrinter dec() {
		depth--;
		return this;
	}
}
