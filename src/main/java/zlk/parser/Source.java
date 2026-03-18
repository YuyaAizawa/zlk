package zlk.parser;

import java.util.Arrays;

import zlk.common.Position;

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
}