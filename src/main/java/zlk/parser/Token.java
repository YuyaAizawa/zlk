package zlk.parser;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import zlk.util.Location;

public record Token(Kind kind, String value, Location location) {

	public enum Kind {
		UCID(""),
		LCID(""),
		DIGITS(""),
		EOF(""),
		ILL(""),

		ARROW     ("->"),
		COLON     (":"),
		EQUAL     ("="),
		LPAREN    ("("),
		RPAREN    (")"),

		TRUE("true"),
		FALSE("false"),
		MODULE("module"),
		LET("let"),
		IN("in"),
		IF("if"),
		THEN("then"),
		ELSE("else"),
		;

		private static final Map<String, Kind> keywordLookup =
				List.of(
						TRUE,
						FALSE,
						MODULE,
						LET,
						IN,
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

	public Token(Kind kind, String str, int line, int column) {
		this(kind, str, new Location(line, column));
	}

	public Token(Kind kind, int line, int column) {
		this(kind, kind.str(), line, column);
	}

	public int line() {
		return location.line();
	}

	public int column() {
		return location.column();
	}
}
