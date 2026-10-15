package io.github.ling.style.engine;

import android.content.Context;
import android.os.Bundle;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

/** Uses a temporary private configuration and a synthetic prompt; never sends QQ messages. */
public final class ConnectionChecks {
    public static Bundle run(Context context){
        Bundle result=new Bundle();File config=new File(context.getFilesDir(),"LingConnectionTest.json");
        try{
            JSONObject saved=new JSONObject(new String(Files.readAllBytes(config.toPath()),StandardCharsets.UTF_8));
            ReplyClient client=new ReplyClient();
            String report=client.testConnection(saved.getString("endpoint"),saved.getString("model"),saved.getString("key"),stage->result.putString("stage",stage));
            result.putString("report",report);result.putBoolean("success",report.startsWith("连接成功"));
        }catch(Exception e){result.putString("failure",e instanceof java.io.IOException?e.getMessage():e.getClass().getSimpleName());}
        finally{config.delete();}
        return result;
    }
}
