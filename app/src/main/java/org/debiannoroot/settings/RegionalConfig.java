package org.debiannoroot.settings;

import org.debiannoroot.runtime.NativeCore;

/** Validated guest regional values, independent of Android's own settings. */
public final class RegionalConfig {
    public static final String[] LANGUAGES=NativeCore.regionalCodes(0);
    public static final String[] LANGUAGE_NAMES={"English","Español","Português","Français","Deutsch","日本語","Polski"};
    public static final String[] LOCALES=NativeCore.regionalCodes(1);
    public static final String[] KEYBOARDS=NativeCore.regionalCodes(2);
    public static final String[] KEYBOARD_NAMES={"English (US)","English (US, international)","English (UK)","Español (Latinoamérica)","Español (España)","Português (Portugal)","Português (Brasil)","Français","Deutsch","日本語","Polski","Français (Canada)"};
    public static String language(String value){return NativeCore.language(value);}
    public static String locale(String code){return NativeCore.locale(code);}
    public static String keyboard(String value){return NativeCore.keyboard(value);}
    public static String timezone(String value){
        if(!NativeCore.timezoneSafe(value))return "UTC";
        for(String zone:java.util.TimeZone.getAvailableIDs())if(zone.equals(value))return value;
        return "UTC";
    }
    public static String guestSettings(String language,String keyboard){
        return NativeCore.guestSettings(language,keyboard);
    }
}
