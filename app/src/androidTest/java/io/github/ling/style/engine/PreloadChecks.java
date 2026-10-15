package io.github.ling.style.engine;

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.SystemClock;
import android.webkit.WebView;
import io.github.ling.style.core.Style;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real Android WebView, deterministic local DOM; no model requests or QQ messages. */
public final class PreloadChecks {
    private final Instrumentation test;
    private final Bundle report=new Bundle();
    private WebTranslator engine;
    private int checks;
    private static final class Result implements WebTranslator.Callback {
        final CountDownLatch done=new CountDownLatch(1);String text,error;int calls;
        public void complete(String text,String error){this.text=text;this.error=error;calls++;done.countDown();}
        void await()throws Exception{if(!done.await(8,TimeUnit.SECONDS))throw new AssertionError("callback timed out");}
    }
    private PreloadChecks(Instrumentation i){test=i;}
    public static Bundle run(Instrumentation i){return new PreloadChecks(i).run();}
    private void ui(Runnable r){test.runOnMainSync(r);}
    private Object field(String name){try{Field f=WebTranslator.class.getDeclaredField(name);f.setAccessible(true);return f.get(engine);}catch(Exception e){throw new AssertionError(e);}}
    private void set(String name,Object value){try{Field f=WebTranslator.class.getDeclaredField(name);f.setAccessible(true);f.set(engine,value);}catch(Exception e){throw new AssertionError(e);}}
    private WebView page(){final WebView[] p={null};ui(()->p[0]=(WebView)field("page"));return p[0];}
    private void check(boolean condition,String name){if(!condition)throw new AssertionError(name);checks++;report.putBoolean(name,true);}
    private String js(WebView page,String script)throws Exception{
        String[] result={null};CountDownLatch done=new CountDownLatch(1);
        ui(()->page.evaluateJavascript(script,v->{result[0]=v;done.countDown();}));
        if(!done.await(3,TimeUnit.SECONDS))throw new AssertionError("JS timed out");return result[0];
    }
    private void fixture(WebView page,Style style)throws Exception{
        ui(()->{page.stopLoading();page.loadDataWithBaseURL(style.url,
            "<html><head><title>Local preload check</title></head><body><textarea id='sourceText'></textarea>"+
            "<textarea id='targetText'>STALE</textarea><button onclick=\"var b=this,s=document.getElementById('sourceText').value;window.clicks=(window.clicks||0)+1;b.disabled=true;b.textContent='Translating';setTimeout(function(){document.getElementById('targetText').value='"+style.name()+"|'+s;b.disabled=false;b.textContent='Translate'},650)\">Translate</button></body></html>","text/html","UTF-8",style.url);});
        long until=SystemClock.elapsedRealtime()+4000;
        while(SystemClock.elapsedRealtime()<until){if("true".equals(js(page,"document.readyState==='complete'&&!!document.getElementById('sourceText')")))return;Thread.sleep(50);}
        throw new AssertionError("fixture load timed out");
    }
    private WebTranslator.Preload warm(String owner,long revision,Style style,AtomicBoolean valid)throws Exception{
        WebTranslator.Preload lease=engine.preload(owner,revision,style,valid::get);test.waitForIdleSync();
        if(page()==null)throw new AssertionError("no warm page");return lease;
    }
    private void assertResult(Result r,String expected,String label)throws Exception{
        r.await();check(r.error==null&&expected.equals(r.text)&&r.calls==1,label);
    }
    private Bundle run(){
        try{
            ui(()->engine=new WebTranslator(test.getTargetContext()));
            for(Style style:Style.values()){
                AtomicBoolean valid=new AtomicBoolean(true);
                WebTranslator.Preload lease=warm("account-a",1,style,valid);WebView loaded=page();fixture(loaded,style);
                check("true".equals(js(loaded,"!window.clicks&&document.getElementById('sourceText').value===''")),style.name()+"_preload_has_no_input_or_click");
                Result r=new Result();engine.translate(lease,"合成测试",r);test.waitForIdleSync();
                check(page()==loaded,style.name()+"_warm_page_consumed");
                assertResult(r,style.name()+"|合成测试",style.name()+"_fresh_exact_output");
            }
            AtomicBoolean valid=new AtomicBoolean(true);
            WebTranslator.Preload hydration=warm("account-a",2,Style.CAT,valid);fixture(page(),Style.CAT);
            js(page(),"document.getElementById('sourceText').addEventListener('input',function(){if(!window.resetOnce){window.resetOnce=true;setTimeout(()=>{document.getElementById('sourceText').value=''},50)}})");
            Result repaired=new Result();engine.translate(hydration,"hydration",repaired);
            assertResult(repaired,"CAT|hydration","hydration_cleared_input_repaired_before_click");
            WebTranslator.Preload a=warm("account-a",10,Style.CAT,valid);WebView old=page();
            WebTranslator.Preload same=warm("account-a",10,Style.CAT,valid);
            check(page()==old&&a.matches(same),"same_configuration_shares_idle_page");
            WebTranslator.Preload other=warm("account-b",10,Style.CAT,valid);
            check(page()!=old&&!a.matches(other),"account_isolated");old=page();
            other=warm("account-b",11,Style.CAT,valid);
            check(page()!=old&&!a.matches(other),"revision_isolated");old=page();
            other=warm("account-b",11,Style.YANDERE,valid);
            check(page()!=old&&!a.matches(other),"style_isolated");
            valid.set(false);Result cancelled=new Result();engine.translate(other,"old",cancelled);cancelled.await();
            check(cancelled.text==null&&cancelled.error!=null,"invalid_lease_cannot_start");

            valid=new AtomicBoolean(true);a=warm("account-a",20,Style.CAT,valid);old=page();
            ui(()->set("warmUntil",0L));Result expired=new Result();engine.translate(a,"expired",expired);test.waitForIdleSync();
            check(page()!=old,"expired_page_cold_fallback");fixture(page(),Style.CAT);assertResult(expired,"CAT|expired","expired_fallback_output");

            valid=new AtomicBoolean(true);a=warm("account-a",21,Style.CAT,valid);
            ui(()->{try{java.lang.reflect.Method f=WebTranslator.class.getDeclaredMethod("pageFailure",String.class);f.setAccessible(true);f.invoke(engine,"test network failure");}catch(Exception e){throw new AssertionError(e);}});
            check(page()==null,"failed_preload_discarded");Result recovered=new Result();engine.translate(a,"retry",recovered);test.waitForIdleSync();
            fixture(page(),Style.CAT);assertResult(recovered,"CAT|retry","failed_preload_cold_fallback");

            valid=new AtomicBoolean(true);a=warm("account-a",22,Style.CAT,valid);fixture(page(),Style.YANDERE);
            Result wrong=new Result();engine.translate(a,"wrong",wrong);Thread.sleep(900);
            check("true".equals(js(page(),"!window.clicks&&document.getElementById('sourceText').value===''")),"wrong_style_not_clicked");
            valid.set(false);wrong.await();check(wrong.text==null&&wrong.error!=null,"switch_during_conversion_cancels");

            valid=new AtomicBoolean(true);a=warm("account-a",23,Style.CAT,valid);fixture(page(),Style.CAT);old=page();
            Result first=new Result(),second=new Result();engine.translate(a,"first",first);engine.translate(a,"second",second);
            engine.preload("account-b",24,Style.YANDERE,()->true);test.waitForIdleSync();
            check(page()==old,"busy_preload_does_not_replace_active_page");
            assertResult(first,"CAT|first","first_queued_result");test.waitForIdleSync();
            check(page()!=null&&page()!=old,"next_job_has_fresh_page");fixture(page(),Style.CAT);assertResult(second,"CAT|second","second_queued_result_not_stale");

            valid=new AtomicBoolean(true);a=warm("account-a",25,Style.CAT,valid);fixture(page(),Style.CAT);
            AtomicBoolean pendingValid=new AtomicBoolean(true);
            WebTranslator.Preload pending=engine.preload("account-a",25,Style.CAT,pendingValid::get);
            Result inFlight=new Result(),queued=new Result();engine.translate(a,"ongoing",inFlight);engine.translate(pending,"cancelled",queued);pendingValid.set(false);
            assertResult(inFlight,"CAT|ongoing","valid_active_job_unaffected");queued.await();
            check(queued.error!=null&&queued.text==null,"invalid_queued_job_cancelled");

            valid=new AtomicBoolean(true);warm("account-a",26,Style.CAT,valid);old=page();
            Result manual=new Result();engine.translate(Style.YANDERE,"manual",manual);test.waitForIdleSync();
            check(page()!=old,"manual_does_not_consume_auto_lease");fixture(page(),Style.YANDERE);assertResult(manual,"YANDERE|manual","manual_conversion_preserved");
            report.putBoolean("success",true);
        }catch(Throwable e){report.putString("failure",e.toString());}
        finally{ui(()->{if(engine!=null)try{java.lang.reflect.Method m=WebTranslator.class.getDeclaredMethod("discardPage");m.setAccessible(true);m.invoke(engine);}catch(Exception ignored){}});}
        report.putInt("checks",checks);return report;
    }
}
