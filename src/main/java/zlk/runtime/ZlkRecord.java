package zlk.runtime;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Zlkレコード値のJavaとの界面
 */
public interface ZlkRecord extends ZlkValue {
	/**
	 * このレコードのフィールド名を辞書順で返す．
	 *
	 * フィールドの存在と型はコンパイル時に検査されるため，
	 * 通常のフィールドアクセスに{@code names()}は使用しない．
	 * このメソッドは主にJava側から利用するときの利便性，テスト用途のために提供する．
	 *
	 * @return 正規順リスト
	 */
	List<String> names();

	/**
	 * 指定したフィールドの値を返す．
	 *
	 * @param name {@link #names()}に含まれるフィールド名
	 * @return フィールドの値
	 * @throws IllegalArgumentException 指定したフィールドが存在しない場合
	 */
	Object get(String name);

	/**
	 * 指定したフィールドの値だけを置き換えた新しいレコード値を返す．
	 *
	 * @param name {@link #names()}に含まれるフィールド名
	 * @param value 新しいフィールド値
	 * @return 当該フィールドを置き換えた新しいレコード値
	 * @throws IllegalArgumentException 指定したフィールドが存在しない場合
	 */
	ZlkRecord update(String name, Object value);

	static ZlkRecord of(Map<String, ?> fields) {
		return ArrayRecord.fromMap(fields);
	}

	/**
	 * 2つのレコードのZlkレコードとしての等価性を返す．
	 *
	 * 等価性はレコードの集合と，それぞれの要素に対応する値が等しいことで定義される．
	 * 実装クラスはこのメソッドをoverrideして良いが，振舞いを変更しないように注意すること．
	 *
	 * @param self
	 * @param other
	 * @return 等価ならtrue
	 */
	static boolean structuralEquals(ZlkRecord self, Object other) {
		if(self == other) {
			return true;
		}
		if(!(other instanceof ZlkRecord record)) {
			return false;
		}

		List<String> names = self.names();
		if(!names.equals(record.names())) {
			return false;
		}
		for(String name : names) {
			if(!Objects.equals(self.get(name), record.get(name))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Zlkレコードとしてのハッシュコードを返す．
	 *
	 * 実装クラスはこのメソッドをoverrideして良いが，振舞いを変更しないように注意すること．
	 *
	 * @param record
	 * @return ハッシュコード
	 */
	static int structuralHashCode(ZlkRecord record) {
		int hash = 1;
		for(String name : record.names()) {
			hash = 31 * hash + name.hashCode();
			hash = 31 * hash + Objects.hashCode(record.get(name));
		}
		return hash;
	}

	static String structuralToString(ZlkRecord record) {
		StringBuilder sb = new StringBuilder();
		record.appendStringTo(sb);
		return sb.toString();
	}

	@Override
	default void appendStringTo(StringBuilder sb) {
		List<String> names = names();

		if(names.isEmpty()) {
			sb.append("{}");
			return;
		}

		sb.append("{ ");
		String firstName = names.get(0);
		sb.append(firstName).append(" = ");
		ZlkValue.appendStringTo(sb, get(firstName));
		for(int i = 1; i < names.size(); i++) {
			sb.append(", ");
			String name = names.get(i);
			sb.append(name).append(" = ");
			ZlkValue.appendStringTo(sb, get(name));
		}
		sb.append(" }");
	}

	@Override
	default void appendStringAsArgTo(StringBuilder sb) {
		appendStringTo(sb);
	}
}
