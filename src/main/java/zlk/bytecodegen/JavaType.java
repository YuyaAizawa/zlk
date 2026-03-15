package zlk.bytecodegen;

/**
 * bytecodeに使われるJVMでcheckcastが必要かどうかを判定する型
 * OBJECT, FUNCTION, VOIDは`==`で比較できる
 * @param expected
 * @return
 */
public sealed interface JavaType
permits JavaType.Primitive, JavaType.Simple, JavaType.Variant {

	/**
	 * 「internした」クラス名を返す
	 * @return
	 */
	public String toClassName();
	public String toDesc();

	/**
	 * コンストラクタの戻り値に出現するVOID
	 */
	public static final Primitive VOID = Primitive.VOID;
	/**
	 * 型消去によりなんだかわからなくなった型 `==`で比較可能
	 */
	public static final Simple OBJECT = new Simple("java/lang/Object", true);
	/**
	 * 型消去された何かの関数 `==`で比較可能
	 */
	public static final Simple FUNCTION = new Simple("java/util/function/Function", true);

	enum Primitive implements JavaType {
		VOID("V");

		private final String desc;
		private Primitive(String desc) {
			this.desc = desc;
		}

		@Override
		public String toClassName() {
			return "NOT_SUPPORTED!";
		}

		@Override
		public String toDesc() {
			return this.desc;
		}
	}


	/**
	 * 一般的なクラスの型（カスタム型のUnion含む）
	 */
	record Simple(String toClassName, String toDesc) implements JavaType {
		public Simple(String className) {
			this(className, false);
		}

		Simple(String className, boolean backdoor) {
			this(backdoor(className, backdoor).intern(), JavaType.classNameToDesc(className).intern());
		}

		private static String backdoor(String className, boolean backdoor) {
			if(!backdoor && (
					className.equals("java/lang/Object") ||
					className.equals("java/util/function/Function"))
			) {
				throw new IllegalArgumentException(className);
			}
			return className;
		}

		@Override
		public final String toString() {
			return toClassName;
		}
	}
	/**
	 * カスタム型のVariant
	 */
	record Variant(String toClassName, String toDesc, String union) implements JavaType {
		public Variant(String className, String union) {
			this(className.intern(), JavaType.classNameToDesc(className).intern(), union.intern());
		}
		@Override
		public final String toString() {
			return toClassName+":"+union;
		}
	}

	public default boolean castRequired(JavaType expected) {
		if(expected == OBJECT || expected == VOID) {
			return false;
		}
		if(this == OBJECT) {
			return true;
		}
		if(expected == FUNCTION) {
			if(this == FUNCTION) {
				return false;
			}
			cannotCast(this, expected);
		}

		return switch(expected) {
		case Primitive.VOID -> cannotCast(this, expected);

		case Simple(String to, String _) ->
			switch(this) {
			case Simple(String from, String _) when to == from -> false;
			case Variant(String _, String _, String union) when to == union -> false;
			default -> cannotCast(this, expected);
			};

		case Variant(String to, String _, String union) ->
			switch(this) {
			case Simple(String from, String _) when union == from -> true;
			case Variant(String from, String _, String _) when to == from -> false;
			default -> cannotCast(this, expected);
			};
		};
	}

	private static boolean cannotCast(JavaType from, JavaType to) {
		throw new IllegalArgumentException("cannnot cast from: "+from+", to: "+to);
	}

	private static String classNameToDesc(String className) {
		return "L" + className + ";";
	}
}
