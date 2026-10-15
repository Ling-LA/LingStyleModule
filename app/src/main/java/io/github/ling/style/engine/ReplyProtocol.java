package io.github.ling.style.engine;

import io.github.ling.style.core.*;
import org.json.*;
import java.util.Locale;

/** Accept only complete objects. Never salvage a partial decision or send unparsed model text. */
public final class ReplyProtocol {
    private ReplyProtocol(){}
    private static String clean(String s){s=s==null?"":s.trim();if(s.startsWith("\uFEFF"))s=s.substring(1).trim();return s;}
    private static JSONObject object(String text,FaultKind kind,String prefix,String description)throws ReplyFault{
        try{
            JSONTokener tokens=new JSONTokener(text);Object value=tokens.nextValue();
            if(!(value instanceof JSONObject)||tokens.nextClean()!=0)throw new JSONException("not one object");
            return (JSONObject)value;
        }catch(JSONException e){
            String shape=text.isEmpty()?"empty":text.startsWith("<")?"html":text.startsWith("[")?"array":text.startsWith("{")?"object":"text";
            throw new ReplyFault(kind,prefix+".syntax."+shape,description+"不是完整 JSON 对象，已取消本次回复");
        }
    }
    public static JSONObject response(String body,boolean metadata)throws ReplyFault{
        return object(clean(body),FaultKind.RESPONSE_JSON,metadata?"metadata":"response",metadata?"模型信息响应":"模型接口响应");
    }
    public static String content(JSONObject response)throws ReplyFault{
        JSONArray choices=response.optJSONArray("choices");
        JSONObject choice=choices==null?null:choices.optJSONObject(0);
        if(choice==null)throw new ReplyFault(FaultKind.RESPONSE_SCHEMA,"response.choices","模型接口缺少有效 choices，已取消回复");
        JSONObject message=choice.optJSONObject("message");Object content=message==null?null:message.opt("content");
        if(!(content instanceof String))throw new ReplyFault(FaultKind.RESPONSE_SCHEMA,"response.content_type","模型接口 message.content 不是文本，已取消回复");
        String text=((String)content).trim();
        if("length".equals(choice.optString("finish_reason")))throw new ReplyFault(FaultKind.MODEL_OUTPUT,"output.truncated","模型返回截断结果，已取消回复");
        if(text.isEmpty()||text.length()>2000)throw new ReplyFault(FaultKind.MODEL_OUTPUT,text.isEmpty()?"output.empty":"output.oversized","模型返回空内容或过长结果，已取消回复");
        return text;
    }
    public static JSONObject decision(String result)throws ReplyFault{
        String text=clean(result);
        if(text.startsWith("```")){
            if(!text.endsWith("```"))throw new ReplyFault(FaultKind.DECISION_JSON,"decision.fence","插话判断代码块格式无效，已保持安静");
            // Keep the legacy inline ```json{...}``` form as well as normal fenced blocks.
            if(text.regionMatches(true,3,"json",0,4)&&clean(text.substring(7,text.length()-3)).startsWith("{"))text=clean(text.substring(7,text.length()-3));
            else{
                int newline=text.indexOf('\n');String tag=newline<0?"invalid":text.substring(3,newline).trim().toLowerCase(Locale.ROOT);
                if(newline<0||(!tag.isEmpty()&&!tag.equals("json")))throw new ReplyFault(FaultKind.DECISION_JSON,"decision.fence","插话判断代码块格式无效，已保持安静");
                text=clean(text.substring(newline+1,text.length()-3));
            }
        }
        JSONObject value=object(text,FaultKind.DECISION_JSON,"decision","模型插话判断");
        Object action=value.opt("action"),content=value.opt("text");
        if(!(action instanceof String)||!("answer".equals(action)||"comment".equals(action)||"silent".equals(action)))throw new ReplyFault(FaultKind.DECISION_SCHEMA,"decision.action","插话判断 action 无效，已保持安静");
        // An explicit silent decision may omit its unused text; it can never become a reply.
        if("silent".equals(action)&&(content==null||content==JSONObject.NULL))content="";
        if(!(content instanceof String))throw new ReplyFault(FaultKind.DECISION_SCHEMA,"decision.text_type","插话判断 text 不是文本，已保持安静");
        String reply=((String)content).trim();
        if("silent".equals(action))reply="";
        else if(reply.isEmpty()||reply.length()>400)throw new ReplyFault(FaultKind.DECISION_SCHEMA,"decision.text_length","插话内容为空或过长，已保持安静");
        try{return new JSONObject().put("action",action).put("text",reply);}
        catch(JSONException impossible){throw new ReplyFault(FaultKind.DECISION_SCHEMA,"decision.build","插话判断构造失败");}
    }
}
