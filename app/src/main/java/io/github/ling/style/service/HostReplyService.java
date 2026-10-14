package io.github.ling.style.service;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import io.github.ling.style.hook.*;

/** User-visible service, running in QQ's main process only while auto-reply is enabled. */
public final class HostReplyService extends Service {
    private static final String CHANNEL="ling_style_auto_reply",STOP="io.github.ling.style.STOP_AUTO";
    private static final int ID=0x4c535459;
    @Override public void onCreate(){
        super.onCreate();ModuleRuntime runtime=BackgroundSupport.owner;
        if(runtime==null){stopSelf();return;}
        try{
            NotificationManager manager=getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,"玲 · 自动回复后台运行",NotificationManager.IMPORTANCE_LOW));
            Intent launch=getPackageManager().getLaunchIntentForPackage(getPackageName());
            PendingIntent open=launch==null?null:PendingIntent.getActivity(this,ID,launch,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            PendingIntent stop=PendingIntent.getService(this,ID,new Intent().setComponent(new ComponentName(getPackageName(),BackgroundSupport.COMPONENT)).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification notification=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("玲 · 自动回复运行中")
                .setContentText("按所选风格处理允许范围内的新消息").setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
                .addAction(new Notification.Action.Builder(null,"关闭自动回复",stop).build()).build();
            if(Build.VERSION.SDK_INT>=34)startForeground(ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(ID,notification);
            runtime.background.service=this;runtime.background.status="后台回复服务运行中（常驻通知）";
        }catch(Throwable e){runtime.background.status="后台服务启动失败："+e.getClass().getSimpleName();stopSelf();}
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        ModuleRuntime runtime=BackgroundSupport.owner;
        if(runtime==null){finishService();return START_NOT_STICKY;}
        if(intent!=null&&STOP.equals(intent.getAction()))runtime.settings.flag("auto",false);
        if(!runtime.settings.auto())finishService();
        return START_NOT_STICKY;
    }
    public void finishService(){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public void onTimeout(int startId,int type){finishService();}
    @Override public void onDestroy(){
        ModuleRuntime runtime=BackgroundSupport.owner;
        if(runtime!=null&&runtime.background.service==this){runtime.background.service=null;runtime.background.status="后台回复服务已停止";}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
