package io.github.ling.style.store;

import android.content.Context;
import android.content.SharedPreferences;
import io.github.ling.style.core.*;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** QQ-private, per-account preferences. No exported IPC or world-readable settings. */
public final class Settings {
    private final Context context;
    private volatile String account="preview";
    private volatile SharedPreferences prefs;
    public Runnable onAutoChanged;
    public final AtomicLong revision=new AtomicLong();
    public Settings(Context context){this.context=context;selectAccount("preview");}
    public synchronized void selectAccount(String id){
        if(id==null||!id.matches("[1-9][0-9]{4,12}|preview"))id="unknown";
        if(prefs!=null&&id.equals(account))return;
        account=id;prefs=context.getSharedPreferences("ling_style_v1_"+id,Context.MODE_PRIVATE);revision.incrementAndGet();
    }
    public String account(){return account;}
    public boolean known(){return !account.equals("unknown")&&!account.equals("preview");}
    public Style style(){return Style.from(prefs.getString("style","CAT"));}
    public boolean enabled(){return prefs.getBoolean("enabled",false);}
    public boolean auto(){return prefs.getBoolean("auto",false);}
    public boolean skipActive(){return prefs.getBoolean("skipActive",false);}
    public boolean atOnly(){return prefs.getBoolean("atOnly",true);}
    public boolean pokeReplies(){return prefs.getBoolean("pokeReplies",true);}
    public boolean diagnostic(){return prefs.getBoolean("diagnostic",false);}
    public boolean privateReplies(){return prefs.getBoolean("privateReplies",true);}
    public String get(String key,String fallback){return prefs.getString(key,fallback);}
    public Set<String> blacklist(){return Policy.blacklist(get("blacklist",""));}
    public synchronized void put(String key,String value){prefs.edit().putString(key,value).apply();revision.incrementAndGet();}
    public synchronized void flag(String key,boolean value){prefs.edit().putBoolean(key,value).apply();revision.incrementAndGet();if(key.equals("auto")&&onAutoChanged!=null)onAutoChanged.run();}
    public synchronized void style(Style s){if(s.available())put("style",s.name());}
    public synchronized void update(Map<String,String> values){
        SharedPreferences.Editor edit=prefs.edit();
        for(Map.Entry<String,String> e:values.entrySet())edit.putString(e.getKey(),e.getValue());
        edit.apply();revision.incrementAndGet();
    }
    public synchronized Snapshot snapshot(){return new Snapshot(this);}
    /** One account/configuration captured under the same lock as every mutation. */
    public static final class Snapshot {
        public final String account,endpoint,model,key,fallback;
        public final long revision;
        public final Style style;
        public final boolean enabled,auto,atOnly,known,privateReplies,skipActive,pokeReplies;
        public final Set<String> blacklist,groups;
        private Snapshot(Settings s){
            account=s.account;revision=s.revision.get();style=s.style();known=s.known();
            enabled=s.enabled();auto=s.auto();atOnly=s.atOnly();blacklist=s.blacklist();
            privateReplies=s.privateReplies();skipActive=s.skipActive();pokeReplies=s.pokeReplies();
            groups=Policy.blacklist(s.get("groups",""));
            endpoint=s.get("endpoint",io.github.ling.style.engine.ReplyClient.ENDPOINT);
            model=s.get("model",io.github.ling.style.engine.ReplyClient.MODEL);key=s.get("key","");fallback=s.get("fallback","收到，稍后回复你。");
        }
    }
}
