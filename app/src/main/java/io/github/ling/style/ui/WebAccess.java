package io.github.ling.style.ui;

import android.annotation.SuppressLint;
import android.app.*;
import android.os.*;
import android.webkit.*;
import android.widget.*;
import android.view.*;
import org.json.*;

/** Official activation form in the same QQ WebView profile as the translation engine. */
public final class WebAccess {
    public static volatile String status="QQ 内网页授权尚未确认";
    private WebAccess(){}
    @SuppressLint("SetJavaScriptEnabled") public static void show(Activity a,String license){
        Dialog dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell=PinkTheme.column(a);shell.setBackground(PinkTheme.shape(a,PinkTheme.SOFT,24,0));
        TextView heading=PinkTheme.label(a,"Yinglish 官方网页授权",18,PinkTheme.DEEP);heading.setPadding(20,20,20,10);shell.addView(heading);
        TextView state=PinkTheme.label(a,"此页面与 QQ 内转换共享登录和授权状态",12,PinkTheme.MUTED);state.setPadding(20,0,20,10);shell.addView(state);
        WebView web=new WebView(a);shell.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        Handler handler=new Handler(Looper.getMainLooper());final int[] phase={0};final long deadline=SystemClock.elapsedRealtime()+60000;
        Runnable poll=new Runnable(){public void run(){
            if(!dialog.isShowing())return;
            if(SystemClock.elapsedRealtime()>deadline){state.setText("自动检查已结束，可直接操作官方页面；完成后关闭即可");return;}
            String code="(function(){if(location.origin!=='https://yinglish.club'||document.readyState!=='complete')return '';var d=document.querySelector('[role=dialog]');var text=d?d.innerText:'';if(/已激活激活码/.test(text)){var date=text.match(/[0-9]{4}-[0-9]{2}-[0-9]{2}/);return 'ok:'+(date?date[0]:'有效');}";
            if(!license.isEmpty()){
                code+="if(!d){var open=Array.from(document.querySelectorAll('button')).find(b=>b.textContent.trim()==='激活');if(open)open.click();return '';}";
                if(phase[0]==0)code+="var tab=Array.from(d.querySelectorAll('[role=tab]')).find(b=>b.textContent.trim()==='使用激活码');if(tab){tab.click();return 'tab';}";
                else if(phase[0]==1)code+="var input=d.querySelector('[role=tabpanel] input');var button=Array.from(d.querySelectorAll('button')).find(b=>b.textContent.trim()==='激活许可证');if(input&&button){Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,"+JSONObject.quote(license)+");input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));return 'filled';}";
                else if(phase[0]==2)code+="var submit=Array.from(d.querySelectorAll('button')).find(b=>b.textContent.trim()==='激活许可证');if(submit&&!submit.disabled){submit.click();return 'submitted';}";
            }
            code+="return '';})()";
            web.evaluateJavascript(code,value->{
                if(!dialog.isShowing())return;
                try{Object parsed=new JSONTokener(value).nextValue();String response=parsed instanceof String?(String)parsed:"";
                    if(response.startsWith("ok:")){CookieManager.getInstance().flush();status="QQ 内网页已激活至 "+response.substring(3);state.setText(status);android.util.Log.i("LingStyle",status);return;}
                    if(response.equals("tab"))phase[0]=1;
                    if(response.equals("filled"))phase[0]=2;
                    if(response.equals("submitted")){phase[0]=3;state.setText("已提交官方激活表单，正在确认授权…");}
                }catch(Exception ignored){}
                handler.postDelayed(this,600);
            });
        }};
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){boolean blocked=!"https".equals(r.getUrl().getScheme())||!"yinglish.club".equals(r.getUrl().getHost());if(blocked)Toast.makeText(a,"请在外部浏览器处理购买或联系支持",Toast.LENGTH_SHORT).show();return blocked;}
            @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail){dialog.dismiss();return true;}
        });
        Button close=PinkTheme.button(a,"完成并返回 QQ",false);shell.addView(close);close.setOnClickListener(v->dialog.dismiss());
        dialog.setContentView(shell);dialog.setOnDismissListener(d->{handler.removeCallbacksAndMessages(null);web.stopLoading();web.destroy();});dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setLayout(a.getResources().getDisplayMetrics().widthPixels-ControlPanel.dp(a,20),(int)(a.getResources().getDisplayMetrics().heightPixels*.88));}
        web.loadUrl("https://yinglish.club/translators/%E8%B2%93%E5%A8%98%E8%AA%9E");handler.postDelayed(poll,600);
    }
}
