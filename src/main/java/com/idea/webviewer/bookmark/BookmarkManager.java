package com.idea.webviewer.bookmark;

import com.idea.webviewer.storage.LegacyJsonReader;
import com.idea.webviewer.storage.RecordCodec;
import com.intellij.ide.util.PropertiesComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * 书签存储。
 *
 * <p>与历史一致，使用应用级 {@link PropertiesComponent} + {@link RecordCodec}，
 * 不依赖未声明引入的 Gson；同时限制总量并做 URL 去重。</p>
 */
public final class BookmarkManager {

    private static final String KEY = "WebViewer.Bookmarks";
    private static final int MAX_BOOKMARKS = 300;

    private BookmarkManager() {
    }

    public static List<Bookmark> loadBookmarks() {
        String raw = PropertiesComponent.getInstance().getValue(KEY, "");
        if (raw.isEmpty()) {
            return new ArrayList<>();
        }
        if (LegacyJsonReader.looksLikeLegacyJson(raw)) {
            List<Bookmark> migrated = new ArrayList<>();
            for (String[] fields : LegacyJsonReader.parse(raw)) {
                if (!fields[1].isEmpty()) {
                    migrated.add(new Bookmark(fields[0], fields[1]));
                }
            }
            saveBookmarks(migrated);
            return migrated;
        }
        List<Bookmark> bookmarks = new ArrayList<>();
        for (List<String> fields : RecordCodec.decodeRecords(raw)) {
            if (fields.size() < 2 || fields.get(1).isEmpty()) {
                continue;
            }
            bookmarks.add(new Bookmark(fields.get(0), fields.get(1)));
        }
        return bookmarks;
    }

    public static void saveBookmarks(List<Bookmark> bookmarks) {
        if (bookmarks == null || bookmarks.isEmpty()) {
            PropertiesComponent.getInstance().setValue(KEY, "");
            return;
        }
        List<String> lines = new ArrayList<>(bookmarks.size());
        for (Bookmark bookmark : bookmarks) {
            lines.add(RecordCodec.encodeRecord(bookmark.title, bookmark.url));
        }
        PropertiesComponent.getInstance().setValue(KEY, RecordCodec.encodeRecords(lines));
    }

    /** 添加书签；URL 已存在时直接忽略，避免重复。 */
    public static void addBookmark(Bookmark bookmark) {
        if (bookmark == null || bookmark.url == null || bookmark.url.isEmpty()) {
            return;
        }
        List<Bookmark> bookmarks = loadBookmarks();
        for (Bookmark existing : bookmarks) {
            if (bookmark.url.equals(existing.url)) {
                return;
            }
        }
        bookmarks.add(bookmark);
        while (bookmarks.size() > MAX_BOOKMARKS) {
            bookmarks.remove(0);
        }
        saveBookmarks(bookmarks);
    }

    public static void removeBookmark(int index) {
        List<Bookmark> bookmarks = loadBookmarks();
        if (index >= 0 && index < bookmarks.size()) {
            bookmarks.remove(index);
            saveBookmarks(bookmarks);
        }
    }

    public static class Bookmark {
        public String title;
        public String url;

        public Bookmark() {
        }

        public Bookmark(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }
}
