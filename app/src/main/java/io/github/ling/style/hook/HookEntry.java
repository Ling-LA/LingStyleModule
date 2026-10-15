package io.github.ling.style.hook;

import android.app.*;
import android.content.Context;
import android.os.*;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.ling.style.ui.FloatingMenu;
import java.lang.reflect.Method;
import java.util.*;

public final class HookEntry implements IXposedHookLoadPackage {
    private static boolean started;
    private static int attempts;
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p)throws Throwable{
        if(!"com.tencent.mobileqq".equals(p.packageName)||!p.packageName.equals(p.processName))return;
        XC_MethodHook initialize=new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam param){
                Application app=(Application)param.thisObject;
                // LoadedApk.mApplication is assigned only after Application.attach returns.
                new Handler(Looper.getMainLooper()).post(()->start(app));
            }
        };
        XposedHelpers.findAndHookMethod(Application.class,"onCreate",initialize);
        try{XposedHelpers.findAndHookMethod(Application.class,"attach",Context.class,initialize);}catch(Throwable ignored){}
    }
    private static synchronized void start(Application app){
        if(started||app==null||!"com.tencent.mobileqq".equals(app.getPackageName()))return;
        try{
            Class<?> service=Class.forName("com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService$CppProxy",false,app.getClassLoader());
            String version=app.getPackageManager().getPackageInfo(app.getPackageName(),0).versionName;
            // Fail closed on a different QQ build; standalone preview remains available.
            if(!"9.3.50".equals(version)){XposedBridge.log("[LingStyle] unsupported QQ version "+version);return;}
            Method send=null;
            for(Method m:service.getDeclaredMethods()){
                Class<?>[] t=m.getParameterTypes();
                if(m.getName().equals("sendMsg")&&t.length==5&&t[0]==long.class&&t[1].getSimpleName().equals("Contact")
                    &&List.class.isAssignableFrom(t[2])&&Map.class.isAssignableFrom(t[3])){
                    if(send!=null)throw new IllegalStateException("Ambiguous sendMsg");send=m;
                }
            }
            if(send==null)throw new NoSuchMethodException("sendMsg");
            ModuleRuntime runtime=new ModuleRuntime(app,app.getClassLoader());
            runtime.background.install(app.getClassLoader());
            runtime.activeChat.install();
            started=true;
            // Observe the slash prefix before other modules normalize outgoing elements.
            XposedBridge.hookMethod(send,new XC_MethodHook(XC_MethodHook.PRIORITY_HIGHEST){
                @Override protected void beforeHookedMethod(MethodHookParam p){runtime.observeOutgoing(p);}
            });
            // Preserve metadata prepared by other modules before deferring this send.
            XposedBridge.hookMethod(send,new XC_MethodHook(XC_MethodHook.PRIORITY_LOWEST){
                @Override protected void beforeHookedMethod(MethodHookParam p){runtime.outgoing(p);}
            });
            runtime.attachSend(send);
            FloatingMenu floating=new FloatingMenu(runtime);
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
                public void onActivityResumed(Activity a){runtime.activeChat.resumed(a);runtime.background.visible(true);floating.attach(a);}
                public void onActivityPaused(Activity a){runtime.activeChat.paused(a);runtime.background.visible(false);floating.detach(a);}
                public void onActivityDestroyed(Activity a){floating.detach(a);}
                public void onActivityCreated(Activity a,Bundle b){}
                public void onActivityStarted(Activity a){}
                public void onActivityStopped(Activity a){}
                public void onActivitySaveInstanceState(Activity a,Bundle b){}
            });
            runtime.start();XposedBridge.log("[LingStyle] 0.1.4 initialized QQ="+version);
        }catch(Throwable e){
            if(!started&&++attempts<30)new Handler(Looper.getMainLooper()).postDelayed(()->start(app),1000);
            else {XposedBridge.log("[LingStyle] init failed "+e.getClass().getSimpleName());XposedBridge.log(e);}
        }
    }
}
