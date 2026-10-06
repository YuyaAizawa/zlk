package zlk.compiler.ir.typing;

import java.util.Comparator;
import java.util.Objects;

import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * canonicalをサポートしたレコードフィールド
 */
public record RecordField<T>(String name, T value) {
	public RecordField(String name, T value) {
		this.name = Objects.requireNonNull(name).intern();  // TODO: 名前の妥当性検査
		this.value = Objects.requireNonNull(value);
	}

	/**
	 * 指定したフィールドからなるcanonicalなレコードを返す
	 * @param <T>
	 * @param fields
	 * @throws 重複した名前があった場合
	 * @return
	 */
	public static <T extends PrettyPrintable> Seq<RecordField<T>> canonicalize(Seq<RecordField<T>> fields) {
		if(fields.size() < 2) {
			return fields;
		}

		// 辞書順にならんで
		Seq<RecordField<T>> sorted = fields.sorted(Comparator.comparing(RecordField::name));

		// 重複が無い
		int dupIdx = Seq.zip(sorted.dropLast(), sorted.drop(1))
				.findFirstIndex((left, right) -> left.name().equals(right.name()));
		if(0 <= dupIdx) {
			throw new IllegalArgumentException(
					"duplicate record field: " + sorted.at(dupIdx).name());
		}

		return sorted;
	}

	public static <T extends PrettyPrintable> void appendTo(
			PrettyPrinter pp,
			Seq<RecordField<T>> r,
			CharSequence sep
	) {
		if(r.isEmpty()) {
			pp.append("{}");
		} else {
			pp.append("{ ");
			RecordField<T> head = r.head();
			pp.append(head.name).append(sep).append(head.value);
			r.tail().forEach(field -> {
				pp.append(", ").append(field.name).append(sep).append(field.value);
			});
			pp.append(" }");
		}
	}
}

