package io.github.ling.style.hook;

import java.util.*;

/** QQNT gray-tip identity fields, also used by the original script. No text-message matching. */
public final class PokeEvent {
    public final String actor,target;
    private PokeEvent(String actor,String target){this.actor=actor;this.target=target;}
    public boolean targets(String account){return target.equals(account)&&!actor.equals(account);}
    public static PokeEvent from(Object record){
        return from(record,value->value);
    }
    public static PokeEvent from(Object record,java.util.function.Function<String,String> resolveUin){
        try{
            Object raw=Host.field(record,"elements");
            if(!(raw instanceof List)||((List<?>)raw).size()!=1)return null;
            Object element=((List<?>)raw).get(0);
            if(Host.number(element,"elementType")!=8)return null;
            Object gray=Host.field(element,"grayTipElement");
            Object json=null;try{json=Host.field(gray,"jsonGrayTipElement");}catch(Exception ignored){}
            if(json!=null&&Host.number(json,"busiId")==1061)return json(json,resolveUin);
            Object xml=Host.field(gray,"xmlElement");
            Object params=Host.field(xml,"templParam");
            if(!(params instanceof Map))return null;
            Map<?,?> map=(Map<?,?>)params;
            String actor=id(map.get("uin_str1")),target=id(map.get("uin_str2"));
            if(actor.isEmpty()||target.isEmpty())return null;
            String evidence=Host.text(xml,"content")+" "+map.get("action_str")+" "+map.get("alt_str1");
            evidence=evidence.toLowerCase(Locale.ROOT);
            if(!evidence.contains("戳")&&!evidence.contains("拍一拍")&&!evidence.contains("拍了拍")
                &&!evidence.contains("poke")&&!evidence.contains("nudge"))return null;
            return new PokeEvent(actor,target);
        }catch(Exception ignored){return null;}
    }
    /** Native JSON gray tips identify actor and target by the two ordered UID items. */
    private static PokeEvent json(Object tip,java.util.function.Function<String,String> resolveUin)throws Exception{
        String text=Host.text(tip,"jsonStr");if(text.isEmpty()||text.length()>16384)return null;
        // Android supplies org.json; reflection keeps the XML adapter usable in standalone JVM checks.
        Object object=Class.forName("org.json.JSONObject").getConstructor(String.class).newInstance(text);
        Object items=Host.call(object,"getJSONArray","items");int length=((Number)Host.call(items,"length")).intValue();
        List<String> identities=new ArrayList<>();
        for(int i=0;i<length;i++){
            Object item=Host.call(items,"getJSONObject",i);String uid=(String)Host.call(item,"optString","uid");
            if(uid.isEmpty())continue;
            String uin=id(resolveUin.apply(uid));if(uin.isEmpty())return null;
            identities.add(uin);if(identities.size()>2)return null;
        }
        return identities.size()==2?new PokeEvent(identities.get(0),identities.get(1)):null;
    }
    private static String id(Object value){return value instanceof String&&((String)value).matches("[1-9][0-9]+")?(String)value:"";}
}
