package zlk.recon;

/**
 * @author YuyaAizawa
 *
 * @param <T>
 * @param <S>
 *
 * @see https://github.com/elm/compiler/blob/0.19.1/compiler/src/Type/UnionFind.hs
 */
class UnionFind<T, S extends UnionFind<T,S>> {
	private UnionFind<T, S> link;
	private T desc;
	private int weight;

	public UnionFind(T value) {
		this.link = null;
		this.desc = value;
		this.weight = 1;
	}

	public UnionFind<T, S> root() {
		if(this.link == null) {
			return this;
		}
		return this.link = link.root();
	}

	public T get() {
		return root().desc;
	}

	public void set(T value) {
		root().desc = value;
	}

	public boolean isSame(UnionFind<T, S> other) {
		return this.root() == other.root();
	}

	public void unite(UnionFind<T, S> other, T newDesc) {
		UnionFind<T, S> a = this.root();
		UnionFind<T, S> b = other.root();
		if(a == b) {
			a.desc = newDesc;
		}
		int newWeight = a.weight + b.weight;

		if(a.weight >= b.weight) {
			b.desc = null;
			b.link = a;
			a.desc = newDesc;
			a.weight = newWeight;
		} else {
			a.desc = null;
			a.link = b;
			b.desc = newDesc;
			b.weight = newWeight;
		}
	}

	public boolean redundant() {
		return link != null;
	}
}
