package org.debiannoroot;
import android.content.Context;
import android.content.SharedPreferences;

public final class SessionSettings {
    public final SharedPreferences preferences;
    public SessionSettings(Context context) { preferences=context.getSharedPreferences("display",Context.MODE_PRIVATE); }
    public int width() { return number("width",1280,320,7680); }
    public int height() { return number("height",800,240,4320); }
    public int number(String key,int fallback,int min,int max) { return Math.max(min,Math.min(max,preferences.getInt(key,fallback))); }
    public String value(String key,String fallback) { return preferences.getString(key,fallback); }
    public boolean flag(String key,boolean fallback) { return preferences.getBoolean(key,fallback); }
    public String scaling() { return value("scaling","fit"); }
    public float scale() { return number("scale",100,25,400)/100f; }
}
