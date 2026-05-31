package com.galex.intercom;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;

public class WebAppInterface {
    private final Context context;

    public WebAppInterface(Context context) { this.context = context; }

    @JavascriptInterface
    public String getServerUrl() {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        return prefs.getString(MainActivity.KEY_SERVER, MainActivity.DEFAULT_SERVER);
    }

    @JavascriptInterface
    public void setServerUrl(String url) {
        SharedPreferences.Editor editor = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit();
        editor.putString(MainActivity.KEY_SERVER, url);
        editor.apply();
    }
}
