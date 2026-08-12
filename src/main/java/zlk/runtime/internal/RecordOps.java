package zlk.runtime.internal;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Map;

import zlk.runtime.ZlkRecord;

/**
 * レコード用の内部ABI
 *
 * TODO: 応用の単位でなく，内部最適化用と外部から呼ぶ受け渡し用で分けた方がいいか？
 *       その場合BytecodeGenerator内の文字列も直す
 */
public final class RecordOps {
	private RecordOps() {}

	public static ZlkRecord fromMap(Map<String, Object> fields) {
		return ArrayRecord.fromMap(fields);
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
					MethodHandles.constant(ZlkRecord.class, ArrayRecord.EMPTY).asType(callSiteType));
		}

		MethodHandle target = MethodHandles.lookup().findStatic(
				ArrayRecord.class,
				"owned",
				MethodType.methodType(ZlkRecord.class, String[].class, Object[].class));
		target = MethodHandles.insertArguments(target, 0, (Object) names);
		target = target.asCollector(Object[].class, names.length);
		return new ConstantCallSite(target.asType(callSiteType));
	}

	/**
	 * 指定したレコード値の指定したフィールドの値をin-placeに更新する．
	 *
	 * @param record in-place更新するレコード
	 * @param field {@link ZlkRecord#names()}に含まれるフィールド名
	 * @param value 新しいフィールドの値
	 * @return 指定したレコード
	 * @throws IllegalArgumentException 指定したフィールドが存在しない場合
	 */
	public static ZlkRecord inplaceUpdate(ZlkRecord record, String field, Object value) {
		if (record instanceof ArrayRecord array) {
			return array.inplaceUpdate(field, value);
		}
		return record.update(field, value);
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
}
