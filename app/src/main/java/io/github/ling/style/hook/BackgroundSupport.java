package io.github.ling.style.hook;

import android.app.*;
import android.content.*;
import android.os.*;
import de.robv.android.xposed.*;
import io.github.ling.style.service.HostReplyService;

/** Own declared component in the paired QQ package; never reuses a QQ service. */
public final class BackgroundSupport {
    public static final String COMPONENT="io.github.ling.style.service.HostReplyService";
    public static volatile ModuleRuntime owner;
    private final ModuleRuntime runtime;
    private final Handler main=new Handler(Looper.getMainLooper());
    public volatile String status="后台服务未运行";
    public volatile HostReplyService service;
    private volatile boolean visible;
    private long attempted;
    public BackgroundSupport(ModuleRuntime runtime){this.runtime=runtime;}
    public void install(ClassLoader loader){
        owner=runtime;
        XC_MethodHook create=new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam p){
            if(p.args.length>=2&&COMPONENT.equals(p.args[1]))p.setResult(new HostReplyService());
        }};
        for(String name:new String[]{"android.app.AppComponentFactory","org.lsposed.lspatch.metaloader.LSPAppComponentFactoryStub","org.lsposed.lspatch.loader.LSPAppComponentFactory"}){
            try{XposedBridge.hookAllMethods(Class.forName(name,false,loader),"instantiateService",create);}catch(Throwable ignored){}
        }
    }
    public void visible(boolean value){visible=value;if(value)sync();}
    public boolean isVisible(){return visible;}
    public void sync(){main.post(()->{
        boolean enabled=runtime.settings.known()&&runtime.settings.auto();
        if(!enabled){if(service!=null)service.finishService();return;}
        if(service!=null||!visible)return;
        long now=SystemClock.elapsedRealtime();if(now-attempted<10000)return;attempted=now;
        try{
            ComponentName component=new ComponentName(runtime.context.getPackageName(),COMPONENT);
            runtime.context.getPackageManager().getServiceInfo(component,0);
            runtime.context.startForegroundService(new Intent().setComponent(component));status="正在启动后台回复服务";
        }catch(Exception e){status="后台服务不可用："+e.getClass().getSimpleName()+"（需配套 QQ 安装包）";}
    });}
}
