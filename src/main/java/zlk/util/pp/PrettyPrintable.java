package zlk.util.pp;

import java.util.Iterator;

@FunctionalInterface
public interface PrettyPrintable {
	void mkString(PrettyPrinter pp);

	default void mkStringWithoutLineBreak(PrettyPrinter pp) {
		mkString(pp.wrap(Wrapper.spaceEndl()));
	}

	default void pp(Appendable out) {
		mkString(new BasicPrettyPrinter(out));
	}

	default String buildString() {
		StringBuilder sb = new StringBuilder();
		mkString(new BasicPrettyPrinter(sb));
		return sb.toString();
	}

	public static PrettyPrintable join(Iterator<? extends PrettyPrintable> iter, PrettyPrintable sep) {
		return pp -> {
			if(iter.hasNext()) {
				pp.append(iter.next());
				iter.forEachRemaining(e -> pp.append(sep).append(e));
			}
		};
	}

	public static PrettyPrintable join(Iterator<? extends PrettyPrintable> iter, CharSequence sep) {
		return pp -> {
			if(iter.hasNext()) {
				pp.append(iter.next());
				iter.forEachRemaining(e -> pp.append(sep).append(e));
			}
		};
	}

	public static PrettyPrintable toElmListStyle(Iterator<? extends PrettyPrintable> iter) {
		return new PrettyPrintable() {

			@Override
			public void mkString(PrettyPrinter pp) {
				if(!iter.hasNext()) {
					pp.append("[]");
					return;
				}
				pp.append("[ ").append(iter.next());
				if(!iter.hasNext()) {
					pp.append(" ]");
					return;
				}
				iter.forEachRemaining(e -> pp.endl().append(", ").append(e));
				pp.endl().append("]");
			}

			@Override
			public void mkStringWithoutLineBreak(PrettyPrinter pp) {
				pp.append("[").append(join(iter, ", ")).append("]");
			}
		};
	}
}
