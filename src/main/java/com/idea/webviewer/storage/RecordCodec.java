package com.idea.webviewer.storage;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 极简记录编解码工具，用于替代原先「未声明就直接使用」的 Gson 依赖。
 *
 * <p>格式：每条记录由若干字段组成，字段先按 UTF-8 做 Base64 再以 {@code |} 连接，
 * 记录之间用换行分隔。Base64 的输出中不会出现 {@code |} 和换行，
 * 因此无需再做转义，对 URL 里的 {@code ? & = #} 等字符也是安全的。</p>
 */
public final class RecordCodec {

    private static final String FIELD_SEPARATOR = "|";
    private static final String RECORD_SEPARATOR = "\n";
    private static final Pattern FIELD_PATTERN = Pattern.compile(Pattern.quote(FIELD_SEPARATOR));

    private RecordCodec() {
    }

    /** 编码单个字段，null 视为空串。 */
    public static String encodeField(String value) {
        String safe = value == null ? "" : value;
        return Base64.getEncoder().encodeToString(safe.getBytes(StandardCharsets.UTF_8));
    }

    /** 解码单个字段；非法内容返回空串而不抛异常。 */
    public static String decodeField(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /** 把一条记录的多个字段编码成一行。 */
    public static String encodeRecord(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                sb.append(FIELD_SEPARATOR);
            }
            sb.append(encodeField(fields[i]));
        }
        return sb.toString();
    }

    /** 把一行解码回字段列表；字段数量不足由调用方判断。 */
    public static List<String> decodeRecord(String record) {
        List<String> fields = new ArrayList<>();
        if (record == null || record.isEmpty()) {
            return fields;
        }
        for (String part : FIELD_PATTERN.split(record, -1)) {
            fields.add(decodeField(part));
        }
        return fields;
    }

    /** 把多行记录拼成最终存储串。 */
    public static String encodeRecords(List<String> records) {
        if (records == null || records.isEmpty()) {
            return "";
        }
        return String.join(RECORD_SEPARATOR, records);
    }

    /** 解析存储串，空行会被忽略。 */
    public static List<List<String>> decodeRecords(String raw) {
        List<List<String>> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        for (String line : raw.split(RECORD_SEPARATOR, -1)) {
            if (line.isEmpty()) {
                continue;
            }
            result.add(decodeRecord(line));
        }
        return result;
    }
}
