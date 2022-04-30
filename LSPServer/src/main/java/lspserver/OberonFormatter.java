package lspserver;

import java.util.Arrays;
import java.util.NoSuchElementException;

public class OberonFormatter {
	private final StringBuilder builder = new StringBuilder();
	private String currentLine = "";
	private int nextLineIndent = 0;
	private boolean eatWhitespace = false, lastLineEmpty = true;

	public void appendToken(String token, FormatTokenInfo tokenInfo) {
		if (token.isEmpty() || !token.equals(token.trim()))
			throw new IllegalArgumentException("Invalid token: " + token);
		if (currentLine.trim().isEmpty()) {
			for (int i = 0; i < tokenInfo.outdentCount; i++) {
				if (!currentLine.endsWith("  "))
					break;
				currentLine = currentLine.substring(0, currentLine.length() - 2);
			}
			if (tokenInfo.whitespaceBefore == FormatWhitespaceCategory.MULTI_NEWLINE && !lastLineEmpty) {
				builder.append('\n');
				lastLineEmpty = true;
			}
		} else if (tokenInfo.whitespaceBefore == FormatWhitespaceCategory.ONE_SPACE) {
			if (!currentLine.endsWith(" ")) {
				currentLine += " ";
			}
		} else if (tokenInfo.whitespaceBefore == FormatWhitespaceCategory.NO_WHITESPACE
				|| tokenInfo.whitespaceBefore == FormatWhitespaceCategory.MULTI_NEWLINE
				|| tokenInfo.whitespaceBefore == FormatWhitespaceCategory.ONE_NEWLINE) {
			if (currentLine.endsWith(" "))
				currentLine = currentLine.substring(0, currentLine.length() - 1);
			if (tokenInfo.whitespaceBefore != FormatWhitespaceCategory.NO_WHITESPACE) {
				builder.append(currentLine + "\n");
				lastLineEmpty = false;
				if (tokenInfo.whitespaceBefore == FormatWhitespaceCategory.MULTI_NEWLINE) {
					builder.append('\n');
					lastLineEmpty = true;
				}
				currentLine = makeSpaces(2 * (nextLineIndent - tokenInfo.outdentCount));
			}
		}
		currentLine += token;
		nextLineIndent += tokenInfo.indentCount - tokenInfo.outdentCount;
		if (tokenInfo.whitespaceAfter == FormatWhitespaceCategory.ONE_SPACE) {
			currentLine += " ";
		}
		eatWhitespace = (tokenInfo.whitespaceAfter == FormatWhitespaceCategory.NO_WHITESPACE);
	}

	private String makeSpaces(int count) {
		if (count < 0) {
			return "(* INDENT " + count + "*) ";
		}
		char[] spaces = new char[count];
		Arrays.fill(spaces, ' ');
		return new String(spaces);
	}

	public void appendWhitespace(String whitespace) {
		whitespace = whitespace.replace('\t', ' ').replaceAll("  +", " ");
		if (!whitespace.matches("[ \n]+"))
			throw new IllegalArgumentException("Invalid whitespace: " + whitespace);
		if (whitespace.startsWith(" ") && eatWhitespace) {
			whitespace = whitespace.substring(1);
		}
		eatWhitespace = false;
		for (int i = 0; i < whitespace.length(); i++) {
			if (whitespace.charAt(i) == ' ') {
				if (!currentLine.trim().isEmpty() && !currentLine.endsWith(" "))
					currentLine += " ";
			} else if (whitespace.charAt(i) == '\n') {
				if (currentLine.trim().isEmpty()) {
					builder.append('\n');
					lastLineEmpty = true;
				} else {
					if (currentLine.endsWith(" ")) {
						currentLine = currentLine.substring(0, currentLine.length() - 1);
					}
					builder.append(currentLine + "\n");
					lastLineEmpty = false;
				}
				currentLine = makeSpaces(2 * nextLineIndent);
			} else {
				throw new IllegalStateException();
			}
		}
	}

	public String getResult() {
		builder.append(currentLine.toString()); // fail on null
		if (builder.charAt(builder.length() - 1) != '\n')
			builder.append('\n');
		currentLine = null;
		return builder.toString();
	}

	public static enum FormatWhitespaceCategory {
		NO_WHITESPACE(0), ONE_SPACE(1), ONE_NEWLINE(2), MULTI_NEWLINE(3), UNCHANGED(9);

		private int code;

		private FormatWhitespaceCategory(int code) {
			this.code = code;
		}

		public static FormatWhitespaceCategory forCode(int code) {
			for (FormatWhitespaceCategory cat : values())
				if (cat.code == code)
					return cat;
			throw new NoSuchElementException(""+code);
		}
	}

	public static class FormatTokenInfo {
		private int startPos, endPos, indentCount, outdentCount;
		private FormatWhitespaceCategory whitespaceBefore, whitespaceAfter;

		public FormatTokenInfo(int startPos, int endPos, int categories) {
			if (categories % 10 > 1 && categories % 10 < 9)
				throw new IllegalArgumentException("" + categories);
			this.startPos = startPos;
			this.endPos = endPos;
			this.whitespaceBefore = FormatWhitespaceCategory.forCode(categories / 10);
			this.whitespaceAfter = FormatWhitespaceCategory.forCode(categories % 10);
		}

		public void updateCategories(int categories) {
			if (categories % 10 > 1 && categories % 10 < 9)
				throw new IllegalArgumentException("" + categories);
			this.whitespaceBefore = FormatWhitespaceCategory.forCode(categories / 10);
			this.whitespaceAfter = FormatWhitespaceCategory.forCode(categories % 10);
		}

		public void indentNextLine() {
			indentCount++;
		}

		public void outdentThisLine() {
			outdentCount++;
		}

		public int getStartPos() {
			return startPos;
		}

		public int getEndPos() {
			return endPos;
		}
	}
}
