package com.zycu.guishop.admin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zycu.guishop.ShopConfig;

/** A detached editor draft. Mutating this never changes a live shop. */
public final class AdminDocument {
    public static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    public ShopConfig config;
    public AdminDocument copy() { return JSON.fromJson(JSON.toJson(this), AdminDocument.class); }
}
