package zlk.common;

import java.util.ArrayList;
import java.util.Collection;

import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

@SuppressWarnings("serial")
public class IdList extends ArrayList<Id> implements PrettyPrintable {

	public IdList() {}
	public IdList(Collection<? extends Id> ids) {
		super(ids);
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
