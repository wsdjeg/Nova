package net.wsdjeg.nova;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 预处理工具类
 *
 * 两项预处理确保 Markwon 正确渲染：
 * 1. 表格前自动插入空行（GFM 规范要求，ChatGPT 渲染器更宽松）
 * 2. 表格单元格长文本插入零宽空格（ZWSP），使 TextView 可在自然断点换行
 */
public final class MarkdownUtils {

    private MarkdownUtils() {}

    /** 零宽空格：不可见字符，为 TextView 提供潜在断行点 */
    private static final String ZWSP = "\u200B";

    /** 触发 ZWSP 插入的最小词长 */
    private static final int LONG_WORD_THRESHOLD = 10;

    /**
     * 预处理 Markdown 文本：
     * - 在 GFM 表格前自动插入空行
     * - 在表格单元格的长词中插入 ZWSP 以支持换行
     *
     * @param markdown 原始 Markdown 文本
     * @return 预处理后的文本
     */
    public static String preprocessMarkdown(String markdown) {
        if (markdown == null || markdown.isEmpty()) return markdown;

        String[] lines = markdown.split("\n", -1);
        List<String> result = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // 1. 确保表格前有空行
            if (i + 1 < lines.length
                    && isTableRow(line)
                    && isTableSeparator(lines[i + 1])) {
                if (!result.isEmpty()) {
                    String lastLine = result.get(result.size() - 1);
                    if (!lastLine.trim().isEmpty()) {
                        result.add("");
                    }
                }
            }

            // 2. 表格行（非分隔行）插入 ZWSP 以支持长文本换行
            if (isTableRow(line) && !isTableSeparator(line)) {
                line = wrapTableCells(line);
            }

            result.add(line);
        }

        return String.join("\n", result);
    }

    // ===== 表格空行插入 =====

    /**
     * 判断是否为 GFM 表格行（以 | 开头）。
     */
    private static boolean isTableRow(String line) {
        if (line == null) return false;
        return line.trim().startsWith("|");
    }

    /**
     * 判断是否为 GFM 表格分隔行。
     * 格式：|---|---| 或 | :--- | ---: | :---: | 等
     */
    private static boolean isTableSeparator(String line) {
        if (line == null) return false;
        String trimmed = line.trim();
        if (!trimmed.startsWith("|")) return false;

        String[] parts = trimmed.split("\\|");
        boolean hasSeparator = false;
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            if (!p.matches(":?-+:?")) return false;
            hasSeparator = true;
        }
        return hasSeparator;
    }

    // ===== 表格单元格长文本换行 =====

    /**
     * 处理表格行，在每个单元格的长词中插入 ZWSP。
     * 保留行首尾空白和 | 分隔符结构不变。
     */
    private static String wrapTableCells(String row) {
        String trimmed = row.trim();
        boolean endsWithPipe = trimmed.endsWith("|");

        String[] parts = row.split("\\|", -1);
        StringBuilder sb = new StringBuilder();

        for (int j = 0; j < parts.length; j++) {
            if (j > 0) sb.append("|");

            // 首段（行首 | 之前，通常为空）和尾段（行尾 | 之后，通常为空）不处理
            boolean isStructural = (j == 0) || (j == parts.length - 1 && endsWithPipe);
            if (isStructural) {
                sb.append(parts[j]);
            } else {
                sb.append(wrapLongWords(parts[j]));
            }
        }
        return sb.toString();
    }

    /**
     * 在文本的长词（无空格序列）中插入 ZWSP。
     * 短词（< 10 字符）不做处理。
     */
    private static String wrapLongWords(String text) {
        if (text == null || text.isEmpty()) return text;

        StringBuilder result = new StringBuilder();
        StringBuilder word = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                if (word.length() > 0) {
                    result.append(insertZwsp(word.toString()));
                    word.setLength(0);
                }
                result.append(c);
            } else {
                word.append(c);
            }
        }
        if (word.length() > 0) {
            result.append(insertZwsp(word.toString()));
        }
        return result.toString();
    }

    /**
     * 在长词中的自然断点字符（/ . _ -）后插入 ZWSP。
     * 这些字符常见于文件路径、包名、标识符等不可断开文本。
     */
    private static String insertZwsp(String word) {
        if (word.length() < LONG_WORD_THRESHOLD) return word;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            sb.append(word.charAt(i));
            if (i < word.length() - 1) {
                char c = word.charAt(i);
                if (c == '/' || c == '.' || c == '_' || c == '-') {
                    sb.append(ZWSP);
                }
            }
        }
        return sb.toString();
    }
}

