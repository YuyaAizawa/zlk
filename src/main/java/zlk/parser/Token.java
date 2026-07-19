package zlk.parser;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public final class Token implements PrettyPrintable, LocationHolder {
	private final Source info;
	public final Kind kind;
	public final int start;
	public final int end;

	public enum Kind {
		ENDENT(""),
		DEDENT(""),
		SAMENT(""),  // no indentation change

		ILL(""),

		UCID(""),
		LCID(""),
		DIGITS(""),

		// punctuators
		ARROW     ("->"),
		BAR       ("|"),
		COMMA     (","),
		COLON     (":"),
		DOT       ("."),
		EQUAL     ("="),
		LAMBDA    ("\\"),
		LBRACE    ("{"),
		LPAREN    ("("),
		RBRACE    ("}"),
		RPAREN    (")"),
		WILDCARD  ("_"),

		// keywords
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

		public boolean isBlack() {
			return switch(this) {
			case ENDENT, DEDENT, SAMENT -> false;
			default -> true;
			};
		}

		public boolean isPunctuators() {
			return switch(this) {
			case ARROW    -> true;
			case BAR      -> true;
			case COMMA    -> true;
			case COLON    -> true;
			case DOT      -> true;
			case EQUAL    -> true;
			case LAMBDA   -> true;
			case LBRACE   -> true;
			case LPAREN   -> true;
			case RBRACE   -> true;
			case RPAREN   -> true;
			default -> false;
			};
		}

		public boolean isWord() {
			return isBlack() && !isPunctuators();
		}

		private static final Map<Character, Kind> punctuatorLookup =
				Stream.of(
						ARROW,
						BAR,
						COMMA,
						COLON,
						DOT,
						EQUAL,
						LAMBDA,
						LBRACE,
						LPAREN,
						RBRACE,
						RPAREN,
						WILDCARD)  // TODO: すぐ後ろに文字が続くのを禁止 正確にはpunctuatorではない
				.collect(Collectors.toMap(
						k -> k.str().charAt(0),  // 1文字目が被ったら実行時例外
						k -> k));

		private static final Map<String, Kind> keywordLookup =
				Stream.of(
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
						ELSE)
				.collect(Collectors.toMap(Kind::str, t -> t));

		private final String str;

		private Kind(String str) {
			this.str = str.intern();
		}

		public String str() {
			return str;
		}

		/**
		 * srcのoffsetの位置からはじまるpunctuatorがあればそれを返す．なければnull．
		 * @param src
		 * @param offset
		 * @return
		 */
		public static Kind lookupPunctuator(String src, int offset) {
			Kind kind = punctuatorLookup.getOrDefault(src.charAt(offset), null);
			if(kind != null && src.startsWith(kind.str, offset)) {
				return kind;
			}
			return null;
		}

		public static Kind lookupKeyword(String id) {
			return keywordLookup.getOrDefault(id, null);
		}
	}

	public Token(Source info, Kind kind, int start, int end) {
		this.info = info;
		this.kind = kind;
		this.start = start;
		this.end = end;
	}

	public String str() {
		return info.content.substring(start, end);
	}

	@Override
	public Location loc() {
		return new Location(info, start, end);
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(str());
	}
}
