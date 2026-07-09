package com.idea.webviewer.bookmark;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.ide.util.PropertiesComponent;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class BookmarkManager {

    private static final String KEY = "WebViewer.Bookmarks";
    private static final Gson gson = new Gson();
    private static final Type listType = new TypeToken<List<Bookmark>>() {}.getType();

    public static List<Bookmark> loadBookmarks() {
        String json = PropertiesComponent.getInstance().getValue(KEY, "[]");
        try {
            List<Bookmark> list = gson.fromJson(json, listType);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void saveBookmarks(List<Bookmark> bookmarks) {
        String json = gson.toJson(bookmarks);
        PropertiesComponent.getInstance().setValue(KEY, json);
    }

    public static void addBookmark(Bookmark bookmark) {
        List<Bookmark> list = loadBookmarks();
        list.add(bookmark);
        saveBookmarks(list);
    }

    public static void removeBookmark(int index) {
        List<Bookmark> list = loadBookmarks();
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            saveBookmarks(list);
        }
    }

    public static class Bookmark {
        public String title;
        public String url;

        public Bookmark() {}

        public Bookmark(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }
}