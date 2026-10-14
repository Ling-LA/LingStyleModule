package io.github.ling.style.core;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Policy {
    private Policy(){}
    public static Set<String> blacklist(String text){
        Set<String> out=new HashSet<>();
        if(text!=null)for(String id:text.split("[\\s,，;；]+"))if(!id.trim().isEmpty())out.add(id.trim());
        return Collections.unmodifiableSet(out);
    }
    public static boolean blocked(Set<String> ids,String... identities){
        for(String id:identities)if(id!=null&&!id.isEmpty()&&ids.contains(id))return true;
        return false;
    }
    public static boolean bypass(String text){return text!=null&&text.startsWith("/");}
    /** Never share a group's memory across senders; unknown identity fails closed. */
    public static String replyScope(String account,int type,String peer,String senderUid,String senderUin){
        String sender=senderUin!=null&&senderUin.matches("[1-9][0-9]+")?"uin="+senderUin:
            senderUid!=null&&senderUid.matches("u_[A-Za-z0-9_-]+")?"uid="+senderUid:null;
        if(sender==null||account==null||account.isEmpty()||peer==null||peer.isEmpty())return null;
        return account+":"+type+":"+peer+":"+sender;
    }
    public static String unprefix(String text){return bypass(text)&&text.length()>1?text.substring(1):text;}
    public static boolean fresh(long eventSeconds,long nowMs){
        long age=nowMs-eventSeconds*1000L;
        return eventSeconds>0 && age>=-30000 && age<=180000;
    }
    /** Fixed-capacity, expiring dedupe. Zero IDs cannot be trusted for auto-send. */
    public static final class Seen {
        private final LinkedHashMap<String,Long> events=new LinkedHashMap<>();
        public synchronized boolean first(String account,String peer,long id,long now){
            if(id==0)return false;
            Iterator<Map.Entry<String,Long>> it=events.entrySet().iterator();
            while(it.hasNext())if(now-it.next().getValue()>300000)it.remove();
            String key=account+":"+peer+":"+id;
            if(events.containsKey(key))return false;
            events.put(key,now);
            while(events.size()>2048)events.remove(events.keySet().iterator().next());
            return true;
        }
    }
    public static final class Once {
        private final AtomicBoolean done=new AtomicBoolean();
        public boolean claim(){return done.compareAndSet(false,true);}
    }
}
