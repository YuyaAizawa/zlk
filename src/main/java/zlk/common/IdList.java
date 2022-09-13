package zlk.common;

import java.util.ArrayList;
import java.util.Collection;

import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

@SuppressWarnings("serial")
public class IdList extends ArrayList<Id> implements PrettyPrintable {

	public IdList() {}
	public IdList(int initialSize) {
		super(initialSize);
	}
	public IdList(Collection<? extends Id> ids) {
		super(ids);
	}

	public IdList substId(IdMap<Id> map) {
		if(stream().anyMatch(map::containsKey)) {
			IdList retVal = new IdList(size());
			forEach(id -> retVal.add(map.getOrDefault(id, id)));
			return retVal;
		}
		return this;
	}

	public boolean contains(Id id) {
		return super.contains(id);
	}

	@Override
	public boolean contains(Object o) {
		throw new RuntimeException("do not check as object");
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.oneline(this, " ");
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		pp(sb);
		return sb.toString();
	}
}
