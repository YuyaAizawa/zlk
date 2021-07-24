package zlk.parser;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import zlk.parser.Token.Kind;

public record Token(Kind type, String value) {

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
		;

		private static final Map<String, Kind> keywordLookup =
				List.of(
						TRUE,
						FALSE,
						MODULE
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

	public Token(Kind type) {
		this(type, type.str());
	}
}
