package zlk.compiler.source;

import java.util.Arrays;

public final class Source {
	public final String fileName;
	public final String content;
	int[] lineStartIndexes = null;

	public Source(String fileName, String src) {
		this.fileName = fileName;
		this.content = src;
	}

	public Position getPosition(int charIdx) {
		int lineIdx = Arrays.binarySearch(lineStartIndexes, charIdx);
		if(lineIdx < 0) {
			lineIdx = -lineIdx - 2;
		}
		return new Position(lineIdx + 1, charIdx - lineStartIndexes[lineIdx] + 1);
	}

	public int getLine(int charIdx) {
		int lineIdx = Arrays.binarySearch(lineStartIndexes, charIdx);
		if(lineIdx < 0) {
			lineIdx = -lineIdx - 2;
		}
		return lineIdx + 1;
	}

	// TODO: 構築時に遅延して初期化が必要だったから入れたが外から操作する作りは良くない
	public void setLineStartIndexes(int[] lineStartIndexes) {
		this.lineStartIndexes = lineStartIndexes;
	}
}