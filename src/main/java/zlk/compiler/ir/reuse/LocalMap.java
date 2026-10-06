package zlk.compiler.ir.reuse;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;

import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

/**
 * localIdに対するMap．
 *
 * 実装はSparse Set．
 * @param <V> 値の型
 */
public class LocalMap<V> implements Cloneable {

	private SeqBuffer<LocalVar> dense;
	private SparseEntry[] sparse;

	private record SparseEntry(Object value, int denseIdx) {}

	public LocalMap(int idSize) {
		dense = new SeqBuffer<>();
		sparse = new SparseEntry[idSize];
	}

	/**
	 * エントリーの大きさ
	 * @return
	 */
	public int idSize() {
		return sparse.length;
	}

	/**
	 * コピーコンストラクタ
	 * @param orig
	 */
	public LocalMap(LocalMap<V> orig) {
		dense = new SeqBuffer<LocalVar>(orig.dense);
		sparse = Arrays.copyOf(orig.sparse, orig.sparse.length);
	}

	public boolean isEmply() {
		return dense.isEmpty();
	}

	public boolean contains(LocalVar k) {
		return sparse[k.localId()] != null;
	}

	@SuppressWarnings("unchecked")
	public V getOrElse(LocalVar k, V defaultValue) {
		SparseEntry entry = sparse[k.localId()];
		if(entry == null) {
			return defaultValue;
		}
		return (V) entry.value;
	}

	public V get(LocalVar k) {
		V result = getOrElse(k, null);
		if(result == null) {
			throw new NoSuchElementException("key: "+k.toString());
		}
		return result;
	}

	public void put(LocalVar k, V v) {
		int localId = k.localId();
		if(sparse[localId] != null) {
			throw new IllegalArgumentException("duplicates key: "+k.toString());
		}
		dense.add(k);
		sparse[localId] = new SparseEntry(v, dense.size() - 1);
	}

	/**
	 * 指定したLocalVarの紐づけを解除し，紐づけられていた値を返す．
	 * 紐づけが無かった場合はnull．
	 *
	 * @param k 削除するkey
	 * @return 紐づけられていた値，またはnull
	 */
	@SuppressWarnings("unchecked")
	public V remove(LocalVar k) {
		int localId = k.localId();
		SparseEntry entry = sparse[localId];
		if(entry == null) {
			return null;
		}

		sparse[localId] = null;
		LocalVar denseLast = dense.removeLast();
		if(denseLast.localId() != localId) {
			dense.replace(entry.denseIdx(), denseLast);
			SparseEntry moved = sparse[denseLast.localId()];
			sparse[denseLast.localId()] = new SparseEntry(moved.value(), entry.denseIdx());
		}
		return (V) entry.value;
	}

	@SuppressWarnings("unchecked")
	public void forEach(BiConsumer<LocalVar, V> action) {
		dense.forEach(var -> {
			SparseEntry entry = sparse[var.localId()];
			action.accept(var, (V) entry.value());
		});
	}

	public Seq<LocalVar> keys() {
		return dense.toSeq();
	}
}
