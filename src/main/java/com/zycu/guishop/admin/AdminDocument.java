package com.zycu.guishop.admin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zycu.guishop.ShopConfig;
import com.zycu.guishop.ShopFolderStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A detached editor draft. Mutating this never changes a live shop. */
public final class AdminDocument {
    public static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    public ShopConfig config;
    public Map<String, FolderLayout> folders = new LinkedHashMap<>();

    public static final class FolderLayout {
        public List<ShopFolderStore.Folder> folders = new ArrayList<>();
        public Map<String, String> assignments = new LinkedHashMap<>();
    }

    public AdminDocument copy() { return JSON.fromJson(JSON.toJson(this), AdminDocument.class); }
}
