package zlk.util.pp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

class BufferPrettyPrinter implements PrettyPrinter {

	protected final List<Consumer<PrettyPrinter>> buffer;

	public BufferPrettyPrinter() {
		buffer = new ArrayList<>();
	}

	public boolean isEmpty() {
		return buffer.isEmpty();
	}

	public PrettyPrinter flush(PrettyPrinter dst) {
		buffer.forEach(action -> action.accept(dst));
		buffer.clear();
		return dst;
	}

	@Override
	public PrettyPrinter append(CharSequence cs) {
		if(cs.isEmpty()) {
			return this;
		}
		buffer.add(pp -> pp.append(cs));
		return this;
	}

	@Override
	public PrettyPrinter endl() {
		buffer.add(PrettyPrinter::endl);
		return this;
	}

	@Override
	public PrettyPrinter inc() {
		buffer.add(PrettyPrinter::inc);
		return this;
	}

	@Override
	public PrettyPrinter dec() {
		buffer.add(PrettyPrinter::dec);
		return this;
	}
}
