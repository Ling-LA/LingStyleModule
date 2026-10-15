package io.github.ling.style.engine;

import io.github.ling.style.core.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Offline regression: real ReplyClient, intercepted HTTPS, synthetic webpage output, no QQ sends. */
public final class StyleOutputChecks {
    private static int checks,modelRequests,metadataRequests;
    private static String response="收到，稍后回复你。";
    private static void check(boolean ok,String title){if(!ok)throw new AssertionError(title);checks++;System.out.println("PASS "+title);}
    private static void rejects(ReplyClient client,ReplyClient.Answer answer,String result,String title)throws Exception{
        boolean rejected=false;try{client.finishStyle(answer,result);}catch(IOException expected){rejected=true;}
        check(rejected,title);
    }
    public static void main(String[] args)throws Exception{
        URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){
            protected URLConnection openConnection(URL url){
                if(!"style-regression.invalid".equals(url.getHost()))throw new AssertionError("unexpected endpoint");
                return new HttpURLConnection(url){
                    public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
                    public OutputStream getOutputStream(){return new ByteArrayOutputStream();}
                    public int getResponseCode(){return 200;}
                    public InputStream getInputStream(){
                        try{
                            String body;
                            if(url.getPath().endsWith("/models")){
                                metadataRequests++;body="{\"data\":[{\"id\":\"synthetic-model\",\"context_length\":8192}]}";
                            }else{
                                modelRequests++;body=new JSONObject().put("choices",new JSONArray().put(new JSONObject()
                                    .put("finish_reason","stop").put("message",new JSONObject().put("content",response)))).toString();
                            }
                            return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
                        }catch(JSONException e){throw new AssertionError(e);}
                    }
                };
            }
        }:null);
        ReplyClient client=new ReplyClient();String endpoint="https://style-regression.invalid/v1/chat/completions";
        String webpage="收到啦，稍后回复你喵～";
        ReplyClient.Answer fallback=client.reply("fallback","你好",endpoint,"synthetic-model","","收到，稍后回复你。");
        check(webpage.equals(client.finishStyle(fallback,webpage)),"missing key retains styled fallback instead of neutral original");
        ReplyClient.Answer noModel=client.reply("no-model","你好",endpoint,"","synthetic-key","收到。");
        check(webpage.equals(client.finishStyle(noModel,webpage)),"missing model retains webpage output");
        check(modelRequests==0&&metadataRequests==0,"fallback finalization makes no network requests");
        ReplyClient.Answer configured=client.reply("configured","你好",endpoint,"synthetic-model","synthetic-key","");
        check(modelRequests==1&&metadataRequests==1,"configured reply makes one generation request plus initial metadata lookup");
        String[] samples={"  收到啦，稍后回复你喵～\n","哼，这就记住啦～","我会记得的，一直记得。","〔第四风格网页原文〕"};
        for(Style style:Style.values()){
            String candidate=samples[style.ordinal()];
            check(candidate.equals(client.finishStyle(configured,candidate)),"preserve exact "+style.name()+" webpage fixture including formatting");
        }
        response="{\"action\":\"comment\",\"text\":\"这片晚霞很漂亮。\"}";
        ReplyClient.Answer comment=client.reply("comment","今天的晚霞",Collections.emptyList(),endpoint,"synthetic-model","synthetic-key","",true);
        check("comment".equals(comment.intent)&&samples[1].equals(client.finishStyle(comment,samples[1])),"smart comment uses the same style-preserving finalization");
        response="怎么啦？";
        ReplyClient.Answer poke=client.reply("poke","对方刚刚戳了你一下",endpoint,"synthetic-model","synthetic-key","");
        check(samples[2].equals(client.finishStyle(poke,samples[2])),"poke response retains webpage output");
        check(modelRequests==3&&metadataRequests==1,"no post-style model calls and metadata cache reused");
        client.commit(configured,webpage);
        java.lang.reflect.Field memory=ReplyClient.class.getDeclaredField("memory");memory.setAccessible(true);
        List<ConversationMemory.Turn> turns=((ConversationMemory)memory.get(client)).snapshot(configured.scope,8192);
        check(turns.size()==1&&webpage.equals(turns.get(0).assistant),"history stores exactly the final sent style");
        rejects(client,configured,null,"null webpage output cancels instead of falling back");
        rejects(client,configured," \n\t","blank webpage output cancels instead of falling back");
        rejects(client,configured,String.join("",Collections.nCopies(8001,"字")),"oversized output cancels without truncating style");
        response="{\"action\":\"silent\",\"text\":\"\"}";
        ReplyClient.Answer silent=client.reply("silent","收到",Collections.emptyList(),endpoint,"synthetic-model","synthetic-key","",true);
        rejects(client,silent,webpage,"silent decision cannot become a styled send");
        check(modelRequests==4,"validations do not trigger any model request");
        System.out.println("TOTAL style output "+checks);
    }
}
