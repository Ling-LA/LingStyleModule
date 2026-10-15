package io.github.ling.style.ui;

import android.app.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import io.github.ling.style.core.Style;
import io.github.ling.style.engine.WebTranslator;
import io.github.ling.style.store.Settings;
import java.util.function.Supplier;

public final class ControlPanel {
    private ControlPanel(){}
    public static Dialog show(Activity a,Settings settings,WebTranslator web,Supplier<String> status,Runnable importOld){
        return show(a,settings,web,status,importOld,new io.github.ling.style.engine.ReplyClient(),null);
    }
    public static Dialog show(Activity a,Settings settings,WebTranslator web,Supplier<String> status,Runnable importOld,io.github.ling.style.engine.ReplyClient replies,Runnable importLocal){
        return show(a,settings,web,status,importOld,replies,importLocal,null);
    }
    public static Dialog show(Activity a,Settings settings,WebTranslator web,Supplier<String> status,Runnable importOld,io.github.ling.style.engine.ReplyClient replies,Runnable importLocal,ContactPicker.Source roster){
        String owner=settings.account();
        LinearLayout shell=PinkTheme.column(a);shell.setBackground(PinkTheme.shape(a,PinkTheme.SOFT,24,0));
        shell.addView(PinkTheme.header(a,"玲 · 风格控制","选一种心情，让转换与自动回复使用同一风格"));
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout box=PinkTheme.column(a);box.setPadding(dp(a,14),dp(a,14),dp(a,14),dp(a,4));scroll.addView(box);
        LinearLayout styles=PinkTheme.card(a,"01  /  选择风格");box.addView(styles);
        RadioGroup group=new RadioGroup(a);styles.addView(group);
        for(Style style:Style.values()){
            RadioButton row=new RadioButton(PinkTheme.controls(a));row.setText(style.title);row.setTextSize(15);row.setId(100+style.ordinal());row.setMinHeight(dp(a,48));row.setEnabled(style.available());
            row.setPadding(dp(a,10),dp(a,6),dp(a,12),dp(a,6));row.setButtonTintList(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xFFC8BDC2,PinkTheme.DEEP,0xFFB79AA6}));
            RadioGroup.LayoutParams lp=new RadioGroup.LayoutParams(-1,-2);lp.bottomMargin=dp(a,6);group.addView(row,lp);if(settings.style()==style)group.check(row.getId());
        }
        paintStyles(a,group);
        TextView choiceHint=text(a,"四种风格均可选择 · 单选",12);choiceHint.setTextColor(PinkTheme.MUTED);styles.addView(choiceHint);
        android.app.Dialog dialog=new android.app.Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.setContentView(shell);
        group.setOnCheckedChangeListener((g,id)->{
            if(!owner.equals(settings.account())){dialog.dismiss();return;}
            int index=id-100;if(index>=0&&index<Style.values().length)settings.style(Style.values()[index]);paintStyles(a,group);
        });
        LinearLayout controls=PinkTheme.card(a,"02  /  消息与回复");box.addView(controls);
        Switch transform=toggle(a,"发送前转换",settings.enabled());controls.addView(transform);
        Switch auto=toggle(a,"自动回复 · 跟随当前风格",settings.auto());controls.addView(auto);
        Switch atOnly=toggle(a,"群聊仅回复 @自己",settings.atOnly());controls.addView(atOnly);
        Switch poke=toggle(a,"戳一戳自动回复 · 跟随当前风格",settings.pokeReplies());controls.addView(poke);
        poke.setOnCheckedChangeListener((v,b)->{if(owner.equals(settings.account()))settings.flag("pokeReplies",b);});
        controls.addView(text(a,"自动回复开启后，回应别人戳你；沿用群白名单、好友私聊和黑名单设置。群消息引用原消息回复；戳一戳提示不可引用，群内改为 @戳你的人。",12));
        Switch skipActive=toggle(a,"当前聊天页面不自动回复",settings.skipActive());controls.addView(skipActive);
        skipActive.setOnCheckedChangeListener((v,b)->{if(owner.equals(settings.account()))settings.flag("skipActive",b);});
        controls.addView(text(a,"开启后，正在查看的群聊或好友会话交给你处理；离开该会话、后台或息屏时恢复自动回复。生成期间打开该会话也会取消发送。",12));
        transform.setOnCheckedChangeListener((v,b)->{if(owner.equals(settings.account()))settings.flag("enabled",b);});
        auto.setOnCheckedChangeListener((v,b)->{if(owner.equals(settings.account()))settings.flag("auto",b);});
        atOnly.setOnCheckedChangeListener((v,b)->{if(owner.equals(settings.account()))settings.flag("atOnly",b);});
        controls.addView(text(a,"关闭「仅回复 @自己」后启用智能插话：主动图片点评按 30% 概率放行，仍受点评冷却限制；明确提问、@自己及好友私聊不受概率限制。未点评的图片保留供后续追问。@其他人的消息不插话。",12));
        controls.addView(text(a,"/ 开头可跳过转换，发送时去掉首个 /。\n网页失败：手写消息发送原文，自动回复取消。",12));
        LinearLayout previewCard=PinkTheme.card(a,"03  /  试一句话");box.addView(previewCard);
        EditText preview=edit(a,"输入一句话预览，不会发送 QQ 消息","");previewCard.addView(preview);
        Button test=PinkTheme.button(a,"预览当前风格",true);previewCard.addView(test);
        TextView result=text(a,"转换使用 Yinglish Club 对应网页。",13);result.setTextIsSelectable(true);PinkTheme.space(result,10,0);previewCard.addView(result);
        test.setOnClickListener(v->{
            test.setEnabled(false);result.setText("正在转换…");
            web.translate(settings.style(),preview.getText().toString(),(out,error)->{test.setEnabled(true);result.setText(error==null?out:error);});
        });
        Button authorization=PinkTheme.button(a,"网页激活 / 查看授权",false);previewCard.addView(authorization);
        authorization.setOnClickListener(v->WebAccess.show(a,""));
        LinearLayout settingsCard=PinkTheme.card(a,"04  /  更多设置");box.addView(settingsCard);
        Button advanced=button(a,"黑名单与回复设置");settingsCard.addView(advanced);
        LinearLayout details=PinkTheme.column(a);details.setVisibility(View.GONE);settingsCard.addView(details);
        advanced.setOnClickListener(v->{boolean expand=details.getVisibility()!=View.VISIBLE;details.setVisibility(expand?View.VISIBLE:View.GONE);if(android.os.Build.VERSION.SDK_INT>=30)advanced.setStateDescription(expand?"已展开":"已收起");});
        Switch privateReplies=toggle(a,"允许好友私聊自动回复",settings.privateReplies());details.addView(privateReplies);
        privateReplies.setOnCheckedChangeListener((v,b)->{if(owner.equals(settings.account()))settings.flag("privateReplies",b);});
        details.addView(text(a,"全局黑名单 · QQ号 / 群号 / UID，空格或换行分隔",13));
        EditText blacklist=edit(a,"黑名单",settings.get("blacklist",""));details.addView(blacklist);
        Button chooseFriends=button(a,"勾选好友黑名单");details.addView(chooseFriends);chooseFriends.setEnabled(roster!=null);
        chooseFriends.setOnClickListener(v->ContactPicker.show(a,settings,false,blacklist.getText().toString(),roster,value->{blacklist.setText(value);settings.put("blacklist",value);Toast.makeText(a,"好友黑名单已保存",Toast.LENGTH_SHORT).show();}));
        details.addView(text(a,"自动回复群号 · 留空仅处理好友私聊",13));
        EditText groups=edit(a,"群号",settings.get("groups",""));details.addView(groups);
        Button chooseGroups=button(a,"勾选群聊白名单");details.addView(chooseGroups);chooseGroups.setEnabled(roster!=null);
        chooseGroups.setOnClickListener(v->ContactPicker.show(a,settings,true,groups.getText().toString(),roster,value->{groups.setText(value);settings.put("groups",value);Toast.makeText(a,"群聊白名单已保存",Toast.LENGTH_SHORT).show();}));
        if(roster==null)details.addView(text(a,"好友与群聊勾选列表请在 QQ 内打开。",12));
        details.addView(text(a,"未配置模型时使用此回复，再交给当前风格转换",13));
        EditText fallback=edit(a,"普通回复",settings.get("fallback","收到，稍后回复你。"));details.addView(fallback);
        details.addView(text(a,"回复生成 API 地址 · HTTPS",13));
        EditText endpoint=edit(a,"回复生成 API 地址（HTTPS）",settings.get("endpoint",io.github.ling.style.engine.ReplyClient.ENDPOINT));details.addView(endpoint);
        details.addView(text(a,"回复模型",13));EditText model=edit(a,"回复模型",settings.get("model",io.github.ling.style.engine.ReplyClient.MODEL));details.addView(model);
        details.addView(text(a,"API Key",13));EditText key=edit(a,"API Key",settings.get("key",""));key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);details.addView(key);
        Button save=PinkTheme.button(a,"保存设置",true);details.addView(save);
        save.setOnClickListener(v->{
            if(!owner.equals(settings.account())){dialog.dismiss();return;}
            String url=endpoint.getText().toString().trim();
            if(!url.startsWith("https://")){endpoint.setError("请填写 HTTPS 接口地址");return;}
            if(fallback.getText().toString().trim().isEmpty()){fallback.setError("请填写普通回复");return;}
            java.util.Map<String,String> values=new java.util.HashMap<>();
            values.put("blacklist",blacklist.getText().toString());values.put("groups",groups.getText().toString());
            values.put("fallback",fallback.getText().toString().trim());values.put("endpoint",url);
            values.put("model",model.getText().toString().trim());values.put("key",key.getText().toString().trim());settings.update(values);
            Toast.makeText(a,"已保存",Toast.LENGTH_SHORT).show();
        });
        TextView capability=text(a,replies.status,12);details.addView(capability);
        Button detect=button(a,"检测模型连接与上下文");details.addView(detect);
        detect.setOnClickListener(v->{
            Settings.Snapshot config=settings.snapshot();detect.setEnabled(false);capability.setText("正在检测已保存的模型设置…");
            new Thread(()->{String report;try{report=replies.testConnection(config.endpoint,config.model,config.key,stage->a.runOnUiThread(()->{if(dialog.isShowing()&&owner.equals(settings.account())&&config.revision==settings.revision.get())capability.setText(stage);}));}
                catch(Exception e){report="检测失败："+(e instanceof java.io.IOException?e.getMessage():e.getClass().getSimpleName());}
                final String value=report;a.runOnUiThread(()->{if(dialog.isShowing()&&owner.equals(settings.account())){detect.setEnabled(true);capability.setText(config.revision==settings.revision.get()?value:"设置已变化，请重新检测当前配置");}});
            },"LingStyle-model-check").start();
        });
        Button clear=button(a,"清空自动回复上下文");details.addView(clear);clear.setOnClickListener(v->{replies.clear();Toast.makeText(a,"已清空内存中的对话上下文",Toast.LENGTH_SHORT).show();});
        details.addView(text(a,"图像识别自动适配当前模型：每条最多 2 张图片，支持图片与文字一起理解；图片会压缩后发往已配置的模型接口。明确不支持图片时跳过，不编造画面。图片上下文按群及发言人隔离，只保存在内存，清空上下文时一并移除。",12));
        if(importLocal!=null){Button local=button(a,"导入本机迁移配置");details.addView(local);local.setOnClickListener(v->{if(owner.equals(settings.account())){dialog.dismiss();importLocal.run();}});}
        if(importOld!=null){Button restore=button(a,"导入旧脚本的 API、群号与黑名单");details.addView(restore);restore.setOnClickListener(v->{if(owner.equals(settings.account())){importOld.run();dialog.dismiss();}});}
        Switch diagnosticFile=toggle(a,"保存本机诊断（不含消息与密钥）",settings.diagnostic());details.addView(diagnosticFile);
        diagnosticFile.setOnCheckedChangeListener((v,b)->{if(owner.equals(settings.account()))settings.flag("diagnostic",b);});
        TextView diagnostic=text(a,status.get(),12);diagnostic.setPadding(dp(a,12),dp(a,12),dp(a,12),dp(a,12));diagnostic.setBackground(PinkTheme.shape(a,PinkTheme.SOFT,12,0));PinkTheme.space(diagnostic,16,0);details.addView(diagnostic);
        Button refresh=button(a,"刷新运行状态");details.addView(refresh);refresh.setOnClickListener(v->diagnostic.setText(status.get()));
        details.addView(text(a,"自动回复：每个会话发送后冷却 10 秒，每分钟最多判断 10 次。未 @时每群最多每 10 秒判断一次，自然点评发送后该群 60 秒不再点评；正常提问与 @自己仍可回答。先生成回答或点评，再由当前风格网页转换，网页结果原样发送，不做二次改写。",12));
        LinearLayout footer=PinkTheme.column(a);footer.setPadding(dp(a,18),0,dp(a,18),dp(a,14));shell.addView(footer);
        Button close=button(a,"关闭");footer.addView(close);close.setOnClickListener(v->dialog.dismiss());
        dialog.show();
        if(dialog.getWindow()!=null){Window w=dialog.getWindow();w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setDimAmount(.28f);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            android.util.DisplayMetrics metrics=a.getResources().getDisplayMetrics();w.setLayout(Math.min(metrics.widthPixels-dp(a,24),dp(a,440)),(int)(metrics.heightPixels*.88));}
        return dialog;
    }
    private static void paintStyles(Activity a,RadioGroup group){for(int i=0;i<group.getChildCount();i++){RadioButton row=(RadioButton)group.getChildAt(i);boolean selected=row.isChecked();row.setTextColor(!row.isEnabled()?0xFF998C93:selected?PinkTheme.DEEP:PinkTheme.INK);row.setTypeface(Typeface.DEFAULT,selected?Typeface.BOLD:Typeface.NORMAL);row.setBackground(PinkTheme.shape(a,!row.isEnabled()?0xFFF5F2F4:selected?PinkTheme.SOFT:0xFFFFFCFD,12,selected?PinkTheme.PINK:PinkTheme.LINE));}}
    static int dp(android.content.Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    static TextView text(Activity a,String s,int size){TextView t=PinkTheme.label(a,s,size,size<=13?PinkTheme.MUTED:PinkTheme.INK);t.setPadding(0,dp(a,5),0,dp(a,5));return t;}
    private static EditText edit(Activity a,String hint,String value){return PinkTheme.input(a,hint,value);}
    private static Button button(Activity a,String text){return PinkTheme.button(a,text,false);}
    private static Switch toggle(Activity a,String label,boolean checked){return PinkTheme.toggle(a,label,checked);}
}
