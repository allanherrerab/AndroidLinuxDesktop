package org.debiannoroot.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import java.util.Locale;

public final class RegionalSettings {
    public final SharedPreferences preferences;
    public RegionalSettings(Context context){preferences=context.getSharedPreferences("regional",Context.MODE_PRIVATE);}
    public String appLanguage(){return RegionalConfig.language(preferences.getString("appLanguage","en"));}
    public String guestLanguage(){return RegionalConfig.language(preferences.getString("guestLanguage",appLanguage()));}
    public String keyboard(){return RegionalConfig.keyboard(preferences.getString("keyboard","us"));}
    public String timezone(){return RegionalConfig.timezone(preferences.getString("timezone","UTC"));}
    public boolean complete(){return preferences.getBoolean("setupComplete",false);}
    public static Context localized(Context base){
        Configuration config=new Configuration(base.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(new RegionalSettings(base).appLanguage()));
        return base.createConfigurationContext(config);
    }
}
