package org.debiannoroot;

import org.debiannoroot.settings.RegionalConfig;
public final class RegionalConfigTest {
    private static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        check(RegionalConfig.language("ja").equals("ja"));check(RegionalConfig.language("fr").equals("fr"));
        check(RegionalConfig.language("bad").equals("en"));
        for(String language:RegionalConfig.LANGUAGES){
            String config=RegionalConfig.guestSettings(language,"us:intl");
            check(config.contains("NOROOT_LANG='"+RegionalConfig.locale(language)+"'"));
            check(config.contains("NOROOT_KEYBOARD_LAYOUT='us'"));check(config.contains("NOROOT_KEYBOARD_VARIANT='intl'"));
        }
        check(RegionalConfig.timezone("America/El_Salvador").equals("America/El_Salvador"));
        check(RegionalConfig.timezone("Asia/Tokyo").equals("Asia/Tokyo"));
        check(RegionalConfig.timezone("../../etc/passwd").equals("UTC"));
        check(RegionalConfig.timezone("/etc/passwd").equals("UTC"));
        check(RegionalConfig.timezone("Europe/Warsaw'\nBAD=1").equals("UTC"));
        check(RegionalConfig.guestSettings("ja'\nBAD=1","us'\nBAD=1").equals(RegionalConfig.guestSettings("en","us")));
        System.out.println("Regional locale/layout validation and safe guest configuration tests passed");
    }
}
