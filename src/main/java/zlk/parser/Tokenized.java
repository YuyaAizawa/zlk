package zlk.parser;

import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 字句解析の結果をPEGパーサから利用するための形式
 */
public final class Tokenized {
	private final Token[] tokens;
	private int idx;

	public Tokenized(Token[] tokens) {
		this.tokens = tokens;
		this.idx = 0;
	}

	/**
	 * 全トークン数を返す．
	 * @return このソースのトークン数
	 */
	public int length() {
		return tokens.length;
	}

	/**
	 * 現在の読み取り位置からさらにトークンを読み取れるかどうかを返す．
	 * @return 読み取ればtrue
	 */
	public boolean hasNext() {
		return idx < tokens.length;
	}

	/**
	 * 現在の読み取り位置から1トークン読み取り，読み取り位置を1つ進める．
	 * @return トークン
	 * @throws NoSuchElementException トークンが無い場合
	 */
	public Token next() {
		if(!hasNext()) {
			throw new NoSuchElementException();
		}
		return tokens[idx++];
	}

	/**
	 * 現在の読み取り位置から1トークン読み取るが読取り位置は進めない．
	 * @return トークン
	 * @throws NoSuchElementException トークンが無い場合
	 */
	public Token peek() {
		if(!hasNext()) {
			throw new NoSuchElementException();
		}
		return tokens[idx];
	}

	/**
	 * このソースの現在の読み取り位置を返す．
	 * @return 現在の読み取り位置
	 */
	public int mark() {
		return idx;
	}

	/**
	 * このソースの読み取り位置を指定する.
	 * @param index インデックス
	 * @throws IndexOutOfBoundsException 指定した位置が無効な場合
	 */
	public void jump(int index) {
		if(index < 0 || tokens.length < idx) {
			throw new IndexOutOfBoundsException("index: "+index+", length: "+tokens.length);
		}
		idx = index;
	}

	public void forEach(Consumer<? super Token> action) {
		Stream.of(tokens).forEach(action);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("idx: ").append(idx).append(", tokens: [");
		boolean first = true;
		for(Token token : tokens) {
			if(!first) {
				sb.append(", ");
			}
			sb.append(token);
			first = false;
		}
		sb.append("]");
		return sb.toString();
	}
}
