package zlk.parser;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record Token(Kind kind, String value) {

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
		SEMICOLON (";"),

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
			this.str = str;
		}

		public String str() {
			return str;
		}

		public static Kind lookupKeywordType(String id) {
			return keywordLookup.getOrDefault(id, null);
		}
	}

	public Token(Kind kind) {
		this(kind, kind.str());
	}
}
