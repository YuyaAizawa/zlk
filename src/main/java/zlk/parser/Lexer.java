package zlk.parser;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;

/**
 * コードポイント単位の字句解析器．
 *
 * コメント，オフサイドルールなども処理する予定
 * @author YuyaAizawa
 *
 */
public class Lexer {
	private final Reader reader;
	int current;
	int buffer;
	int count;

	int[] wordArray = new int[256];

	public Lexer(String src) {
		this.reader = new StringReader(src);
		next();
		next();
	}

	public Token nextToken() {
		skipWhitespace();

		Token token = switch(current) {

		case ':' -> new Token(Token.Kind.COLON);
		case '=' -> new Token(Token.Kind.EQUAL);
		case '(' -> new Token(Token.Kind.LPAREN);
		case ')' -> new Token(Token.Kind.RPAREN);
		case ';' -> new Token(Token.Kind.SEMICOLON);

		case '-' -> ifNext('>') ? new Token(Token.Kind.ARROW) : new Token(Token.Kind.ILL, '-' + codepointToString(current));

		case  -1 -> new Token(Token.Kind.EOF);

		default -> {
			if(isLower(current)) {
				String id = readWord();
				Token.Kind kind = Token.Kind.lookupKeywordType(id);
				if(kind == null) {
					yield new Token(Token.Kind.LCID, id);
				} else {
					yield new Token(kind);
				}
			} else if(isUpper(current)) {
				yield new Token(Token.Kind.UCID, readWord());
			} else if(isDigit(current)) {
				yield new Token(Token.Kind.DIGITS, readWord());
			} else {
				yield new Token(Token.Kind.ILL, new String(new int[] {current}, 0, 1));
			}
		}};
		next();
		System.out.println(token);
		return token;
	}

	private void next() {
		current = buffer;

		int data = uncheckRead();

		if(Character.isHighSurrogate((char)data)) {
			char high = (char) data;
			int tmp = uncheckRead();
			if(tmp == -1) {
				throw new RuntimeException("EOF while surrogate pair.");
			}
			char low = (char) tmp;
			if(Character.isLowSurrogate(low)) {
				buffer = Character.codePointAt(new char[] {high, low}, 0);
				count++;
				return;
			}
		}

		buffer = data;
		count++;
	}

	private int uncheckRead() {
		try {
			return reader.read();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * 次の文字が指定したものなら読み進めtrueを返し，そうでなければ読み進めずfalseを返す
	 * @param target
	 * @return
	 */
	private boolean ifNext(int target) {
		if(buffer == target) {
			next();
			return true;
		} else {
			return false;
		}
	}

	private void skipWhitespace() {
		while (isWhitespace(current)) {
			next();
		}
	}

	private String readWord() {
		try {
			int idx = 0;
			while(isLetterOrDigit(buffer)) {
				wordArray[idx++] = current;
				next();
			}
			wordArray[idx++] = current;

			return new String(wordArray, 0, idx).intern();
		} catch(ArrayIndexOutOfBoundsException e) {
			throw new RuntimeException("too long word.");
		}
	}

	private static boolean isWhitespace(int cp) {
		return cp == '\s' || cp == '\t' || cp == '\r' || cp == '\n';
	}

	private static boolean isLetterOrDigit(int cp) {
		return
				isLower(cp) ||
				isUpper(cp) ||
				isDigit(cp) ||
				cp == '_';
	}

	private static boolean isLower(int cp) {
		return 'a' <= cp && cp <= 'z';
	}

	private static boolean isUpper(int cp) {
		return 'A' <= cp && cp <= 'Z';
	}

	private static boolean isDigit(int cp) {
		return '0' <= cp && cp <= '9';
	}

	private static String codepointToString(int codepoint) {
		return new String(new int[] {codepoint}, 0, 1);
	}
}
