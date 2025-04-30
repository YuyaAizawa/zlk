package zlk.nameeval;

import java.util.function.BiConsumer;

import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;

public final class IdToIds {
	private IdMap<IdList> impl = new IdMap<>();

	public void add(Id from, Id to) {
		IdList list = impl.getOrNull(from);
		if(list == null) {
			list = new IdList();
			impl.put(from, list);
		}
		if(!list.contains(to)) {
			list.add(to);
		}
	}

	public IdList get(Id from) {
		return impl.getOrDefault(from, new IdList());
	}

	public void forEach(BiConsumer<Id, IdList> action) {
		impl.forEach(action);
	}

	@Override
	public String toString() {
		return impl.buildString();
	}
}
