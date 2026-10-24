package studio.pvf.intellij;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerBase;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;

/** Lightweight lexical colouring, deliberately independent of a full PSI/index. */
final class PvfSyntaxHighlighter extends SyntaxHighlighterBase {
    private static final IElementType TAG = new IElementType("PVF_TAG", null);
    private static final IElementType STRING = new IElementType("PVF_STRING", null);
    private static final IElementType COMMENT = new IElementType("PVF_COMMENT", null);
    private static final IElementType NUMBER = new IElementType("PVF_NUMBER", null);
    private static final IElementType TEXT = new IElementType("PVF_TEXT", null);

    @Override public Lexer getHighlightingLexer() { return new PvfLexer(); }

    @Override public TextAttributesKey[] getTokenHighlights(IElementType type) {
        if (type == TAG) return pack(DefaultLanguageHighlighterColors.KEYWORD);
        if (type == STRING) return pack(DefaultLanguageHighlighterColors.STRING);
        if (type == COMMENT) return pack(DefaultLanguageHighlighterColors.LINE_COMMENT);
        if (type == NUMBER) return pack(DefaultLanguageHighlighterColors.NUMBER);
        return TextAttributesKey.EMPTY_ARRAY;
    }

    private static final class PvfLexer extends LexerBase {
        private CharSequence buffer = "";
        private int end, start, tokenEnd, state, tokenState;
        private IElementType type;

        @Override public void start(CharSequence text, int startOffset, int endOffset, int initialState) {
            buffer = text; end = endOffset; start = startOffset; state = initialState; scan();
        }
        @Override public int getState() { return tokenState; }
        @Override public IElementType getTokenType() { return type; }
        @Override public int getTokenStart() { return start; }
        @Override public int getTokenEnd() { return tokenEnd; }
        @Override public CharSequence getBufferSequence() { return buffer; }
        @Override public int getBufferEnd() { return end; }
        @Override public void advance() { start = tokenEnd; scan(); }

        private void scan() {
            tokenState = state;
            if (start >= end) { type = null; tokenEnd = start; return; }
            char c = buffer.charAt(start); tokenEnd = start + 1;
            if (state == 1 || c == '`') {
                type = STRING;
                // State 1 means this token resumes an unterminated backtick string.
                if (state == 1) tokenEnd = start;
                while (tokenEnd < end && buffer.charAt(tokenEnd) != '`') tokenEnd++;
                if (tokenEnd < end) { tokenEnd++; state = 0; } else state = 1;
            } else if (c == '/' && tokenEnd < end && buffer.charAt(tokenEnd) == '/') {
                type = COMMENT;
                while (tokenEnd < end && buffer.charAt(tokenEnd) != '\n') tokenEnd++;
            } else if (c == '[') {
                type = TAG;
                while (tokenEnd < end && buffer.charAt(tokenEnd) != ']' && buffer.charAt(tokenEnd) != '\n') tokenEnd++;
                if (tokenEnd < end && buffer.charAt(tokenEnd) == ']') tokenEnd++;
            } else if (Character.isDigit(c) || (c == '-' && tokenEnd < end && Character.isDigit(buffer.charAt(tokenEnd)))) {
                type = NUMBER;
                while (tokenEnd < end && (Character.isDigit(buffer.charAt(tokenEnd)) || buffer.charAt(tokenEnd) == '.')) tokenEnd++;
            } else {
                type = TEXT;
                while (tokenEnd < end) {
                    char next = buffer.charAt(tokenEnd);
                    if (next == '[' || next == '`' || next == '/' || next == '-' || Character.isDigit(next)) break;
                    tokenEnd++;
                }
            }
        }
    }
}
