package io.github.ling.style.engine;

import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Inject transport failures into the actual HTTP method; no external requests or credentials. */
public final class NetworkChecks {
    private static String mode="ok";
    private static int requests,posts,checks;
    private static HttpURLConnection last;
    private static final String ENDPOINT="https://network-check.invalid/v1/chat/completions";
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
    private static JSONObject request(JSONObject body)throws Exception{
        Method m=ReplyClient.class.getDeclaredMethod("request",String.class,String.class,JSONObject.class,int.class);m.setAccessible(true);
        try{return (JSONObject)m.invoke(null,body==null?"https://network-check.invalid/v1/models":ENDPOINT,"synthetic-secret",body,131072);}
        catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    private static void failure(String injected,String expected)throws Exception{
        mode=injected;int before=requests;IOException error=null;
        try{request(new JSONObject().put("model","test"));}catch(IOException e){error=e;}
        check(error!=null&&error.getMessage().contains(expected)&&!error.getMessage().contains("synthetic-secret")&&requests==before+1,injected+" reports safe stage without retry");
    }
    public static void main(String[] args)throws Exception{
        URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){
            protected URLConnection openConnection(URL url){
                if(!url.getHost().equals("network-check.invalid"))throw new AssertionError("unexpected host");
                requests++;final boolean metadata=url.getPath().endsWith("/models");if(!metadata)posts++;
                return last=new HttpURLConnection(url){
                    public void connect()throws IOException{
                        if(mode.equals("connect-timeout"))throw new SocketTimeoutException("timeout");
                        if(mode.equals("dns"))throw new UnknownHostException("synthetic-secret");
                        if(mode.equals("tls"))throw new javax.net.ssl.SSLHandshakeException("synthetic-secret");
                        if(mode.equals("connection"))throw new ConnectException("synthetic-secret");
                    }
                    public void disconnect(){}public boolean usingProxy(){return false;}
                    public OutputStream getOutputStream()throws IOException{
                        if(mode.equals("write-timeout"))throw new SocketTimeoutException("timeout");
                        return new ByteArrayOutputStream();
                    }
                    public int getResponseCode()throws IOException{
                        if(mode.equals("response-timeout")||(metadata&&mode.equals("metadata-timeout")))throw new SocketTimeoutException("timeout");
                        if(mode.equals("reset"))throw new SocketException("synthetic-secret");
                        return mode.equals("http401")?401:mode.equals("http429")?429:200;
                    }
                    public InputStream getErrorStream(){return new ByteArrayInputStream("{\"error\":\"synthetic-secret\"}".getBytes(StandardCharsets.UTF_8));}
                    public InputStream getInputStream(){
                        if(mode.equals("read-timeout"))return new InputStream(){public int read()throws IOException{throw new SocketTimeoutException("timeout");}};
                        String data=metadata?"{\"data\":[{\"id\":\"test\",\"context_length\":8192}]}":"{\"choices\":[{\"message\":{\"content\":\"连接成功\"},\"finish_reason\":\"stop\"}]}";
                        if(mode.equals("empty")&&!metadata)data="{\"choices\":[{\"message\":{\"content\":\"\"}}]}";
                        return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
                    }
                };
            }
        }:null);
        failure("connect-timeout","建立 HTTPS 连接");failure("write-timeout","提交请求");
        failure("response-timeout","等待响应");failure("read-timeout","读取响应");
        failure("dns","域名解析失败");failure("tls","HTTPS 握手/传输失败");
        failure("connection","连接失败");failure("reset","网络连接中断");
        failure("http401","HTTP 401");failure("http429","HTTP 429");
        mode="ok";request(new JSONObject());
        check(last.getConnectTimeout()==12000&&last.getReadTimeout()==45000,"generation timeout tolerates jitter without changing request count");
        request(null);check(last.getReadTimeout()==10000,"optional model metadata stays bounded at ten seconds");
        List<String> stages=new ArrayList<>();ReplyClient client=new ReplyClient();int beforePosts=posts;
        String report=client.testConnection(ENDPOINT,"test","synthetic-secret",stages::add);
        check(report.startsWith("连接成功")&&report.contains("8192")&&posts==beforePosts+1&&stages.size()==2,"diagnostic succeeds only after actual generation and reports local snapshot");
        java.lang.reflect.Field mem=ReplyClient.class.getDeclaredField("memory");mem.setAccessible(true);
        java.lang.reflect.Field sessions=io.github.ling.style.core.ConversationMemory.class.getDeclaredField("sessions");sessions.setAccessible(true);
        check(((Map<?,?>)sessions.get(mem.get(client))).isEmpty(),"diagnostic does not commit reply history");
        mode="metadata-timeout";report=new ReplyClient().testConnection(ENDPOINT,"test","synthetic-secret",s->{});
        check(report.startsWith("连接成功")&&report.contains("保守使用 8K"),"metadata timeout does not hide a successful generation");
        mode="empty";boolean failed=false;try{new ReplyClient().testConnection(ENDPOINT,"test","synthetic-secret",s->{});}catch(IOException expected){failed=true;}
        check(failed,"empty generation is never reported as connected");
        mode="ok";failed=false;beforePosts=posts;try{client.testConnection(ENDPOINT,"test","",s->{});}catch(IOException expected){failed=true;}
        check(failed&&posts==beforePosts,"missing key cannot report fallback as connectivity success");
        System.out.println("TOTAL network "+checks);
    }
}
