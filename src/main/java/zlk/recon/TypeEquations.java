package zlk.recon;

import java.util.ArrayList;
import java.util.function.BiConsumer;

public final class TypeEquations {
	private final ArrayList<TypeEquation> impl = new ArrayList<>();

	public boolean isEmpty() {
		return impl.isEmpty();
	}

	public int size() {
		return impl.size();
	}

	public void push(TypeSchema left, TypeSchema right) {
		impl.add(new TypeEquation(left, right));
	}

	public void pop(BiConsumer<? super TypeSchema, ? super TypeSchema> action) {
		TypeEquation e = impl.remove(impl.size() - 1);
		action.accept(e.left, e.right);
	}

	public void substitute(TsVar var, TypeSchema type) {
		impl.forEach(e -> e.substitute(var, type));
	}

	public void forEach(BiConsumer<TypeSchema, TypeSchema> action) {
		impl.forEach(e -> action.accept(e.left, e.right));
	}
}
