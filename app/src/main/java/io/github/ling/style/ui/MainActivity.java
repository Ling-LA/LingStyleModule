package io.github.ling.style.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import io.github.ling.style.engine.WebTranslator;
import io.github.ling.style.store.Settings;

public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(PinkTheme.SOFT);getWindow().setNavigationBarColor(PinkTheme.SOFT);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(PinkTheme.SOFT);setContentView(scroll);
        LinearLayout root=PinkTheme.column(this);root.setPadding(PinkTheme.dp(this,16),PinkTheme.dp(this,24),PinkTheme.dp(this,16),PinkTheme.dp(this,24));scroll.addView(root);
        root.addView(PinkTheme.header(this,"玲 · QQ 风格","猫娘语 / 俏皮吐槽 / 病娇 · 每次选择一种风格"));
        LinearLayout card=PinkTheme.card(this,"从 QQ 中打开");PinkTheme.space(card,16,12);root.addView(card);
        card.addView(ControlPanel.text(this,"启用模块后，在 QQ 内点击悬浮的「玲」图标，选择风格并管理转换与自动回复。",16));
        LinearLayout previewCard=PinkTheme.card(this,"先试试看");root.addView(previewCard);
        previewCard.addView(ControlPanel.text(this,"这里可以预览菜单和网页转换。预览设置独立保存，不会修改 QQ 内设置，也不会发送 QQ 消息。",14));
        Settings settings=new Settings(this);WebTranslator web=new WebTranslator(this);
        Button preview=PinkTheme.button(this,"打开控制菜单预览",true);previewCard.addView(preview);
        preview.setOnClickListener(v->ControlPanel.show(this,settings,web,()->"独立应用预览 · QQ 注入状态请在 QQ 内查看",null));
    }
}
