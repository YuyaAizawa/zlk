package zlk.parser;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;

/**
 * コードポイントベースの字句解析器．
 *
 * コメントも処理する予定
 * @author YuyaAizawa
 *
 */
public class Lexer {
	private final String fileName;
	private final Reader reader;
	int current;
	int buffer;
	int count;
	
	int currentLine;
	int currentColumn;

	int[] wordArray = new int[256];

	private static final char CR = '\r';
	private static final char LF = '\n';

	public Lexer(String fileName, String src) {
		this.fileName = fileName;
		this.reader = new StringReader(src);
		next();
		next();
		currentColumn = 1;
		currentLine = 1;
	}

	public String getFileName() {
		return fileName;
	}

	public Token nextToken() {
		skipWhitespace();

		Token token = switch(current) {

		case ':' -> new Token(Token.Kind.COLON, currentLine, currentColumn);
		case '=' -> new Token(Token.Kind.EQUAL, currentLine, currentColumn);
		case '(' -> new Token(Token.Kind.LPAREN, currentLine, currentColumn);
		case ')' -> new Token(Token.Kind.RPAREN, currentLine, currentColumn);

		case '-' -> ifNext('>')
				? new Token(Token.Kind.ARROW, currentLine, currentColumn)
				: new Token(Token.Kind.ILL, '-' + codepointToString(current), currentLine, currentColumn);

		case  -1 -> new Token(Token.Kind.EOF, currentLine, currentColumn);

		default -> {
			if(isLower(current)) {
				int startColumn = currentColumn;
				String id = readWord();
				Token.Kind kind = Token.Kind.lookupKeywordType(id);
				if(kind == null) {
					yield new Token(Token.Kind.LCID, id, currentLine, startColumn);
				} else {
					yield new Token(kind, currentLine, startColumn);
				}
			} else if(isUpper(current)) {
				int startColumn = currentColumn;
				yield new Token(Token.Kind.UCID, readWord(), currentLine, startColumn);
			} else if(isDigit(current)) {
				int startColumn = currentColumn;
				yield new Token(Token.Kind.DIGITS, readWord(), currentLine, startColumn);
			} else {
				yield new Token(Token.Kind.ILL, new String(new int[] {current}, 0, 1), currentLine, currentColumn);
			}
		}};
		next();
		return token;
	}

	private void next() {
		if(current == LF) {
			currentLine++;
			currentColumn = 0;
		}
		current = buffer;
		currentColumn++;

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
		return cp == '\s' || cp == CR || cp == LF;
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
