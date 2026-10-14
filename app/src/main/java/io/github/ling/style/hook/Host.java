package io.github.ling.style.hook;

import java.lang.reflect.*;
import java.util.*;

/** Small, version-sensitive QQ adapter. No QFun dependency. */
public final class Host {
    final ClassLoader loader;
    public Host(ClassLoader loader){this.loader=loader;}
    public Class<?> type(String name)throws Exception{return Class.forName(name,false,loader);}
    public static Object call(Object receiver,String name,Object... args)throws Exception{
        Class<?> type=receiver instanceof Class?(Class<?>)receiver:receiver.getClass();
        for(Class<?> c=type;c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods()){
            if(!m.getName().equals(name)||m.getParameterTypes().length!=args.length)continue;
            Class<?>[] p=m.getParameterTypes();boolean match=true;
            for(int i=0;i<p.length;i++)if(args[i]!=null&&!boxed(p[i]).isInstance(args[i])){match=false;break;}
            if(!match)continue;m.setAccessible(true);return m.invoke(receiver instanceof Class?null:receiver,args);
        }
        throw new NoSuchMethodException(type.getName()+"."+name);
    }
    private static Class<?> boxed(Class<?> c){
        if(c==int.class)return Integer.class;if(c==long.class)return Long.class;if(c==boolean.class)return Boolean.class;
        if(c==byte.class)return Byte.class;if(c==short.class)return Short.class;return c;
    }
    public static Object field(Object receiver,String name)throws Exception{
        for(Class<?> c=receiver.getClass();c!=null;c=c.getSuperclass())try{
            Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(receiver);
        }catch(NoSuchFieldException ignored){}
        return call(receiver,"get"+Character.toUpperCase(name.charAt(0))+name.substring(1));
    }
    public static String text(Object o,String f){try{Object v=field(o,f);return v instanceof String||v instanceof Number?String.valueOf(v):"";}catch(Exception e){return "";}}
    public static void set(Object receiver,String name,Object value)throws Exception{
        for(Class<?> c=receiver.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);f.set(receiver,value);return;}catch(NoSuchFieldException ignored){}
        call(receiver,"set"+Character.toUpperCase(name.charAt(0))+name.substring(1),value);
    }
    public static long number(Object o,String f){try{Object v=field(o,f);return v instanceof Number?((Number)v).longValue():0;}catch(Exception e){return 0;}}
    Object runtime()throws Exception{return call(call(type("mqq.app.MobileQQ"),"getMobileQQ"),"peekAppRuntime");}
    public String account(){try{return String.valueOf(call(runtime(),"getCurrentAccountUin"));}catch(Exception e){return "unknown";}}
    public Object kernel()throws Exception{
        Object service=call(runtime(),"getRuntimeService",type("com.tencent.qqnt.kernel.api.IKernelService"),"");
        return call(call(service,"getMsgService"),"getService");
    }
    public Object route(String name)throws Exception{return call(type("com.tencent.mobileqq.qroute.QRoute"),"api",type(name));}
    public String uin(String uid){
        if(uid==null)return "";if(uid.matches("[1-9][0-9]+"))return uid;
        try{String value=String.valueOf(call(route("com.tencent.relation.common.api.IRelationNTUinAndUidApi"),"getUinFromUid",uid));return value.matches("[1-9][0-9]+")?value:"";}
        catch(Exception e){return "";}
    }
    public boolean friend(String uid){try{return Boolean.TRUE.equals(call(route("com.tencent.qqnt.ntrelation.friendsinfo.api.IFriendsInfoService"),"isFriend",uid,""));}catch(Exception e){return false;}}
    public Object contact(int type,String uid)throws Exception{return this.type("com.tencent.qqnt.kernelpublic.nativeinterface.Contact").getConstructor(int.class,String.class,String.class).newInstance(type,uid,"");}
    public ArrayList<Object> elements(String text)throws Exception{
        Object element=call(route("com.tencent.qqnt.msg.api.IMsgUtilApi"),"createTextElement",text);
        ArrayList<Object> out=new ArrayList<>();out.add(element);return out;
    }
    /** Only a single ordinary text element can be rewritten. Rich text/@/media stay intact. */
    public static String plain(Object raw){
        if(!(raw instanceof List))return null;List<?> list=(List<?>)raw;if(list.size()!=1)return null;
        try{
            Object e=list.get(0);if(number(e,"elementType")!=1)return null;
            Object text=field(e,"textElement");
            if(mention(text))return null;
            return text(text,"content");
        }catch(Exception e){return null;}
    }
    public static boolean mention(Object text){
        String uid=text(text,"atUid");
        // QQ 9.3.50 uses the literal "0" for ordinary text's absent atUid.
        return number(text,"atType")!=0||(!uid.isEmpty()&&!"0".equals(uid));
    }
    public static String shape(Object raw){
        if(!(raw instanceof List))return raw==null?"null":raw.getClass().getSimpleName();
        List<?> list=(List<?>)raw;if(list.size()!=1)return "elements="+list.size();
        Object e=list.get(0);
        try{Object text=field(e,"textElement");return "type="+number(e,"elementType")+", at="+number(text,"atType")+", uidEmpty="+text(text,"atUid").isEmpty();}
        catch(Exception ignored){return "textElement unavailable, type="+number(e,"elementType");}
    }
}
