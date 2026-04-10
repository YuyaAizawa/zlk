package zlk.common.id;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import zlk.util.collection.Seq.Folder;
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

	public boolean addIfNotContains(Id id) {
		if(this.contains(id)) {
			return false;
		} else {
			this.add(id);
			return true;
		}
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
		pp.append("[").append(PrettyPrintable.join(this, ", ")).append("]");
	}

	@Override
	public String toString() {
		return buildString();
	}

	public static <E> Folder<E, ?, IdList> folder(Function<? super E, Id> idExtractor) {
		return new Folder<E, IdList, IdList>() {
			@Override
			public BiFunction<? super E, IdList, IdList> accumulator() {
				return (e, ids) -> { ids.add(idExtractor.apply(e)); return ids; };
			}

			@Override
			public IdList initialValue() {
				return new IdList();
			}

			@Override
			public Function<? super IdList, ? extends IdList> finisher() {
				return Function.identity();
			}
		};
	}

	public static Collector<Id, ?, IdList> collector() {
		return Collectors.toCollection(IdList::new);
	}
}
