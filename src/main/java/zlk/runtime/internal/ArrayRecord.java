package zlk.runtime.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import zlk.runtime.ZlkRecord;

/**
 * 配列を用いた汎用のZlkレコード値
 *
 * 特殊化したデータ型で最適化した際はfallbackとなる予定
 */
final class ArrayRecord implements ZlkRecord {
	static final ArrayRecord EMPTY =
			new ArrayRecord(
				new String[0],
				new Object[0]
			);

	private final String[] names;
	private final Object[] values;

	private ArrayRecord(String[] names, Object[] values) {
		this.names = names;
		this.values = values;
	}

	static ZlkRecord fromMap(Map<String, ?> fields) {
		Objects.requireNonNull(fields);
		if(fields.isEmpty()) {
			return EMPTY;
		}
		String[] names = fields.keySet().toArray(String[]::new);
		Arrays.sort(names);
		Object[] values = new Object[names.length];  // TODO: namesをinternする？
		for(int i = 0; i < names.length; i++) {
			values[i] = fields.get(names[i]);
		}
		return new ArrayRecord(names, values);
	}

	@SuppressWarnings("unused")
	static ZlkRecord owned(String[] names, Object[] values) {
		return new ArrayRecord(names, values);
	}

	@Override
	public List<String> names() {
		return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(names)));
	}

	@Override
	public Object get(String name) {
		return values[indexOf(name)];
	}

	@Override
	public ZlkRecord update(String name, Object value) {
		int index = indexOf(name);
		Object[] newValues = values.clone();
		newValues[index] = value;
		return new ArrayRecord(names, newValues);
	}

	/**
	 * {@link RecordOps#inplaceUpdate(ZlkRecord, String, Object)} から呼び出すための内部ABI
	 * @param name
	 * @param value
	 * @return
	 */
	ZlkRecord inplaceUpdate(String name, Object value) {
		int index = indexOf(name);
		values[index] = value;
		return this;
	}

	private int indexOf(String name) {
		int index = Arrays.binarySearch(names, name);
		if(index < 0) {
			throw new IllegalArgumentException("unknown field: " + name);
		}
		return index;
	}

	@Override
	public void appendStringTo(StringBuilder sb) {
		ZlkRecord.super.appendStringTo(sb);
	}

	@Override
	public void appendStringAsArgTo(StringBuilder sb) {
		ZlkRecord.super.appendStringAsArgTo(sb);
	}

	@Override
	public boolean equals(Object other) {
		return ZlkRecord.structuralEquals(this, other);
	}

	@Override
	public int hashCode() {
		return ZlkRecord.structuralHashCode(this);
	}

	@Override
	public String toString() {
		return ZlkRecord.structuralToString(this);
	}
}
