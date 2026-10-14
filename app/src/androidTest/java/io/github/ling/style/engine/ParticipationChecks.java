package io.github.ling.style.engine;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import org.json.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Synthetic prompts only. Does not load QQ or send messages. */
public final class ParticipationChecks {
    public static Bundle run(Context context){
        Bundle out=new Bundle();File config=new File(context.getExternalFilesDir(null),"LingVisionTest.json"),card=new File(context.getCacheDir(),"ling-participation.jpg");
        try{
            if(!ReplyClient.decision("{\"action\":\"silent\",\"text\":\"must never be sent\"}").getString("text").isEmpty())throw new AssertionError();
            for(String bad:new String[]{"{\"action\":true,\"text\":\"x\"}","{\"action\":\"answer\",\"text\":\"\"}","{\"action\":\"send_all\",\"text\":\"x\"}"}){boolean rejected=false;try{ReplyClient.decision(bad);}catch(Exception expected){rejected=true;}if(!rejected)throw new AssertionError();}
            out.putBoolean("decision_fail_closed",true);
            ReplyClient client=new ReplyClient();if(!client.reply("s","hello",Collections.emptyList(),"","","","fallback",true).silent())throw new AssertionError();out.putBoolean("no_model_no_intrusion",true);
            JSONObject cfg=new JSONObject(new String(Files.readAllBytes(config.toPath()),java.nio.charset.StandardCharsets.UTF_8));String endpoint=cfg.getString("endpoint"),model=cfg.getString("model"),key=cfg.getString("key");
            ReplyClient.Answer quiet=client.reply("synthetic:quiet","收到，谢谢。",Collections.emptyList(),endpoint,model,key,"",true);
            if(!quiet.silent())throw new AssertionError("ack should be quiet");client.commit(quiet,"never");
            java.lang.reflect.Field mem=ReplyClient.class.getDeclaredField("memory");mem.setAccessible(true);
            if(!((io.github.ling.style.core.ConversationMemory)mem.get(client)).snapshot(quiet.scope,10000).isEmpty())throw new AssertionError("silent commit");out.putBoolean("real_ack_silent_no_history_commit",true);
            ReplyClient.Answer question=client.reply("synthetic:question","请问群里有谁知道，三角形有几条边？",Collections.emptyList(),endpoint,model,key,"",true);
            if(!question.intent.equals("answer")||!(question.text.contains("三")||question.text.contains("3")))throw new AssertionError("public question");out.putBoolean("real_public_question_answered",true);
            ReplyClient.Answer direct=new ReplyClient.Answer("图片中是红色正方形和蓝色圆形。","","这个图片是什么内容？",10000,Collections.emptyList(),0);
            String aligned=client.alignStyle(direct,"主人主人，快看快看喵！我发现一张红色正方形和蓝色圆形的图片啦！（兴奋地摇尾巴）",endpoint,model,key);
            if(aligned.contains("快看")||aligned.contains("我发现")||!aligned.contains("红")||!aligned.contains("蓝"))throw new AssertionError("answer intent changed");out.putBoolean("real_style_preserves_answer_intent",true);out.putString("synthetic_answer",aligned);
            ReplyClient.Answer comment=new ReplyClient.Answer("这片晚霞的颜色真漂亮。","","分享今天的晚霞",10000,Collections.emptyList(),0,"comment");
            String natural=client.alignStyle(comment,"你问的图片内容是晚霞，答案是橙红色的天空喵。",endpoint,model,key);
            if(natural.contains("你问")||natural.contains("答案是")||natural.contains("图片内容是")||natural.contains("橙红"))throw new AssertionError("comment sounds like Q&A");out.putBoolean("real_comment_not_question_answer",true);out.putString("synthetic_comment",natural);
            Bitmap bitmap=Bitmap.createBitmap(512,384,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);Paint paint=new Paint();paint.setColor(Color.RED);canvas.drawRect(30,60,180,210,paint);paint.setColor(Color.BLUE);canvas.drawCircle(350,160,70,paint);
            try(OutputStream file=new FileOutputStream(card)){bitmap.compress(Bitmap.CompressFormat.JPEG,90,file);}finally{bitmap.recycle();}
            ReplyClient.Answer picture=client.reply("synthetic:share","随手画了张几何小图，发群里留个纪念。",Collections.singletonList(ImageCodec.encode(card)),endpoint,model,key,"",true);
            if(!picture.silent()&&!picture.intent.equals("comment"))throw new AssertionError("image share is not a question");out.putBoolean("real_image_share_comment_or_silent",true);out.putString("synthetic_image_action",picture.intent);out.putString("synthetic_image_text",picture.text);
            // Exercise the silent-image path regardless of the model's stochastic participation choice.
            ReplyClient.Answer silentImage=new ReplyClient.Answer("",picture.scope,"",picture.budget,picture.images,picture.generation,"silent");
            client.observeImage(silentImage);
            java.util.List<io.github.ling.style.core.ConversationMemory.Turn> history=((io.github.ling.style.core.ConversationMemory)mem.get(client)).snapshot(picture.scope,20000);
            if(history.size()!=1||!history.get(0).assistant.isEmpty()||history.get(0).images.size()!=1)throw new AssertionError("unanswered image history");
            out.putBoolean("silent_image_no_fictitious_reply",true);
            ReplyClient.Answer follow=client.reply("synthetic:share","刚才图片中的圆形是什么颜色？",Collections.emptyList(),endpoint,model,key,"");
            if(!follow.text.contains("蓝"))throw new AssertionError("silent image follow-up");out.putBoolean("real_silent_image_followup",true);out.putString("synthetic_followup",follow.text);
            client.clear();client.observeImage(silentImage);
            if(!((io.github.ling.style.core.ConversationMemory)mem.get(client)).snapshot(picture.scope,20000).isEmpty())throw new AssertionError("stale observation after clear");
            out.putBoolean("stale_observation_discarded",true);
            out.putBoolean("success",true);
        }catch(Throwable e){out.putString("failure",e.getClass().getSimpleName()+": "+(e instanceof IOException?e.getMessage():"participation check failed"));}
        finally{config.delete();card.delete();}
        return out;
    }
}
