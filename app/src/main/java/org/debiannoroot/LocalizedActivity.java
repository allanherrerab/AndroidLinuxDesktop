package org.debiannoroot;

import android.app.Activity;
import android.content.Context;

/** Apply this app's language without changing the phone's locale. */
public class LocalizedActivity extends Activity {
    private String language;
    @Override protected void attachBaseContext(Context base){language=new RegionalSettings(base).appLanguage();super.attachBaseContext(RegionalSettings.localized(base));}
    @Override protected void onResume(){super.onResume();if(!new RegionalSettings(this).appLanguage().equals(language))recreate();}
    protected String t(String text){return UiText.get(this,text);}
    protected String tf(String source,Object... args){return String.format(getResources().getConfiguration().getLocales().get(0),t(source),args);}
    protected String[] t(String[] labels){String[] result=labels.clone();for(int i=0;i<result.length;i++)result[i]=t(result[i]);return result;}
}
