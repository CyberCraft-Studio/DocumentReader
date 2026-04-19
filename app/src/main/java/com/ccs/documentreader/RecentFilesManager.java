package com.ccs.documentreader;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Зберігає історію останніх відкритих документів у SharedPreferences.
 * Тримає до MAX_ITEMS записів, найновіший — першим.
 */
public final class RecentFilesManager {

    public static final int MAX_ITEMS = 25;
    private static final String PREFS = "recent_files_prefs";
    private static final String KEY = "items_v1";

    public static final class RecentFile {
        public String uri;
        public String name;
        public long openedAt;

        public RecentFile() {
        }

        public RecentFile(String uri, String name, long openedAt) {
            this.uri = uri;
            this.name = name;
            this.openedAt = openedAt;
        }
    }

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public RecentFilesManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<RecentFile> getAll() {
        String json = prefs.getString(KEY, null);
        if (json == null) return new ArrayList<>();
        try {
            Type type = new TypeToken<List<RecentFile>>() {}.getType();
            List<RecentFile> list = gson.fromJson(json, type);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void add(Uri uri, String name) {
        if (uri == null || name == null) return;
        List<RecentFile> list = getAll();
        String uriStr = uri.toString();
        Iterator<RecentFile> it = list.iterator();
        while (it.hasNext()) {
            RecentFile rf = it.next();
            if (rf != null && uriStr.equals(rf.uri)) {
                it.remove();
            }
        }
        list.add(0, new RecentFile(uriStr, name, System.currentTimeMillis()));
        while (list.size() > MAX_ITEMS) {
            list.remove(list.size() - 1);
        }
        prefs.edit().putString(KEY, gson.toJson(list)).apply();
    }

    public void remove(String uriStr) {
        List<RecentFile> list = getAll();
        Iterator<RecentFile> it = list.iterator();
        while (it.hasNext()) {
            RecentFile rf = it.next();
            if (rf != null && uriStr.equals(rf.uri)) {
                it.remove();
            }
        }
        prefs.edit().putString(KEY, gson.toJson(list)).apply();
    }

    public void clear() {
        prefs.edit().remove(KEY).apply();
    }
}
