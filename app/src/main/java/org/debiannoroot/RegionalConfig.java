package org.debiannoroot;

/** Validated guest regional values, independent of Android's own settings. */
public final class RegionalConfig {
    public static final String[] LANGUAGES={"en","es","pt","fr","de","ja","pl"};
    public static final String[] LANGUAGE_NAMES={"English","Español","Português","Français","Deutsch","日本語","Polski"};
    public static final String[] LOCALES={"en_US.UTF-8","es_ES.UTF-8","pt_BR.UTF-8","fr_FR.UTF-8","de_DE.UTF-8","ja_JP.UTF-8","pl_PL.UTF-8"};
    public static final String[] KEYBOARDS={"us","us:intl","gb","latam","es","pt","br","fr","de","jp","pl","ca"};
    public static final String[] KEYBOARD_NAMES={"English (US)","English (US, international)","English (UK)","Español (Latinoamérica)","Español (España)","Português (Portugal)","Português (Brasil)","Français","Deutsch","日本語","Polski","Français (Canada)"};
    public static String language(String value){for(String code:LANGUAGES)if(code.equals(value))return value;return "en";}
    public static String locale(String code){for(int i=0;i<LANGUAGES.length;i++)if(LANGUAGES[i].equals(code))return LOCALES[i];return LOCALES[0];}
    public static String keyboard(String value){for(String code:KEYBOARDS)if(code.equals(value))return value;return "us";}
    public static String timezone(String value){
        if(value==null||value.contains("..")||value.startsWith("/")||!value.matches("[A-Za-z0-9_+./-]+"))return "UTC";
        for(String zone:java.util.TimeZone.getAvailableIDs())if(zone.equals(value))return value;
        return "UTC";
    }
    public static String guestSettings(String language,String keyboard){
        String code=language(language),layout=keyboard(keyboard);
        String[] parts=layout.split(":",2);
        return "NOROOT_LANG='"+locale(code)+"'\nNOROOT_LANGUAGE='"+code+"'\nNOROOT_KEYBOARD_LAYOUT='"+parts[0]+"'\nNOROOT_KEYBOARD_VARIANT='"+(parts.length>1?parts[1]:"")+"'\n";
    }
}
