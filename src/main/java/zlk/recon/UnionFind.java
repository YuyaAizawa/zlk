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
	private T content;
	private int weight;

	public UnionFind(T value) {
		this.link = null;
		this.content = value;
		this.weight = 1;
	}

	public UnionFind<T, S> root() {
		if(this.link == null) {
			return this;
		}
		return this.link = link.root();
	}

	public T get() {
		return root().content;
	}

	public void set(T value) {
		root().content = value;
	}

	public boolean isSame(UnionFind<T, S> other) {
		return this.root() == other.root();
	}
	
	public boolean isRedundant() {
		return link != null;
	}

	public void unite(UnionFind<T, S> other, T newContent) {
		UnionFind<T, S> a = this.root();
		UnionFind<T, S> b = other.root();
		if(a == b) {
			a.content = newContent;
		}
		int newWeight = a.weight + b.weight;

		if(a.weight >= b.weight) {
			b.content = null;
			b.link = a;
			a.content = newContent;
			a.weight = newWeight;
		} else {
			a.content = null;
			a.link = b;
			b.content = newContent;
			b.weight = newWeight;
		}
	}

	public boolean redundant() {
		return link != null;
	}
}
