package io.github.ling.style;

import android.app.Instrumentation;
import android.os.Bundle;
import io.github.ling.style.core.Style;
import io.github.ling.style.engine.WebTranslator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import io.github.ling.style.store.Settings;

/** Calls the real website with a synthetic ordinary sentence. Never loads QQ or sends messages. */
public final class DeviceChecks extends Instrumentation {
    private Bundle args;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);args=arguments==null?new Bundle():arguments;start();}
    @Override public void onStart(){
        if(args.containsKey("connection")){Bundle report=io.github.ling.style.engine.ConnectionChecks.run(getTargetContext());finish(report.getBoolean("success")?-1:0,report);return;}
        if(args.containsKey("preload")){Bundle report=io.github.ling.style.engine.PreloadChecks.run(this);finish(report.getBoolean("success")?-1:0,report);return;}
        if(args.containsKey("participation")){Bundle report=io.github.ling.style.engine.ParticipationChecks.run(getTargetContext());finish(report.getBoolean("success")?-1:0,report);return;}
        if(args.containsKey("vision")){Bundle report=io.github.ling.style.engine.VisionChecks.run(getTargetContext());finish(report.getBoolean("success")?-1:0,report);return;}
        if(args.containsKey("floating")){checkFloating();return;}
        Bundle summary=new Bundle();boolean success=true;
        Settings settings=new Settings(getContext());settings.selectAccount("12345");settings.put("key","test-old");
        Settings.Snapshot saved=settings.snapshot();settings.selectAccount("67890");settings.put("key","test-new");
        boolean isolated=saved.account.equals("12345")&&saved.key.equals("test-old")&&settings.snapshot().key.equals("test-new")&&saved.revision!=settings.revision.get();
        summary.putBoolean("settings_snapshot_isolated",isolated);success&=isolated;
        for(Style style:Style.values()){
            if(!style.available())continue;
            if(args.containsKey("style")&&!style.name().equals(args.getString("style")))continue;
            Bundle report=new Bundle();CountDownLatch complete=new CountDownLatch(1);
            android.os.PowerManager pm=(android.os.PowerManager)getTargetContext().getSystemService(android.content.Context.POWER_SERVICE);
            android.os.PowerManager.WakeLock wake=pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,"LingStyle:device-check");wake.acquire(60000);
            report.putBoolean("interactive",pm.isInteractive());
            runOnMainSync(()->{WebTranslator engine=new WebTranslator(getTargetContext());WebTranslator.Callback callback=(result,error)->{
                report.putString("style",style.name());report.putString("web_error",error==null?"":error);
                report.putInt("result_length",result==null?0:result.length());
                report.putBoolean("success",error==null&&result!=null&&!result.trim().isEmpty());
                report.putString("engine",engine.diagnostics);complete.countDown();
            };
                String input=args.containsKey("replyBase")?"收到，稍后回复你。":"今天下午三点见。";
                if(args.containsKey("warmup")){
                    WebTranslator.Preload lease=engine.preload("device-check",1,style,()->true);
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->engine.translate(lease,input,callback),3000);
                }else engine.translate(style,input,callback);
                // Test-only inspection of the rendered page for a fixed synthetic input.
                if(args.containsKey("inspect"))new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
                    try{java.lang.reflect.Field f=WebTranslator.class.getDeclaredField("page");f.setAccessible(true);android.webkit.WebView page=(android.webkit.WebView)f.get(engine);
                        if(page!=null)page.evaluateJavascript("document.body.innerText.slice(0,5000)",value->report.putString("page_state",value));
                    }catch(Exception ignored){}
                },15000);
            });
            try{if(!complete.await(55,TimeUnit.SECONDS))report.putString("test_error","callback deadline exceeded");}
            catch(InterruptedException e){report.putString("test_error","interrupted");}
            if(wake.isHeld())wake.release();sendStatus(0,report);summary.putBoolean(style.name(),report.getBoolean("success"));success&=report.getBoolean("success");
        }
        summary.putBoolean("success",success);finish(success?-1:0,summary);
    }
    private void checkFloating(){
        Bundle result=new Bundle();android.app.Activity activity=null;
        final io.github.ling.style.ui.EdgeBubble[] bubble={null};final int[] clicks={0};
        try{
            activity=startActivitySync(new android.content.Intent(getTargetContext(),io.github.ling.style.ui.MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            final android.app.Activity a=activity;
            runOnMainSync(()->{bubble[0]=new io.github.ling.style.ui.EdgeBubble(a);bubble[0].mount((android.view.ViewGroup)a.getWindow().getDecorView());bubble[0].setOnClickListener(v->clicks[0]++);});
            waitForIdleSync();Thread.sleep(3450);
            runOnMainSync(()->{if(!bubble[0].getContentDescription().toString().startsWith("展开"))throw new AssertionError("idle collapse");});
            result.putBoolean("idle_3_seconds",true);
            runOnMainSync(()->bubble[0].performClick());Thread.sleep(300);
            if(clicks[0]!=0)throw new AssertionError("collapsed click opened menu");
            runOnMainSync(()->bubble[0].performClick());
            if(clicks[0]!=1)throw new AssertionError("expanded click did not open menu");
            result.putBoolean("tap_expand_then_menu",true);
            runOnMainSync(()->bubble[0].setMenuOpen(true));Thread.sleep(3400);
            runOnMainSync(()->{if(bubble[0].getContentDescription().toString().startsWith("展开"))throw new AssertionError("collapsed during menu");bubble[0].setMenuOpen(false);});
            result.putBoolean("menu_suspends_timer",true);
            final long down=android.os.SystemClock.uptimeMillis();
            runOnMainSync(()->touch(bubble[0],down,android.view.MotionEvent.ACTION_DOWN,600,1000));
            Thread.sleep(android.view.ViewConfiguration.getLongPressTimeout()+100);
            runOnMainSync(()->{touch(bubble[0],down,android.view.MotionEvent.ACTION_MOVE,-1000,650);touch(bubble[0],down,android.view.MotionEvent.ACTION_UP,-1000,650);});
            Thread.sleep(300);
            final float[] y={0};runOnMainSync(()->{if(bubble[0].getX()>100)throw new AssertionError("left snap");y[0]=bubble[0].getY();});
            if(clicks[0]!=1)throw new AssertionError("drag opened menu");
            result.putBoolean("long_press_drag_snap_no_click",true);
            runOnMainSync(()->{bubble[0].dispose();bubble[0]=new io.github.ling.style.ui.EdgeBubble(a);bubble[0].mount((android.view.ViewGroup)a.getWindow().getDecorView());});
            waitForIdleSync();Thread.sleep(100);
            runOnMainSync(()->{if(bubble[0].getX()>100||Math.abs(bubble[0].getY()-y[0])>2)throw new AssertionError("restore position");});
            result.putBoolean("position_restored",true);
            Thread.sleep(3350);
            runOnMainSync(()->{float visible=bubble[0].getX()+bubble[0].getWidth();float density=a.getResources().getDisplayMetrics().density;if(Math.abs(visible/density-12)>1)throw new AssertionError("peek width");});
            result.putBoolean("visible_strip_12dp",true);result.putBoolean("success",true);
        }catch(Throwable e){result.putString("failure",e.toString());}
        finally{final android.app.Activity a=activity;runOnMainSync(()->{if(bubble[0]!=null)bubble[0].dispose();if(a!=null)a.finish();});}
        finish(result.getBoolean("success")?-1:0,result);
    }
    private static void touch(android.view.View view,long down,int action,float x,float y){
        android.view.MotionEvent event=android.view.MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,x,y,0);
        view.dispatchTouchEvent(event);event.recycle();
    }

}
