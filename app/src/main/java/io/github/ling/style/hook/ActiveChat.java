package io.github.ling.style.hook;

import android.app.*;
import android.content.Context;
import android.os.*;
import android.view.View;
import de.robv.android.xposed.*;
import io.github.ling.style.core.ActiveConversation;
import java.lang.ref.WeakReference;

/** QQ 9.3.50 AIO lifecycle, independent of QFun and stale Activity intent extras. */
public final class ActiveChat {
    private final Host host;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private WeakReference<Object> delegate=new WeakReference<>(null);
    private WeakReference<Activity> resumed=new WeakReference<>(null);
    private volatile ActiveConversation conversation;
    private volatile boolean visible;
    private String shownAccount="";
    public volatile boolean installed;
    private final Runnable tick=new Runnable(){public void run(){capture();if(resumed.get()!=null)main.postDelayed(this,250);}};
    public ActiveChat(Context context,Host host){this.context=context;this.host=host;}
    public void install() throws Exception {
        Class<?> type=host.type("com.tencent.qqnt.aio.activity.AIODelegate");
        XposedHelpers.findAndHookMethod(type,"show",new XC_MethodHook(){
            protected void afterHookedMethod(MethodHookParam p){delegate=new WeakReference<>(p.thisObject);shownAccount=host.account();capture();}
        });
        XposedHelpers.findAndHookMethod(type,"hide",new XC_MethodHook(){
            protected void beforeHookedMethod(MethodHookParam p){if(delegate.get()==p.thisObject){delegate.clear();conversation=null;visible=false;}}
        });
        installed=true;
    }
    public void resumed(Activity activity){resumed=new WeakReference<>(activity);main.removeCallbacks(tick);tick.run();}
    public void paused(Activity activity){if(resumed.get()==activity){resumed.clear();visible=false;main.removeCallbacks(tick);}}
    private boolean interactive(){
        try{return ((PowerManager)context.getSystemService(Context.POWER_SERVICE)).isInteractive()
            &&!((KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked();}
        catch(Exception ignored){return false;}
    }
    /** Main-thread only: a delegate can remain allocated after its page has disappeared. */
    private void capture(){
        visible=false;
        try{
            Object current=delegate.get();Activity activity=resumed.get();
            if(current==null||activity==null||activity.isFinishing()||!interactive())return;
            View root=(View)Host.call(current,"getAIORootView");
            if(root==null||!root.isAttachedToWindow()||!root.isShown()
                ||root.getRootView()!=activity.getWindow().getDecorView().getRootView())return;
            Object contact=Host.call(current,"getAIOContact");
            // Verified against this QQ APK: d = chatType, e = peerUid (not guildId/nickname).
            conversation=new ActiveConversation(shownAccount,(int)Host.number(contact,"d"),Host.text(contact,"e"));
            visible=true;
        }catch(Exception ignored){conversation=null;}
    }
    public boolean suppress(boolean enabled,String account,int type,String uid){
        if(!enabled)return false;
        if(Looper.myLooper()==Looper.getMainLooper())capture();
        ActiveConversation current=conversation;
        return current!=null&&current.matches(true,visible&&interactive(),account,type,uid);
    }
    public String status(){ActiveConversation c=conversation;return "当前聊天页检测 "+installed+" · 可见="+(visible&&interactive())+" · 类型="+(c==null?0:c.type);}
}
