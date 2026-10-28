package org.debiannoroot.ui;

import java.util.*;

public final class HeldKeysTest {
    public static void main(String[] args){
        List<String> events=new ArrayList<>();HeldKeys held=new HeldKeys((key,down)->events.add(key+":"+down));
        long up=HeldKeys.key(0,19),left=HeldKeys.key(0,21),space=HeldKeys.key(0,62);
        held.set("pad",up);held.set("pad",up); // A stationary hold must never become repeated taps.
        expect(events,"19:true");
        held.set("toolbar",up);held.set("pad",up,left);held.set("action",space);
        expect(events,"21:true","62:true");
        held.set("pad",left);held.set("action");
        expect(events,"62:false"); // The other finger still owns Up.
        held.set("toolbar");expect(events,"19:false");
        held.clear();expect(events,"21:false");held.clear();expect(events);
        held.set("pad",up,left);events.clear();held.set("pad",up);expect(events,"21:false");
        held.set("pad");expect(events,"19:false"); // Cancel, hiding and detach all use an empty set.
        held.set("new session",up);expect(events,"19:true");held.clear();expect(events,"19:false");
        long physical=HeldKeys.key(103,19);held.set("physical",physical);held.clear();
        expect(events,physical+":true",physical+":false");
        System.out.println("Held keys: sustained, shared, diagonal, simultaneous action, cancellation and reconnect passed");
    }
    private static void expect(List<String> events,String... expected){
        if(!events.equals(Arrays.asList(expected)))throw new AssertionError("Expected "+Arrays.toString(expected)+", got "+events);
        events.clear();
    }
}
