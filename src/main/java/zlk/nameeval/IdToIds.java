package zlk.nameeval;

import java.util.function.BiConsumer;

import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

public final class IdToIds {
	private IdMap<SeqBuffer<Id>> impl = new IdMap<>();

	public void add(Id from, Id to) {
		SeqBuffer<Id> buffer = impl.getOrNull(from);
		if(buffer == null) {
			buffer = new SeqBuffer<>();
			impl.put(from, buffer);
		}
		if(!buffer.contains(to)) {
			buffer.add(to);
		}
	}

	public Seq<Id> get(Id from) {
		return impl.getOptional(from).map(SeqBuffer::toSeq).orElse(Seq.of());
	}

	public void forEach(BiConsumer<Id, Seq<Id>> action) {
		impl.forEach((id, buf) -> action.accept(id, buf.toSeq()));
	}

	@Override
	public String toString() {
		return impl.buildString();
	}
}
