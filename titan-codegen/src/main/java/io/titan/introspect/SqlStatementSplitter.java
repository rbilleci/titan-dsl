package io.titan.introspect;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a SQL script into individual statements.
 *
 * <p>Statement boundaries are top-level semicolons. The splitter understands the lexical
 * structure both dialects share so a semicolon inside any of the following never splits a
 * statement:</p>
 *
 * <ul>
 *   <li>single-quoted strings (with {@code ''} doubling and backslash escapes),</li>
 *   <li>double-quoted and backtick-quoted identifiers,</li>
 *   <li>line comments ({@code --} and {@code #}) and block comments ({@code /* ... *&#47;}),</li>
 *   <li>PostgreSQL dollar-quoted strings ({@code $$...$$}, {@code $tag$...$tag$}).</li>
 * </ul>
 *
 * <p>Comments are preserved inside statements (the scratch database does not care) but
 * statements that consist solely of whitespace/comments are dropped.</p>
 */
public final class SqlStatementSplitter {

    private SqlStatementSplitter() {
    }

    public static List<String> split(String sql) {
        var statements = new ArrayList<String>();
        var current = new StringBuilder();

        int i = 0;
        int length = sql.length();
        while (i < length) {
            char c = sql.charAt(i);

            // Line comments.
            if (c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
                int end = endOfLine(sql, i);
                current.append(sql, i, end);
                i = end;
                continue;
            }
            if (c == '#') {
                int end = endOfLine(sql, i);
                current.append(sql, i, end);
                i = end;
                continue;
            }

            // Block comments.
            if (c == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                end = end < 0 ? length : end + 2;
                current.append(sql, i, end);
                i = end;
                continue;
            }

            // Quoted regions.
            if (c == '\'' || c == '"' || c == '`') {
                int end = endOfQuoted(sql, i, c);
                current.append(sql, i, end);
                i = end;
                continue;
            }

            // PostgreSQL dollar quoting: $tag$ ... $tag$ (tag may be empty).
            if (c == '$') {
                int tagEnd = dollarTagEnd(sql, i);
                if (tagEnd > 0) {
                    String tag = sql.substring(i, tagEnd);
                    int close = sql.indexOf(tag, tagEnd);
                    int end = close < 0 ? length : close + tag.length();
                    current.append(sql, i, end);
                    i = end;
                    continue;
                }
            }

            if (c == ';') {
                addIfMeaningful(statements, current);
                current.setLength(0);
                i++;
                continue;
            }

            current.append(c);
            i++;
        }
        addIfMeaningful(statements, current);
        return statements;
    }

    private static void addIfMeaningful(List<String> statements, StringBuilder current) {
        String statement = stripLeadingComments(current.toString()).trim();
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
    }

    /** Drops whitespace and SQL comments preceding the first significant token. */
    public static String stripLeadingComments(String text) {
        int i = 0;
        int length = text.length();
        while (i < length) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '-' && i + 1 < length && text.charAt(i + 1) == '-') {
                i = endOfLine(text, i);
                continue;
            }
            if (c == '#') {
                i = endOfLine(text, i);
                continue;
            }
            if (c == '/' && i + 1 < length && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                i = end < 0 ? length : end + 2;
                continue;
            }
            break;
        }
        return text.substring(i);
    }

    private static int endOfLine(String sql, int from) {
        int newline = sql.indexOf('\n', from);
        return newline < 0 ? sql.length() : newline;
    }

    /**
     * Returns the index just past the closing quote, honoring doubled quotes ({@code ''},
     * {@code ""}, {@code ``}) and backslash escapes inside single quotes.
     */
    private static int endOfQuoted(String sql, int start, char quote) {
        int i = start + 1;
        int length = sql.length();
        while (i < length) {
            char c = sql.charAt(i);
            if (c == '\\' && quote == '\'') {
                i += 2;
                continue;
            }
            if (c == quote) {
                if (i + 1 < length && sql.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return length;
    }

    /**
     * If {@code start} begins a dollar-quote tag ({@code $$} or {@code $word$}), returns the
     * index just past the tag's closing {@code $}; otherwise returns {@code -1}.
     */
    private static int dollarTagEnd(String sql, int start) {
        int i = start + 1;
        int length = sql.length();
        while (i < length) {
            char c = sql.charAt(i);
            if (c == '$') {
                return i + 1;
            }
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return -1;
            }
            i++;
        }
        return -1;
    }
}
