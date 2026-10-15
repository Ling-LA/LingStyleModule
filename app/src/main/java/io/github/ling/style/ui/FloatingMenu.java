package io.github.ling.style.ui;

import android.app.Activity;
import android.view.*;
import android.widget.*;
import io.github.ling.style.hook.ModuleRuntime;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.File;
import java.util.WeakHashMap;

/** Activity-local overlay; no system overlay permission, no UI in another app. */
public final class FloatingMenu {
    private final ModuleRuntime runtime;
    private final WeakHashMap<Activity,EdgeBubble> icons=new WeakHashMap<>();
    public FloatingMenu(ModuleRuntime runtime){this.runtime=runtime;}
    public void attach(Activity a){
        if(a.isFinishing()||icons.containsKey(a))return;
        View decor=a.getWindow().getDecorView();if(!(decor instanceof ViewGroup))return;
        EdgeBubble icon=new EdgeBubble(a);icons.put(a,icon);icon.mount((ViewGroup)decor);
        icon.setOnClickListener(v->{
            icon.setMenuOpen(true);
            android.app.Dialog dialog=ControlPanel.show(a,runtime.settings,runtime.translator,runtime::diagnostics,this::importOld,runtime.replies,()->importLocal(a),new io.github.ling.style.hook.ContactRoster(runtime.host)::load,runtime.faults::report);
            dialog.setOnDismissListener(d->icon.setMenuOpen(false));
        });
    }
    public void detach(Activity a){EdgeBubble icon=icons.remove(a);if(icon!=null)icon.dispose();}
    private void importLocal(Activity activity){
        try{
            String account=runtime.settings.account();if(!runtime.settings.known())throw new IllegalStateException("请先登录 QQ");
            File root=runtime.context.getExternalFilesDir(null);if(root==null)throw new IllegalStateException("目录不可用");
            File file=new File(root,"LingStyleImport.json");JSONObject data=read(file);
            if(!account.equals(runtime.host.account()))throw new IllegalStateException("账号已切换");
            if(!io.github.ling.style.engine.ReplyClient.ENDPOINT.equals(data.optString("endpoint")))throw new IllegalStateException("迁移仅支持 Rinko 官方接口");
            if(data.optString("key").trim().isEmpty())throw new IllegalStateException("缺少密钥");
            java.util.Map<String,String> values=new java.util.HashMap<>();
            for(String name:new String[]{"endpoint","model","key","groups","blacklist"})if(data.has(name))values.put(name,data.getString(name));
            runtime.settings.update(values);runtime.replies.clear();
            String license=data.optString("license","");
            boolean removed=file.delete();runtime.notice(removed?"本机配置已导入，迁移文件已移除":"已导入，请删除迁移文件");
            if(!license.isEmpty())WebAccess.show(activity,license);
        }catch(Exception e){runtime.notice("迁移失败："+e.getClass().getSimpleName());}
    }
    private void importOld(){
        try{
            String account=runtime.settings.account();if(!runtime.settings.known())throw new IllegalStateException("请先登录 QQ");
            File external=runtime.context.getExternalFilesDir(null);if(external==null)throw new IllegalStateException("无法访问旧配置目录");
            File qqDir=external.getParentFile();
            File dir=new File(qqDir,"QFun/"+account+"/plugin/LingCatGirl_Custom_V2/config");
            JSONObject auto=read(new File(dir,"auto_reply.json")),global=read(new File(dir,"global.json"));
            if(!account.equals(runtime.host.account())||!account.equals(runtime.settings.account()))throw new IllegalStateException("账号已切换");
            java.util.Map<String,String> values=new java.util.HashMap<>();
            values.put("key",auto.optString("回复_AI_API_KEY",""));values.put("model",auto.optString("回复_AI_模型",""));
            values.put("groups",auto.optString("回复_active_groups",""));values.put("blacklist",global.optString("好友黑名单",""));runtime.settings.update(values);
            if(auto.has("回复_当前聊天页不自动回复"))runtime.settings.flag("skipActive","开".equals(auto.optString("回复_当前聊天页不自动回复").trim()));
            if(auto.has("sw_戳一戳回复@global"))runtime.settings.flag("pokeReplies","开".equals(auto.optString("sw_戳一戳回复@global").trim()));
            runtime.notice("已导入，转换和自动回复开关保持原状");
        }catch(Exception e){runtime.notice("导入失败："+e.getClass().getSimpleName());}
    }
    private static JSONObject read(File file)throws Exception{
        if(!file.isFile()||file.length()>1048576)throw new IllegalStateException("旧配置不存在或过大");
        return new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
    }
}
