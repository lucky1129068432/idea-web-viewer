package com.idea.webviewer.history;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.ide.util.PropertiesComponent;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class HistoryManager {

    private static final String KEY = "WebViewer.History";
    private static final Gson gson = new Gson();
    private static final Type listType = new TypeToken<List<HistoryRecord>>() {}.getType();
    private static final int MAX_HISTORY = 200;

    public static List<HistoryRecord> loadHistory() {
        String json = PropertiesComponent.getInstance().getValue(KEY, "[]");
        try {
            List<HistoryRecord> list = gson.fromJson(json, listType);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void saveHistory(List<HistoryRecord> records) {
        String json = gson.toJson(records);
        PropertiesComponent.getInstance().setValue(KEY, json);
    }

    public static void addHistory(String title, String url) {
        List<HistoryRecord> list = loadHistory();
        // 避免连续重复
        if (!list.isEmpty() && list.get(list.size() - 1).url.equals(url)) {
            return;
        }
        list.add(new HistoryRecord(title, url, System.currentTimeMillis()));
        // 超过上限删除最早的
        while (list.size() > MAX_HISTORY) {
            list.remove(0);
        }
        saveHistory(list);
    }

    public static void removeHistory(int index) {
        List<HistoryRecord> list = loadHistory();
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            saveHistory(list);
        }
    }

    public static void clearHistory() {
        saveHistory(new ArrayList<>());
    }

    public static class HistoryRecord {
        public String title;
        public String url;
        public long timestamp;

        public HistoryRecord() {}

        public HistoryRecord(String title, String url, long timestamp) {
            this.title = title;
            this.url = url;
            this.timestamp = timestamp;
        }
    }
}
