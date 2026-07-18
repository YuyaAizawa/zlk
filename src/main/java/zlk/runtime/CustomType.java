package zlk.runtime;

/**
 * ZLKのカスタム型値が持つ文字列表現の契約．
 */
public interface CustomType {
	void appendStringTo(StringBuilder sb);

	void appendStringAsArgTo(StringBuilder sb);

	static void appendStringTo(StringBuilder sb, Object value) {
		switch(value) {
		case Integer i -> sb.append(i);
		case Boolean b -> sb.append(b ? "True" : "False");
		case CustomType custom -> custom.appendStringTo(sb);
		case null -> sb.append("null");  // NPE occurs without null case
		default -> sb.append(value);
		}
	}

	static void appendStringAsArgTo(StringBuilder sb, Object value) {
		if(value instanceof CustomType custom) {
			custom.appendStringAsArgTo(sb);
		} else {
			appendStringTo(sb, value);
		}
	}
}
