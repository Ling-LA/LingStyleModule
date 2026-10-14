package io.github.ling.style.ui;
import android.app.*;
import android.content.res.ColorStateList;
import android.text.*;
import android.view.*;
import android.widget.*;
import io.github.ling.style.core.*;
import io.github.ling.style.store.Settings;
import java.util.*;
import java.util.function.Consumer;

final class ContactPicker {
    interface Source {List<ContactChoice> load(boolean groups,String account)throws Exception;}
    static void show(Activity a,Settings settings,boolean groups,String current,Source source,Consumer<String> saved){
        String owner=settings.account();Set<String> before=Policy.blacklist(current),checked=new HashSet<>();List<ContactChoice> rows=new ArrayList<>(),visible=new ArrayList<>();
        Dialog dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell=PinkTheme.column(a);shell.setBackground(PinkTheme.shape(a,PinkTheme.SOFT,24,0));
        shell.addView(PinkTheme.header(a,groups?"群聊白名单":"好友黑名单",groups?"勾选允许自动回复的群聊":"勾选后加入全局黑名单，群聊与私聊均跳过该好友"));
        LinearLayout content=PinkTheme.column(a);content.setPadding(PinkTheme.dp(a,16),0,PinkTheme.dp(a,16),PinkTheme.dp(a,14));shell.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        EditText search=PinkTheme.input(a,"搜索名称或号码","");search.setSingleLine(true);content.addView(search);
        TextView count=PinkTheme.label(a,"正在读取 QQ 列表…",12,PinkTheme.MUTED);content.addView(count);
        ListView list=new ListView(PinkTheme.controls(a));list.setDividerHeight(0);content.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        Button confirm=PinkTheme.button(a,"保存勾选",true),cancel=PinkTheme.button(a,"取消",false);content.addView(confirm);content.addView(cancel);confirm.setEnabled(false);
        BaseAdapter adapter=new BaseAdapter(){
            public int getCount(){return visible.size();}public Object getItem(int pos){return visible.get(pos);}public long getItemId(int pos){return pos;}
            public View getView(int pos,View old,ViewGroup parent){ContactChoice row=visible.get(pos);CheckBox box=old instanceof CheckBox?(CheckBox)old:new CheckBox(PinkTheme.controls(a));box.setOnCheckedChangeListener(null);box.setText(row.name+"\n"+row.id);box.setTextColor(PinkTheme.INK);box.setTextSize(14);box.setMinHeight(PinkTheme.dp(a,64));box.setPadding(PinkTheme.dp(a,8),PinkTheme.dp(a,8),PinkTheme.dp(a,8),PinkTheme.dp(a,8));box.setButtonTintList(ColorStateList.valueOf(PinkTheme.DEEP));box.setChecked(checked.contains(row.id));box.setOnCheckedChangeListener((v,on)->{if(on)checked.add(row.id);else checked.remove(row.id);count.setText("已选 "+checked.size()+" 项 · 列表 "+rows.size()+" 项");});return box;}
        };list.setAdapter(adapter);
        Runnable filter=()->{String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);visible.clear();for(ContactChoice row:rows)if(row.id.contains(q)||row.name.toLowerCase(Locale.ROOT).contains(q))visible.add(row);adapter.notifyDataSetChanged();};
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int after){}public void onTextChanged(CharSequence s,int st,int before,int c){filter.run();}public void afterTextChanged(Editable e){}});
        confirm.setOnClickListener(v->{if(!owner.equals(settings.account())){dialog.dismiss();return;}String value=String.join("\n",ContactChoice.apply(before,rows,checked));saved.accept(value);dialog.dismiss();});cancel.setOnClickListener(v->dialog.dismiss());
        dialog.setContentView(shell);dialog.show();if(dialog.getWindow()!=null){Window w=dialog.getWindow();w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(Math.min(a.getResources().getDisplayMetrics().widthPixels-PinkTheme.dp(a,24),PinkTheme.dp(a,440)),(int)(a.getResources().getDisplayMetrics().heightPixels*.85));w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
        new Thread(()->{
            List<ContactChoice> loaded;String error=null;try{loaded=source.load(groups,owner);}catch(Exception e){loaded=Collections.emptyList();error=e instanceof java.io.IOException?e.getMessage():"暂时无法读取 QQ 列表，可继续手动填写号码";}
            List<ContactChoice> result=loaded;String failure=error;a.runOnUiThread(()->{
                if(!dialog.isShowing()||!owner.equals(settings.account())){dialog.dismiss();return;}
                rows.addAll(result);for(ContactChoice row:rows)if(row.selected(before))checked.add(row.id);filter.run();
                count.setText(failure!=null?failure:"已选 "+checked.size()+" 项 · 列表 "+rows.size()+" 项；未列出的手动条目会保留");confirm.setEnabled(failure==null);
            });
        },"LingStyle-roster").start();
    }
}
