package io.github.ling.style.engine;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.*;
import android.webkit.*;
import io.github.ling.style.core.Style;
import org.json.*;
import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;

/** Uses the actual website DOM. No copied prompt, guessed private endpoint or JS bridge. */
public final class WebTranslator {
    public interface Callback {void complete(String result,String error);}
    private static final long BUDGET=45000;
    private static final long PRELOAD_TTL=60000;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Context context;
    private final ArrayDeque<Job> queue=new ArrayDeque<>();
    private Job active;
    private WebView page;
    private Preload warm;
    private long warmUntil,pageSerial;
    private boolean preloadHit;
    private int phase;
    private boolean callbackPending;
    private boolean sawBusy,clickedAgain;
    private long clickedAt;
    private String baseline="";
    private long serial;
    public volatile String status="网页引擎待命";
    public volatile String diagnostics="";
    /** Immutable configuration lease; validity is checked on the main thread, including while queued. */
    public static final class Preload {
        final String owner;final long revision;final Style style;final BooleanSupplier valid;
        private Preload(String o,long r,Style s,BooleanSupplier v){owner=o;revision=r;style=s;valid=v;}
        boolean current(){try{return valid.getAsBoolean();}catch(Throwable e){return false;}}
        boolean matches(Preload other){return other!=null&&owner.equals(other.owner)&&revision==other.revision&&style==other.style&&current()&&other.current();}
    }
    private static final class Job {
        final Style style;final String text;final Callback callback;final long deadline;
        final Preload preload;
        Job(Style s,String t,Callback c,Preload p){style=s;text=t;callback=c;preload=p;deadline=SystemClock.elapsedRealtime()+BUDGET;}
    }
    public WebTranslator(Context context){Context app=context.getApplicationContext();this.context=app==null?context:app;}
    /** Opens only an idle page. Does not fill text, click Translate, or extend a conversion deadline. */
    public Preload preload(String owner,long revision,Style style,BooleanSupplier valid){
        Preload request=new Preload(owner,revision,style,valid);
        main.post(()->{
            if(active!=null||!queue.isEmpty()||owner==null||style==null||!style.available()||!request.current())return;
            if(page!=null&&warm!=null&&warm.matches(request)&&SystemClock.elapsedRealtime()<warmUntil)return;
            discardPage();warm=request;warmUntil=SystemClock.elapsedRealtime()+PRELOAD_TTL;
            try{openPage(style);status="正在预加载 "+style.title;expireWarm(pageSerial);}
            catch(Throwable ignored){discardPage();status="网页引擎待命";}
        });
        return request;
    }
    private void expireWarm(long id){
        main.postDelayed(()->{
            if(active!=null||warm==null||id!=pageSerial)return;
            if(!warm.current()||SystemClock.elapsedRealtime()>=warmUntil){discardPage();status="网页引擎待命";}
            else expireWarm(id);
        },1000);
    }
    public void translate(Style style,String text,Callback callback){
        translate(style,text,callback,null);
    }
    public void translate(Preload preload,String text,Callback callback){
        translate(preload.style,text,callback,preload);
    }
    private void translate(Style style,String text,Callback callback,Preload preload){
        main.post(()->{
            if(style==null||!style.available()){callback.complete(null,"该风格不可用");return;}
            if(text==null||text.trim().isEmpty()||text.length()>4000){callback.complete(null,"只支持 1–4000 字纯文本");return;}
            if(queue.size()>=8){callback.complete(null,"转换队列已满");return;}
            queue.add(new Job(style,text,callback,preload));if(active==null)next();
        });
    }
    private void next(){
        if(active!=null)return;
        active=queue.poll();if(active==null){status="网页引擎待命";return;}
        if(SystemClock.elapsedRealtime()>active.deadline){finish(null,"排队超时");return;}
        if(active.preload!=null&&!active.preload.current()){finish(null,"配置已变化，取消旧转换");return;}
        final long id=++serial;
        try{
            preloadHit=page!=null&&warm!=null&&warm.matches(active.preload)&&SystemClock.elapsedRealtime()<warmUntil;
            if(!preloadHit){discardPage();openPage(active.style);}
            warm=null; // Consume once. Never reuse a page containing another job's input/output.
            phase=0;callbackPending=false;sawBusy=false;clickedAgain=false;baseline="";
            status=preloadHit?"使用预加载页面 · "+active.style.title:"正在打开 "+active.style.title;
            poll(id);
        }catch(Throwable e){finish(null,"WebView 不可用："+e.getClass().getSimpleName());}
    }
    @SuppressLint("SetJavaScriptEnabled") private void openPage(Style style){
            final long id=++pageSerial;
            page=new WebView(context);
            page.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT,false);
            page.onResume();
            WebSettings s=page.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
            s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            s.setJavaScriptCanOpenWindowsAutomatically(false);s.setSupportMultipleWindows(false);
            CookieManager.getInstance().setAcceptThirdPartyCookies(page,false);
            page.setWebViewClient(new WebViewClient(){
                @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){
                    if(id==pageSerial)pageFailure("网页渲染进程已退出");
                    else try{view.destroy();}catch(Throwable ignored){}
                    return true;
                }
                @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                    return !official(request.getUrl())||(request.isForMainFrame()&&!style.url.equals(request.getUrl().toString()));
                }
                @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){
                    if(request.isForMainFrame()&&id==pageSerial)pageFailure("网页加载失败（"+error.getErrorCode()+"）");
                }
                @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){
                    if(request.isForMainFrame()&&id==pageSerial&&response.getStatusCode()>=400)pageFailure("网页返回 HTTP "+response.getStatusCode());
                }
            });
            page.loadUrl(style.url);
    }
    private void pageFailure(String error){
        if(active!=null)finish(null,error);
        else{discardPage();status="预加载未完成，转换时重试";}
    }
    private void discardPage(){
        ++pageSerial;warm=null;
        WebView old=page;page=null;
        if(old!=null)try{old.stopLoading();old.destroy();}catch(Throwable ignored){}
    }
    private static boolean official(Uri u){return "https".equals(u.getScheme())&&"yinglish.club".equals(u.getHost());}
    private void poll(long id){
        if(active==null||id!=serial)return;
        if(active.preload!=null&&!active.preload.current()){finish(null,"配置已变化，取消旧转换");return;}
        if(SystemClock.elapsedRealtime()>active.deadline){finish(null,"网页转换超时（阶段 "+phase+"）；请检查网络或网页是否需要验证");return;}
        if(!callbackPending){
            callbackPending=true;
            final String script;
            if(phase==0){
                script="(function(){if(location.origin!=='https://yinglish.club'||document.readyState!=='complete')return 'wait';var s=document.getElementById('sourceText'),t=document.getElementById('targetText');if(!s||!t)return 'wait';Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(s,"+JSONObject.quote(active.text)+");s.dispatchEvent(new Event('input',{bubbles:true}));s.dispatchEvent(new Event('change',{bubbles:true}));return 'filled';})()";
            }else if(phase==1){
                script="(function(){var all=Array.from(document.querySelectorAll('button')),b=all.find(x=>['Translate','翻译','翻譯'].includes(x.textContent.trim()));if(!b)return 'wait:'+all.map(x=>x.textContent.trim()).join('|').slice(0,400);if(b.disabled){var s=document.getElementById('sourceText');if(s){var set=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set;set.call(s,'');s.dispatchEvent(new Event('input',{bubbles:true}));set.call(s,"+JSONObject.quote(active.text)+");s.dispatchEvent(new Event('input',{bubbles:true}));s.dispatchEvent(new Event('change',{bubbles:true}));}return 'wait:disabled';}var source=document.getElementById('sourceText');if(!source||source.value!=="+JSONObject.quote(active.text)+"){if(source){Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(source,"+JSONObject.quote(active.text)+");source.dispatchEvent(new Event('input',{bubbles:true}));}return 'wait:input';}var target=document.getElementById('targetText'),previous=target?target.value:'';b.click();return JSON.stringify({clicked:true,baseline:previous});})()";
            }else{
                script="(function(){var t=document.getElementById('targetText');var buttons=Array.from(document.querySelectorAll('button'));var b=buttons.find(x=>['Translate','翻译','翻譯'].includes(x.textContent.trim()));return JSON.stringify({result:t&&b&&!b.disabled?t.value:'',error:Array.from(document.querySelectorAll('[role=dialog],[role=alert],[data-sonner-toast]')).filter(x=>x.getClientRects().length).some(x=>/今日.{0,5}翻译限制|今日.{0,5}翻譯限制|达到.{0,8}次翻译|daily.{0,15}limit/i.test(x.innerText))?'网页免费额度已用完，请在模块内激活授权':'',visible:document.visibilityState,ready:!!b&&!b.disabled,busy:buttons.some(x=>/Translating|翻译中|翻譯中/.test(x.textContent)),length:t?t.value.length:0,alert:!!document.querySelector('[role=alert],[data-sonner-toast],.g-recaptcha,iframe[src*=\"challenges.cloudflare.com\"]')});})()";
            }
            // Bind every DOM operation to the exact style URL, input and job, including on a warm page.
            String guard="if(location.href!=="+JSONObject.quote(active.style.url)+")return 'wait:style';";
            if(phase==0)guard+="window.__lingStyleJob="+id+";";
            else{
                guard+="if(window.__lingStyleJob!=="+id+")return 'wait:job-owner';";
                // Before the click, the existing phase-1 script repairs input cleared by hydration.
                // After the click, never accept a result whose source belongs to something else.
                if(phase==2)guard+="if(!document.getElementById('sourceText')||document.getElementById('sourceText').value!=="+JSONObject.quote(active.text)+")return 'wait:input-owner';";
            }
            String guarded="(function(){"+guard+"return "+script+";})()";
            try{page.evaluateJavascript(guarded,value->{
                if(active==null||id!=serial)return;
                if(active.preload!=null&&!active.preload.current()){finish(null,"配置已变化，取消旧转换");return;}
                callbackPending=false;
                diagnostics="phase="+phase+" preload="+preloadHit+" url="+page.getUrl()+" title="+page.getTitle();
                try{
                    Object decoded=new JSONTokener(value).nextValue();String out=decoded instanceof String?(String)decoded:"";
                    if(out.startsWith("wait:")){diagnostics+=" "+out;return;}
                    if(phase==0&&out.equals("filled")){phase=1;status="准备翻译";}
                    else if(phase==1&&out.startsWith("{")){JSONObject clicked=new JSONObject(out);if(clicked.optBoolean("clicked")){if(!clickedAgain)baseline=clicked.optString("baseline","");phase=2;clickedAt=SystemClock.elapsedRealtime();status="网页正在翻译";}}
                    else if(phase==2&&!out.trim().isEmpty()){
                        JSONObject state=new JSONObject(out);String failure=state.optString("error","");if(!failure.isEmpty()){finish(null,failure);return;}String result=state.optString("result","");
                        diagnostics+=" visible="+state.optString("visible")+" ready="+state.optBoolean("ready")+" busy="+state.optBoolean("busy")+" length="+state.optInt("length")+" alert="+state.optBoolean("alert");
                        sawBusy|=state.optBoolean("busy");
                        if(result.length()>8000){finish(null,"网页结果过长");return;}
                        if(!result.trim().isEmpty()&&(sawBusy||!result.equals(baseline)))finish(result,null);
                        // A hydrated button may consume its first click without entering loading.
                        // Retry that UI action once only, never after loading, an error or a challenge.
                        else if(!sawBusy&&!clickedAgain&&state.optBoolean("ready")&&!state.optBoolean("alert")&&SystemClock.elapsedRealtime()-clickedAt>=1600){clickedAgain=true;phase=1;}
                    }
                }catch(Exception e){finish(null,"网页结果解析失败");}
            });}catch(Throwable e){finish(null,"网页执行失败");return;}
        }
        main.postDelayed(()->poll(id),400);
    }
    private void finish(String result,String error){
        if(active==null)return;
        Job done=active;active=null;++serial;
        discardPage();
        status=error==null?"转换完成":error;
        try{done.callback.complete(result,error);}finally{main.post(this::next);}
    }
}
