package org.debiannoroot.ui;

import java.util.*;

/** A key stays down until every input source holding it has released it. */
final class HeldKeys {
    interface Output { void key(long key, boolean down); }
    private final Output output;
    private final Map<Object,Set<Long>> owners=new HashMap<>();
    private final Map<Long,Integer> counts=new HashMap<>();
    HeldKeys(Output output){this.output=output;}
    static long key(int scan,int code){return ((long)scan<<32)|(code&0xffffffffL);}
    void set(Object owner,long... keys){
        Set<Long> next=new HashSet<>();for(long key:keys)next.add(key);
        Set<Long> old=owners.getOrDefault(owner,Collections.emptySet());
        // Press before releasing when a finger slides between directions.
        for(long key:next)if(!old.contains(key)){
            int count=counts.getOrDefault(key,0);counts.put(key,count+1);
            if(count==0)output.key(key,true);
        }
        for(long key:old)if(!next.contains(key)){
            int count=counts.get(key)-1;
            if(count==0){counts.remove(key);output.key(key,false);}else counts.put(key,count);
        }
        if(next.isEmpty())owners.remove(owner);else owners.put(owner,next);
    }
    void clear(){
        for(long key:new ArrayList<>(counts.keySet()))output.key(key,false);
        owners.clear();counts.clear();
    }
}
