package io.github.ling.style.engine;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import org.json.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
/** Synthetic fixture only. Credentials are temporary device-local input, never reported. */
public final class VisionChecks {
    public static Bundle run(Context context){
        Bundle result=new Bundle();File config=new File(context.getExternalFilesDir(null),"LingVisionTest.json");File card=new File(context.getCacheDir(),"ling-vision-card.jpg");
        try{
            if(!Boolean.TRUE.equals(ReplyClient.visionMetadata(new JSONObject("{\"architecture\":{\"input_modalities\":[\"text\",\"image\"]}}"))))throw new AssertionError("metadata image");
            if(!Boolean.FALSE.equals(ReplyClient.visionMetadata(new JSONObject("{\"input_modalities\":[\"text\"]}"))))throw new AssertionError("metadata text only");
            if(ReplyClient.visionMetadata(new JSONObject("{\"id\":\"unknown-vision\"}"))!=null)throw new AssertionError("model name is not capability");
            result.putBoolean("metadata_three_states",true);
            Bitmap bitmap=Bitmap.createBitmap(768,512,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.RED);canvas.drawRect(40,120,210,290,paint);paint.setColor(Color.BLUE);canvas.drawCircle(400,205,85,paint);
            paint.setColor(Color.BLACK);paint.setTextSize(72);canvas.drawText("5827",420,440,paint);
            try(OutputStream out=new FileOutputStream(card)){bitmap.compress(Bitmap.CompressFormat.JPEG,90,out);}finally{bitmap.recycle();}
            String data=ImageCodec.encode(card);List<String> pictures=Collections.singletonList(data);
            try(RandomAccessFile padded=new RandomAccessFile(card,"rw")){padded.setLength(13L*1024*1024);}
            String large=ImageCodec.encode(card);if(large.length()>1100000)throw new AssertionError("large file must be recompressed");
            result.putBoolean("large_file_bounded_reencode",true);
            JSONObject msg=ReplyClient.message("user","describe",pictures);JSONArray parts=msg.getJSONArray("content");
            if(parts.length()!=2||!parts.getJSONObject(1).getJSONObject("image_url").getString("url").startsWith("data:image/jpeg;base64,"))throw new AssertionError("image payload");
            if(!(ReplyClient.message("user","text",Collections.emptyList()).get("content") instanceof String))throw new AssertionError("text request compatibility");
            result.putBoolean("codec_and_payload",true);
            JSONObject cfg=new JSONObject(new String(Files.readAllBytes(config.toPath()),java.nio.charset.StandardCharsets.UTF_8));
            ReplyClient client=new ReplyClient();ReplyClient.Answer first=client.reply("synthetic:A","描述图片中的两个彩色图形，并读出黑色数字。",pictures,cfg.getString("endpoint"),cfg.getString("model"),cfg.getString("key"),"");
            boolean recognized=first.text.contains("5827")&&first.text.contains("红")&&first.text.contains("蓝");result.putBoolean("real_image_recognition",recognized);result.putString("synthetic_image_answer",first.text);
            client.commit(first,first.text);
            client.unreadImage("synthetic:B",cfg.getString("endpoint"),cfg.getString("model"),cfg.getString("key"));
            java.lang.reflect.Field unread=ReplyClient.class.getDeclaredField("unreadImages");unread.setAccessible(true);
            if(((Set<?>)unread.get(client)).size()!=1)throw new AssertionError("unread image marker");
            result.putBoolean("failed_image_sender_marker",true);
            ReplyClient.Answer second=client.reply("synthetic:A","上一张图的黑色数字是多少？",cfg.getString("endpoint"),cfg.getString("model"),cfg.getString("key"),"");
            result.putBoolean("real_image_follow_up",second.text.contains("5827"));result.putString("synthetic_follow_up",second.text);
            client.clear();client.commit(second,second.text);
            if(!((Set<?>)unread.get(client)).isEmpty())throw new AssertionError("clear image failure markers");
            java.lang.reflect.Field f=ReplyClient.class.getDeclaredField("memory");f.setAccessible(true);
            io.github.ling.style.core.ConversationMemory memory=(io.github.ling.style.core.ConversationMemory)f.get(client);
            if(!memory.snapshot(second.scope,100000).isEmpty())throw new AssertionError("late callback restored cleared history");
            result.putBoolean("late_commit_after_clear_rejected",true);
            result.putBoolean("success",recognized&&second.text.contains("5827"));
        }catch(Throwable e){result.putString("failure",e.getClass().getSimpleName()+": "+(e instanceof IOException?e.getMessage():"vision check failed"));}
        finally{card.delete();config.delete();}
        return result;
    }
}
