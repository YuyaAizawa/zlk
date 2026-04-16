package zlk.parser;

import static zlk.util.ErrorUtils.todo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import zlk.parser.Token.Kind;
import zlk.util.collection.IntSeqBuffer;
import zlk.util.collection.SeqBuffer;

/**
 * ソースをトークンのインデントで分離されたブロック構造に変換する
 * 周りよりインデントの深い場所はENDENTとDEDENTで囲み，同じ高さの改行は
 *
 * ソースをトークンに変換する．改行文字は\n, \r\n, \rに対応．
 *
 * 行ごとに行頭にインデントの増減を表すstromaトークン，その後に行中のparenchymaトークンを列にする．
 * 行頭はインデントが増えた場合INDENT，減った場合DEDENTをレベルの増減と同じ数だけ入れる．
 * 増減が無ければNEWLINE，1つが対応する．
 * parenchymaが1つもない行は飛ばす．
 */
public final class Lexer {
	private final String fileName;
	private final String src;
	int idx;  // 現在の読取り位置
	int currentLevel;  // 現在のインデントレベル
	int lineStartIdx;  // 現在の行の先頭位置
	IndentStyle indentStyle;
	IntSeqBuffer lineStartIndexes;
	SeqBuffer<Token> result;

	public Lexer(String fileName, String src) {
		this.fileName = fileName;
		this.src = src;
	}

	public Lexer(String fileName) throws IOException {
		this(fileName, Files.readString(Paths.get(fileName)));
	}

	public Tokenized lex() {
		idx = 0;
		currentLevel = 0;
		indentStyle = IndentStyle.UNKNOWN;
		lineStartIndexes = new IntSeqBuffer();
		result = new SeqBuffer<>();
		Source info = new Source(fileName, src);
		int srcLength = src.length();

		READ_LOOP:
		while(idx < srcLength) {
			// 行のはじめにここに戻る
			lineStartIdx = idx;
			lineStartIndexes.add(lineStartIdx);

			// 行頭の空白からインデントを計算
			indentStyle = indentStyle.initIndentStyleIfNeeded(src, idx);
			int level = indentStyle.count(src, idx);
			idx += level * indentStyle.value.length();

			// 行末まで空なら次の行へ コメントを処理するならたぶんココ
			char c = src.charAt(idx);
			if(c == '\r') {
				idx++;
				if(idx < srcLength && src.charAt(idx) == '\n') {
					idx++;
				}
				continue READ_LOOP;
			} else if(c == '\n') {
				idx++;
				continue READ_LOOP;
			} else if(isPrintableAscii(c)) {
				// 中身のある行だけ後続の処理へ
			} else {
				todo("'"+c+"' is unsupported"); // TODO: スペース奇数のときの対応とか
			}

			// インデントトークンを追加
			int levelDiff = currentLevel - level;
			if(levelDiff < 0) {
				for(int i = levelDiff; i < 0; i++) {
					result.push(new Token(info, Token.Kind.ENDENT, lineStartIdx, idx));
				}
			}
			if(levelDiff > 0) {
				for(int i = levelDiff; i > 0; i--) {
					result.push(new Token(info, Token.Kind.DEDENT, lineStartIdx, idx));
				}
			}
			result.push(new Token(info, Token.Kind.SAMENT, lineStartIdx, idx));
			currentLevel = level;

			// 行内トークンを追加
			while(idx < srcLength) {
				c = src.charAt(idx);

				// 改行なら行内処理を終える
				if(c == '\r') {
					idx++;
					if(idx < srcLength && src.charAt(idx) == '\n') {
						idx++;
					}
					break;
				} else if(c == '\n') {
					idx++;
					break;
				}

				// トークン開始
				int start = idx;
				if(isUpper(c)) {  // UCID
					while(++idx < srcLength && isIdentifierPart(src.charAt(idx)));
					result.push(new Token(info, Kind.UCID, start, idx));
				} else if(isLower(c)) {  // LCID or keyword
					while(++idx < srcLength && isIdentifierPart(src.charAt(idx)));
					Kind k = Token.Kind.lookupKeyword(src.substring(start, idx));
					result.push(new Token(info, k == null ? Kind.LCID : k, start, idx));
				} else if(isPunctuator(c)) {  // punctuator
					Kind k = Token.Kind.lookupPunctuator(src, start);
					if(k != null) {
						idx += k.str().length();
						result.push(new Token(info, k, start, idx));
					} else {
						while(++idx < srcLength && isPunctuator(src.charAt(idx)));
						result.push(new Token(info, Kind.ILL, start, idx));
					}
				} else if(isDigit(c)) {  // DIGIT
					while(++idx < srcLength && isDigit(src.charAt(idx)));
					result.push(new Token(info, Kind.DIGITS, start, idx));
				} else {
					todo("unsupported token start char'"+c+"'@"+idx);
				}
				if(idx < srcLength && src.charAt(idx) == ' ') {  // スペースがあれば1つまで飛ばす
					idx++;
				}
			}
			// TODO: 行末の処理 なんかあったっけ
		}
		// 閉じていないブロックを閉じる
		for (int i = 0; i < currentLevel; i++) {
			result.push(new Token(info, Token.Kind.DEDENT, idx, idx));
		}
		// 共有情報に改行位置を登録
		info.lineStartIndexes = lineStartIndexes.toArray();

		return new Tokenized(result.toArray(Token[]::new));
	}

	private static boolean isPrintableAscii(char c) {
		return '!' <= c && c <= '~';
	}
	private static boolean isUpper(char c) {
		return 'A' <= c && c <= 'Z';
	}
	private static boolean isLower(char c) {
		return 'a' <= c && c <= 'z';
	}
	private static boolean isDigit(char c) {
		return '0' <= c && c <= '9';
	}
	private static boolean isIdentifierPart(char c) {
		return isUpper(c) || isLower(c) || isDigit(c) || c == '_';
	}
	private static boolean isPunctuator(char c) {
		return '!' <= c && c <= '/'
				|| ':' <= c && c <= '@'
				|| '[' <= c && c <= '`'
				|| '{' <= c && c <= '~';
	}
}

enum IndentStyle {
	UNKNOWN(""),
	TAB("\t"),
	SPACE2("  "),
	SPACE4("    ");

	String value;
	private IndentStyle(String value) {
		this.value = value;
	}

	IndentStyle initIndentStyleIfNeeded(String src, int i) {
		if(this != UNKNOWN) {
			return this;
		}
		if(src.startsWith(TAB.value, i)) {
			return TAB;
		} else if(src.startsWith(SPACE4.value, i)) {
			return SPACE4;
		} else if(src.startsWith(SPACE2.value, i)) {
			return SPACE2;
		}
		return UNKNOWN;
	}

	/**
	 * 文字列の指定された位置から，このインデントでのレベルを返す．
	 * @param src
	 * @param offset
	 * @return インデントレベル
	 */
	int count(String src, int offset) {
		char prefix = src.charAt(offset);
		if(prefix != ' ' && prefix != '\t') {
			return 0;
		}
		if(this == UNKNOWN) {
			throw new IllegalStateException("UNKOWN indent cannot count.");
		}
		int c = 0;
		while(src.startsWith(value, offset)) {
			c++;
			offset += value.length();
		}
		return c;
	}
}