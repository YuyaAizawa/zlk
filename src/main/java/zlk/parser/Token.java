package zlk.parser;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import zlk.util.Position;

public record Token(Kind kind, String value, Position pos) {

	public enum Kind {
		UCID(""),
		LCID(""),
		DIGITS(""),
		BR(""),
		EOF(""),
		ILL(""),

		ARROW     ("->"),
		BAR       ("|"),
		COLON     (":"),
		EQUAL     ("="),
		LAMBDA    ("\\"),
		LPAREN    ("("),
		RPAREN    (")"),
		UNIT      ("()"),

		TRUE("true"),
		FALSE("false"),
		MODULE("module"),
		TYPE("type"),
		LET("let"),
		IN("in"),
		CASE("case"),
		OF("of"),
		IF("if"),
		THEN("then"),
		ELSE("else"),
		;

		private static final Map<String, Kind> keywordLookup =
				List.of(
						TRUE,
						FALSE,
						MODULE,
						TYPE,
						LET,
						IN,
						CASE,
						OF,
						IF,
						THEN,
						ELSE
						)
				.stream()
				.collect(Collectors.toMap(Kind::str, t -> t));

		private final String str;

		private Kind(String str) {
			this.str = str.intern();
		}

		public String str() {
			return str;
		}

		public static Kind lookupKeywordType(String id) {
			return keywordLookup.getOrDefault(id, null);
		}
	}

	public Token(Kind kind, Position pos) {
		this(kind, kind.str(), pos);
	}

	public int line() {
		return pos.line();
	}

	public int column() {
		return pos.column();
	}

	public Position endPos() {
		int codepoints = value.codePointCount(0, value.length());
		return new Position(line(), column() + codepoints);
	}
}
