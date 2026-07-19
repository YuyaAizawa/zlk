package zlk.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 配列を用いた汎用のZlkレコード値
 *
 * 特殊化したデータ型で最適化した際はfallbackとなる予定
 */
public final class ArrayRecord implements ZlkRecord {
	private static final ArrayRecord EMPTY =
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

	public static CallSite bootstrapLiteral(
			MethodHandles.Lookup caller,
			String invokedName,
			MethodType callSiteType,
			String encodedNames) throws ReflectiveOperationException {
		String[] names = decodeNames(encodedNames);
		if(callSiteType.returnType() != ZlkRecord.class) {
			throw new IllegalArgumentException("record literal must return ZlkRecord");
		}
		if(callSiteType.parameterCount() != names.length) {
			throw new IllegalArgumentException(
					"record field count mismatch: " + names.length + " names but "
					+ callSiteType.parameterCount() + " values");
		}

		if(names.length == 0) {
			return new ConstantCallSite(
					MethodHandles.constant(ZlkRecord.class, EMPTY).asType(callSiteType));
		}

		MethodHandle target = MethodHandles.lookup().findStatic(
				ArrayRecord.class,
				"owned",
				MethodType.methodType(ZlkRecord.class, String[].class, Object[].class));
		target = MethodHandles.insertArguments(target, 0, (Object) names);
		target = target.asCollector(Object[].class, names.length);
		return new ConstantCallSite(target.asType(callSiteType));
	}

	@SuppressWarnings("unused")
	private static ZlkRecord owned(String[] names, Object[] values) {
		return new ArrayRecord(names, values);
	}

	private static String[] decodeNames(String encoded) {
		if(encoded == null || !encoded.startsWith("v1;")) {
			throw new IllegalArgumentException("invalid record shape encoding");
		}

		ArrayList<String> names = new ArrayList<>();
		String previous = null;
		int offset = 3;
		while(offset < encoded.length()) {
			int separator = encoded.indexOf('#', offset);
			if(separator == -1 || separator == offset) {
				throw new IllegalArgumentException("invalid record shape encoding");
			}

			int length;
			try {
				length = Integer.parseInt(encoded, offset, separator, 10);
			} catch(NumberFormatException e) {
				throw new IllegalArgumentException("invalid record shape encoding", e);
			}
			int start = separator + 1;
			int end = start + length;
			if(length <= 0 || end < start || end > encoded.length()) {
				throw new IllegalArgumentException("invalid record shape encoding");
			}

			String name = encoded.substring(start, end);
			if(previous != null && previous.compareTo(name) >= 0) {
				throw new IllegalArgumentException("record fields are not in canonical order: " + name);
			}
			names.add(name);
			previous = name;
			offset = end;
		}
		return names.toArray(String[]::new);
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
