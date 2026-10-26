package org.debiannoroot.ui;

import org.debiannoroot.settings.RegionalConfig;
import org.debiannoroot.settings.RegionalSettings;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.*;
import java.util.*;

/** First-install choices and later regional changes share the same screen. */
public final class SetupActivity extends LocalizedActivity {
    private RegionalSettings settings;
    private LinearLayout page;
    @Override public void onCreate(Bundle state){super.onCreate(state);settings=new RegionalSettings(this);render();}
    private void button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setOnClickListener(v->action.run());page.addView(b);}
    private void language(boolean app){
        String value=app?settings.appLanguage():settings.guestLanguage();
        int current=Arrays.asList(RegionalConfig.LANGUAGES).indexOf(value);
        new AlertDialog.Builder(this).setTitle(t(app?"App language":"Debian language"))
                .setSingleChoiceItems(RegionalConfig.LANGUAGE_NAMES,current,(dialog,index)->{
                    android.content.SharedPreferences.Editor edit=settings.preferences.edit();
                    if(app){edit.putString("appLanguage",RegionalConfig.LANGUAGES[index]);}
                    else edit.putString("guestLanguage",RegionalConfig.LANGUAGES[index]);
                    edit.commit();dialog.dismiss();if(app)recreate();else render();
                }).setNegativeButton(t("Cancel"),null).show();
    }
    private String name(String code){return RegionalConfig.LANGUAGE_NAMES[Arrays.asList(RegionalConfig.LANGUAGES).indexOf(code)];}
    private void timezone(){
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint(t("Search timezone"));body.addView(search);
        ArrayList<String> zones=new ArrayList<>(Arrays.asList(java.util.TimeZone.getAvailableIDs()));Collections.sort(zones);
        ListView list=new ListView(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,zones);list.setAdapter(adapter);body.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(t("Timezone")).setView(body).setNegativeButton(t("Cancel"),null).create();
        list.setOnItemClickListener((parent,view,index,id)->{settings.preferences.edit().putString("timezone",adapter.getItem(index)).apply();dialog.dismiss();render();});
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){adapter.getFilter().filter(s);}public void afterTextChanged(android.text.Editable e){}});
        dialog.show();dialog.getWindow().setLayout(-1,Math.round(getResources().getDisplayMetrics().heightPixels*.8f));
    }
    private void render(){
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(20*getResources().getDisplayMetrics().density);page.setPadding(pad,pad,pad,pad);
        page.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(pad+insets.getSystemWindowInsetLeft(),pad+insets.getSystemWindowInsetTop(),pad+insets.getSystemWindowInsetRight(),pad+insets.getSystemWindowInsetBottom());return insets;});
        TextView title=new TextView(this);title.setText(t("Language, keyboard & timezone"));title.setTextSize(24);page.addView(title);
        TextView note=new TextView(this);note.setText(t("Choose settings for this app and Debian. Your phone settings are unchanged. Debian changes apply on the next session start."));note.setPadding(0,pad,0,pad);page.addView(note);
        button(t("App language")+": "+name(settings.appLanguage()),()->language(true));
        button(t("Debian language")+": "+name(settings.guestLanguage()),()->language(false));
        button(t("Guest keyboard layout")+": "+RegionalConfig.KEYBOARD_NAMES[Arrays.asList(RegionalConfig.KEYBOARDS).indexOf(settings.keyboard())],()->new AlertDialog.Builder(this).setTitle(t("Guest keyboard layout")).setSingleChoiceItems(RegionalConfig.KEYBOARD_NAMES,Arrays.asList(RegionalConfig.KEYBOARDS).indexOf(settings.keyboard()),(dialog,index)->{settings.preferences.edit().putString("keyboard",RegionalConfig.KEYBOARDS[index]).apply();dialog.dismiss();render();}).setNegativeButton(t("Cancel"),null).show());
        button(t("Timezone")+": "+settings.timezone(),this::timezone);
        TextView keyboard=new TextView(this);keyboard.setText(t("The layout controls the guest keyboard. Android’s on-screen keyboard is selected separately; its Unicode input also supports Japanese."));keyboard.setPadding(0,pad,0,pad);page.addView(keyboard);
        button(t(settings.complete()?"Save":"Save and continue"),()->{settings.preferences.edit().putString("guestLanguage",settings.guestLanguage()).putBoolean("setupComplete",true).commit();finish();});
        ScrollView scroll=new ScrollView(this);scroll.addView(page);setContentView(scroll);
    }
}
