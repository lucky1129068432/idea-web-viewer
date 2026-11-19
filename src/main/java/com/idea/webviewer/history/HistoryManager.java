package com.idea.webviewer.history;

import com.idea.webviewer.storage.LegacyJsonReader;
import com.idea.webviewer.storage.RecordCodec;
import com.intellij.ide.util.PropertiesComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 浏览历史存储。
 *
 * <p>历史属于「用户级」而非「项目级」数据，因此仍使用应用级 {@link PropertiesComponent}，
 * 但序列化改为 {@link RecordCodec}，不再依赖未声明引入的 Gson。</p>
 */
public final class HistoryManager {

    private static final String KEY = "WebViewer.History";
    private static final int MAX_HISTORY = 200;

    private HistoryManager() {
    }

    public static List<HistoryRecord> loadHistory() {
        String raw = PropertiesComponent.getInstance().getValue(KEY, "");
        if (raw.isEmpty()) {
            return new ArrayList<>();
        }
        if (LegacyJsonReader.looksLikeLegacyJson(raw)) {
            List<HistoryRecord> migrated = migrateLegacy(raw);
            saveHistory(migrated);
            return migrated;
        }
        List<HistoryRecord> records = new ArrayList<>();
        for (List<String> fields : RecordCodec.decodeRecords(raw)) {
            if (fields.size() < 2) {
                continue;
            }
            records.add(new HistoryRecord(fields.get(0), fields.get(1), parseTimestamp(fields.size() > 2 ? fields.get(2) : "0")));
        }
        return records;
    }

    public static void saveHistory(List<HistoryRecord> records) {
        if (records == null || records.isEmpty()) {
            PropertiesComponent.getInstance().setValue(KEY, "");
            return;
        }
        List<String> lines = new ArrayList<>(records.size());
        for (HistoryRecord record : records) {
            lines.add(RecordCodec.encodeRecord(record.title, record.url, Long.toString(record.timestamp)));
        }
        PropertiesComponent.getInstance().setValue(KEY, RecordCodec.encodeRecords(lines));
    }

    public static void addHistory(String title, String url) {
        if (url == null || url.isEmpty() || "about:blank".equals(url)) {
            return;
        }
        List<HistoryRecord> records = loadHistory();
        // 避免连续重复（SPA 站点的 title 会反复变化，但不应刷屏）
        if (!records.isEmpty() && url.equals(records.get(records.size() - 1).url)) {
            return;
        }
        records.add(new HistoryRecord(title, url, System.currentTimeMillis()));
        while (records.size() > MAX_HISTORY) {
            records.remove(0);
        }
        saveHistory(records);
    }

    public static void removeHistory(int index) {
        List<HistoryRecord> records = loadHistory();
        if (index >= 0 && index < records.size()) {
            records.remove(index);
            saveHistory(records);
        }
    }

    public static void clearHistory() {
        saveHistory(new ArrayList<>());
    }

    private static List<HistoryRecord> migrateLegacy(String raw) {
        List<HistoryRecord> migrated = new ArrayList<>();
        for (String[] fields : LegacyJsonReader.parse(raw)) {
            migrated.add(new HistoryRecord(fields[0], fields[1], parseTimestamp(fields[2])));
        }
        return migrated;
    }

    private static long parseTimestamp(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static class HistoryRecord {
        public String title;
        public String url;
        public long timestamp;

        public HistoryRecord() {
        }

        public HistoryRecord(String title, String url, long timestamp) {
            this.title = title;
            this.url = url;
            this.timestamp = timestamp;
        }
    }
}
