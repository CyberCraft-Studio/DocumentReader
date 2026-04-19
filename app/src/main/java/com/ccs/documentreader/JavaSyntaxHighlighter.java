package com.ccs.documentreader;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Легка підсвітка Java-коду в HTML (без зовнішніх бібліотек).
 * Обробляє рядки, символьні літерали, однорядкові та багаторядкові коментарі, анотації {@literal @}.
 */
public final class JavaSyntaxHighlighter {

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
        "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
        "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
        "interface", "long", "native", "new", "package", "private", "protected", "public",
        "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while",
        "true", "false", "null",
        "var", "yield", "record", "sealed", "permits", "non-sealed", "exports", "module",
        "open", "opens", "provides", "requires", "to", "transitive", "uses", "with"
    ));

    private JavaSyntaxHighlighter() {}

    public static String highlightToHtml(String src) {
        if (src == null || src.isEmpty()) return "";
        int n = src.length();
        StringBuilder out = new StringBuilder(n + n / 4);
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '\r') {
                i++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                appendEsc(out, c);
                i++;
                continue;
            }
            // Текстові блоки """
            if (c == '"' && i + 2 < n && src.charAt(i + 1) == '"' && src.charAt(i + 2) == '"') {
                int end = findTextBlockEnd(src, i + 3, n);
                out.append("<span class=\"java-str\">");
                for (int k = i; k < end; k++) appendEsc(out, src.charAt(k));
                out.append("</span>");
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < n) {
                char d = src.charAt(i + 1);
                if (d == '/') {
                    int j = i + 2;
                    while (j < n && src.charAt(j) != '\n') j++;
                    out.append("<span class=\"java-com\">");
                    for (int k = i; k < j; k++) appendEsc(out, src.charAt(k));
                    out.append("</span>");
                    i = j;
                    continue;
                }
                if (d == '*') {
                    int j = i + 2;
                    while (j + 1 < n && !(src.charAt(j) == '*' && src.charAt(j + 1) == '/')) j++;
                    if (j + 1 < n) j += 2;
                    out.append("<span class=\"java-com\">");
                    for (int k = i; k < j && k < n; k++) appendEsc(out, src.charAt(k));
                    out.append("</span>");
                    i = Math.min(j, n);
                    continue;
                }
            }
            if (c == '"') {
                int j = readStringEnd(src, i + 1, n, '"');
                out.append("<span class=\"java-str\">");
                for (int k = i; k < j; k++) appendEsc(out, src.charAt(k));
                out.append("</span>");
                i = j;
                continue;
            }
            if (c == '\'') {
                int j = readStringEnd(src, i + 1, n, '\'');
                out.append("<span class=\"java-str\">");
                for (int k = i; k < j; k++) appendEsc(out, src.charAt(k));
                out.append("</span>");
                i = j;
                continue;
            }
            if (c == '@') {
                int j = i + 1;
                while (j < n && (Character.isJavaIdentifierPart(src.charAt(j)) || src.charAt(j) == '.')) {
                    j++;
                }
                out.append("<span class=\"java-ann\">");
                for (int k = i; k < j; k++) appendEsc(out, src.charAt(k));
                out.append("</span>");
                i = j;
                continue;
            }
            if (isNumberStart(src, i)) {
                int j = readNumberEnd(src, i, n);
                out.append("<span class=\"java-num\">");
                for (int k = i; k < j; k++) appendEsc(out, src.charAt(k));
                out.append("</span>");
                i = j;
                continue;
            }
            if (Character.isJavaIdentifierStart(c)) {
                int j = i + 1;
                while (j < n && Character.isJavaIdentifierPart(src.charAt(j))) j++;
                String word = src.substring(i, j);
                if (KEYWORDS.contains(word)) {
                    out.append("<span class=\"java-kw\">");
                    appendEsc(out, word);
                    out.append("</span>");
                } else {
                    appendEsc(out, word);
                }
                i = j;
                continue;
            }
            appendEsc(out, c);
            i++;
        }
        return out.toString();
    }

    private static int findTextBlockEnd(String s, int start, int n) {
        int i = start;
        while (i + 2 < n) {
            if (s.charAt(i) == '"' && s.charAt(i + 1) == '"' && s.charAt(i + 2) == '"') {
                return i + 3;
            }
            i++;
        }
        return n;
    }

    /** Кінець рядка/літерала, починаючи з позиції після відкривальної лапки. */
    private static int readStringEnd(String s, int start, int n, char quote) {
        int i = start;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < n) {
                i += 2;
                continue;
            }
            if (c == quote) return i + 1;
            i++;
        }
        return n;
    }

    private static boolean isNumberStart(String s, int i) {
        char c = s.charAt(i);
        if (Character.isDigit(c)) return true;
        return c == '.' && i + 1 < s.length() && Character.isDigit(s.charAt(i + 1));
    }

    private static int readNumberEnd(String s, int i, int n) {
        int j = i;
        if (j < n && s.charAt(j) == '0' && j + 1 < n && (s.charAt(j + 1) == 'x' || s.charAt(j + 1) == 'X')) {
            j += 2;
            while (j < n && isHex(s.charAt(j))) j++;
        } else {
            while (j < n && Character.isDigit(s.charAt(j))) j++;
            if (j < n && s.charAt(j) == '.') {
                j++;
                while (j < n && Character.isDigit(s.charAt(j))) j++;
            }
            if (j < n && (s.charAt(j) == 'e' || s.charAt(j) == 'E')) {
                j++;
                if (j < n && (s.charAt(j) == '+' || s.charAt(j) == '-')) j++;
                while (j < n && Character.isDigit(s.charAt(j))) j++;
            }
        }
        while (j < n && (s.charAt(j) == 'f' || s.charAt(j) == 'F'
                || s.charAt(j) == 'd' || s.charAt(j) == 'D'
                || s.charAt(j) == 'l' || s.charAt(j) == 'L')) {
            j++;
            break;
        }
        return j;
    }

    private static boolean isHex(char c) {
        return Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static void appendEsc(StringBuilder out, char c) {
        switch (c) {
            case '<':  out.append("&lt;"); break;
            case '>':  out.append("&gt;"); break;
            case '&':  out.append("&amp;"); break;
            case '"':  out.append("&quot;"); break;
            default:   out.append(c); break;
        }
    }

    private static void appendEsc(StringBuilder out, String s) {
        for (int i = 0; i < s.length(); i++) appendEsc(out, s.charAt(i));
    }
}
