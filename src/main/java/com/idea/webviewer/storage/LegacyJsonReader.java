package com.idea.webviewer.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 读取 1.0.1 及更早版本用 Gson 写入的 JSON 记录，供升级时做一次性迁移。
 *
 * <p>只解析本项目自己生成的固定结构（对象数组 + title/url/timestamp 三个字段），
 * 不追求完整的 JSON 兼容性，避免为此重新引入第三方依赖。</p>
 */
public final class LegacyJsonReader {

    private static final Pattern OBJECT = Pattern.compile("\\{([^{}]*)}");
    private static final Pattern TITLE = Pattern.compile("\"title\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern URL = Pattern.compile("\"url\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern TIMESTAMP = Pattern.compile("\"timestamp\"\\s*:\\s*(\\d+)");

    private LegacyJsonReader() {
    }

    /** 旧版本把数据存成 JSON 数组，据此判断是否需要迁移。 */
    public static boolean looksLikeLegacyJson(String raw) {
        return raw != null && raw.trim().startsWith("[");
    }

    /**
     * 解析为若干条记录，每条记录固定为 {@code {title, url, timestamp}} 三个元素，
     * 缺失的 timestamp 用 "0" 表示。
     */
    public static List<String[]> parse(String raw) {
        List<String[]> records = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return records;
        }
        Matcher objectMatcher = OBJECT.matcher(raw);
        while (objectMatcher.find()) {
            String body = objectMatcher.group(1);
            String title = extractString(TITLE, body);
            String url = extractString(URL, body);
            if (title.isEmpty() && url.isEmpty()) {
                continue;
            }
            records.add(new String[]{title, url, extractNumber(TIMESTAMP, body)});
        }
        return records;
    }

    private static String extractString(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? unescape(matcher.group(1)) : "";
    }

    private static String extractNumber(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : "0";
    }

    /** 还原 Gson 写入的转义序列：引号、反斜杠、斜杠、控制字符以及 U+XXXX 形式的码点。 */
    private static String unescape(String source) {
        StringBuilder sb = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c != '\\' || i + 1 >= source.length()) {
                sb.append(c);
                continue;
            }
            char escaped = source.charAt(++i);
            switch (escaped) {
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> i = appendUnicodeEscape(sb, source, i, escaped);
                default -> sb.append(escaped);
            }
        }
        return sb.toString();
    }

    /** 处理 U+XXXX 形式的转义，返回消耗到的下标；数据不完整时原样退化为字面量。 */
    private static int appendUnicodeEscape(StringBuilder sb, String source, int index, char fallback) {
        if (index + 4 >= source.length()) {
            sb.append(fallback);
            return index;
        }
        try {
            sb.append((char) Integer.parseInt(source.substring(index + 1, index + 5), 16));
            return index + 4;
        } catch (NumberFormatException e) {
            sb.append(fallback);
            return index;
        }
    }
}
