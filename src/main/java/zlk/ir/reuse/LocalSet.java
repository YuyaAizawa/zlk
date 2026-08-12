package zlk.ir.reuse;

import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Predicate;

import zlk.ir.reuse.LocalVar;
import zlk.util.collection.Seq;

public class LocalSet implements Cloneable {
	private final LocalMap<LocalVar> impl;

	public LocalSet(int idSize) {
		impl = new LocalMap<>(idSize);
	}

	public int idSize() {
		return impl.idSize();
	}

	/**
	 * コピーコンストラクタ
	 * @param orig
	 */
	public LocalSet(LocalSet orig) {
		impl = new LocalMap<LocalVar>(orig.impl);
	}

	public boolean contains(LocalVar var) {
		return impl.contains(var);
	}

	/**
	 * 指定したLocalVarを追加する．
	 * @param var
	 * @throws IllegalArgumentException 既に追加済みの場合
	 */
	public void add(LocalVar var) {
		impl.put(var, var);
	}

	public void addIfNotContains(LocalVar var) {
		if(!impl.contains(var)) {
			impl.put(var, var);
		}
	}

	/**
	 * 指定したLocalVarを削除する．
	 *
	 * @param k 削除するkey
	 * @throws NoSuchElementException 指定したLocalVarが無かった場合
	 */
	public void remove(LocalVar var) {
		LocalVar result = impl.remove(var);
		if(result == null) {
			throw new NoSuchElementException(var.toString());
		}
	}

	public void removeIfContains(LocalVar var) {
		impl.remove(var);
	}

	public void forEach(Consumer<LocalVar> action) {
		impl.forEach((k, _) -> action.accept(k));
	}

	public Seq<LocalVar> toSeq() {
		return impl.keys();
	}

	public LocalSet filter(Predicate<LocalVar> predicate) {
		LocalSet result = new LocalSet(impl.idSize());
		forEach(var -> {
			if(predicate.test(var)) {
				result.add(var);
			}
		});
		return result;
	}

	public void retainAll(LocalSet other) {
		toSeq().forEach(var -> {
			if(!other.contains(var)) {
				this.remove(var);
			}
		});
	}

	public static LocalSet union(LocalSet a, LocalSet b) {
		int aSize = a.impl.idSize();
		int bSize = b.impl.idSize();
		if(aSize != bSize) {
			throw new IllegalArgumentException("idSize unmatch: "+aSize+", "+bSize);
		}

		LocalSet result = new LocalSet(a);
		b.forEach(result::addIfNotContains);
		return result;
	}

	public static LocalSet intersect(LocalSet a, LocalSet b) {
		int aSize = a.impl.idSize();
		int bSize = b.impl.idSize();
		if(aSize != bSize) {
			throw new IllegalArgumentException("idSize unmatch: "+aSize+", "+bSize);
		}

		return a.filter(b::contains);
	}
}
