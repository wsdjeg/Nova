package net.wsdjeg.nova;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 预处理工具类
 *
 * Markwon 遵循严格的 GFM 规范，要求表格 header 前必须有空行。
 * ChatGPT 等渲染器更宽松，能自动处理无空行的表格。
 * 本工具在渲染前对 markdown 文本做预处理，使 Nova 也能正确渲染无空行表格。
 */
public final class MarkdownUtils {

    private MarkdownUtils() {}

    /**
     * 预处理 Markdown 文本，确保 GFM 表格前有空行。
     *
     * 扫描每一行，当检测到"表头 + 分隔行"模式时，
     * 如果前一行非空，则自动插入一个空行。
     *
     * @param markdown 原始 Markdown 文本
     * @return 预处理后的文本
     */
    public static String preprocessMarkdown(String markdown) {
        if (markdown == null || markdown.isEmpty()) return markdown;

        String[] lines = markdown.split("\n", -1);
        List<String> result = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            // 检测当前行是否为表头，且下一行是分隔行
            if (i + 1 < lines.length
                    && isTableHeader(lines[i])
                    && isTableSeparator(lines[i + 1])) {
                // 如果结果列表最后一行非空，插入空行
                if (!result.isEmpty()) {
                    String lastLine = result.get(result.size() - 1);
                    if (!lastLine.trim().isEmpty()) {
                        result.add("");
                    }
                }
            }
            result.add(lines[i]);
        }

        return String.join("\n", result);
    }

    /**
     * 判断是否为 GFM 表头行（以 | 开头的行）。
     */
    private static boolean isTableHeader(String line) {
        if (line == null) return false;
        String trimmed = line.trim();
        return trimmed.startsWith("|");
    }

    /**
     * 判断是否为 GFM 表格分隔行。
     *
     * 分隔行格式：|---|---| 或 | :--- | ---: | :---: | 等
     * 每个非空段必须匹配 :?-+:? 模式。
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
}

