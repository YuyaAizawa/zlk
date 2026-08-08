package zlk.common.id;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import zlk.util.collection.Accumulator;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

public final class IdSet implements Accumulator<Id> {

	private Map<Id, Id> impl;

	public IdSet(IdSet init) {
		this.impl = new HashMap<>(init.impl);
	}

	public IdSet() {
		this.impl = new HashMap<>();
	}

	public boolean contains(Id id) {
		return impl.containsKey(id);
	}

	@Override
	public void add(Id id) {
		impl.putIfAbsent(id, id);
	}

	public void forEach(Consumer<? super Id> action) {
		impl.values().forEach(action);
	}

	public Seq<Id> toSeq() {
		SeqBuffer<Id> acc = new SeqBuffer<Id>(impl.size());
		forEach(acc::add);
		return acc.toSeq();
	}
}
