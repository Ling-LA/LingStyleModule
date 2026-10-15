package io.github.ling.style.engine;

import io.github.ling.style.core.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public final class FaultChecks {
    private static int checks;
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
    interface Attempt {void run()throws Exception;}
    private static void rejects(Attempt a,FaultKind kind,String code)throws Exception{
        ReplyFault error=null;try{a.run();}catch(ReplyFault e){error=e;}
        check(error!=null&&error.kind==kind&&error.code.startsWith(code)&&!error.getMessage().contains("private-sample"),code);
    }
    public static void main(String[] args)throws Exception{
        String decision="{\"action\":\"answer\",\"text\":\"测试答复\"}";
        for(String wrapper:new String[]{decision,"\uFEFF "+decision,"```json\n"+decision+"\n```","```JSON\r\n"+decision+"\r\n```","```\n"+decision+"\n```","```json"+decision+"```"})check(ReplyProtocol.decision(wrapper).getString("text").equals("测试答复"),"complete decision wrapper");
        check(ReplyProtocol.decision("{\"action\":\"silent\"}").getString("text").isEmpty(),"silent may omit unused text");
        check(ReplyProtocol.decision("{\"action\":\"silent\",\"text\":null}").getString("text").isEmpty(),"silent null text never sends");
        rejects(()->ReplyProtocol.decision("private-sample"),FaultKind.DECISION_JSON,"decision.syntax.text");
        rejects(()->ReplyProtocol.decision(decision+" trailing-private-sample"),FaultKind.DECISION_JSON,"decision.syntax");
        rejects(()->ReplyProtocol.decision(decision+decision),FaultKind.DECISION_JSON,"decision.syntax");
        rejects(()->ReplyProtocol.decision("```python\n"+decision+"\n```"),FaultKind.DECISION_JSON,"decision.fence");
        rejects(()->ReplyProtocol.decision("```json\n"+decision),FaultKind.DECISION_JSON,"decision.fence");
        rejects(()->ReplyProtocol.decision("{\"action\":\"answer\",\"text\":\"private-sample"),FaultKind.DECISION_JSON,"decision.syntax.object");
        rejects(()->ReplyProtocol.decision("{\"action\":true,\"text\":\"x\"}"),FaultKind.DECISION_SCHEMA,"decision.action");
        rejects(()->ReplyProtocol.decision("{\"action\":\"send\",\"text\":\"x\"}"),FaultKind.DECISION_SCHEMA,"decision.action");
        rejects(()->ReplyProtocol.decision("{\"action\":\"answer\",\"text\":{}}"),FaultKind.DECISION_SCHEMA,"decision.text_type");
        rejects(()->ReplyProtocol.decision("{\"action\":\"answer\",\"text\":\"\"}"),FaultKind.DECISION_SCHEMA,"decision.text_length");
        rejects(()->ReplyProtocol.response("<html>private-sample</html>",false),FaultKind.RESPONSE_JSON,"response.syntax.html");
        rejects(()->ReplyProtocol.response("[]",false),FaultKind.RESPONSE_JSON,"response.syntax.array");
        rejects(()->ReplyProtocol.response("",true),FaultKind.RESPONSE_JSON,"metadata.syntax.empty");
        rejects(()->ReplyProtocol.content(new JSONObject("{\"choices\":[]}")),FaultKind.RESPONSE_SCHEMA,"response.choices");
        rejects(()->ReplyProtocol.content(new JSONObject("{\"choices\":[{\"message\":{\"content\":42}}]}")),FaultKind.RESPONSE_SCHEMA,"response.content_type");
        rejects(()->ReplyProtocol.content(new JSONObject("{\"choices\":[{\"message\":{\"content\":\"\"}}]}")),FaultKind.MODEL_OUTPUT,"output.empty");
        rejects(()->ReplyProtocol.content(new JSONObject("{\"choices\":[{\"message\":{\"content\":\"x\"},\"finish_reason\":\"length\"}]}")),FaultKind.MODEL_OUTPUT,"output.truncated");
        check(ReplyProtocol.content(ReplyProtocol.response("\uFEFF{\"choices\":[{\"message\":{\"content\":\"正常回复\"}}]}",false)).equals("正常回复"),"valid response keeps generation text");
        check(QuotedText.context("当前","").equals("当前"),"no quote leaves model input unchanged");
        check(QuotedText.context("回复问题","原始文字").contains("引用消息（对话内容）：\n原始文字"),"quoted text is user context");
        check(QuotedText.context("x",String.join("",Collections.nCopies(3000,"字"))).length()<2100,"quoted text bounded");
        FaultHistory history=new FaultHistory();ExecutorService pool=Executors.newFixedThreadPool(2);
        for(int i=0;i<500;i++)pool.submit(()->history.add(System.currentTimeMillis(),FaultKind.DECISION_JSON,Style.YANDERE,"decision.syntax.object"));
        pool.shutdown();check(pool.awaitTermination(3,TimeUnit.SECONDS)&&history.errors()==500&&history.entries().size()==200,"concurrent history bounded without losing totals");
        history.add(System.currentTimeMillis(),FaultKind.CANCELLED,Style.CAT,"web.config_changed");
        check(history.errors()==500&&history.count(FaultKind.CANCELLED)==1,"expected cancellation separate from errors");
        FaultHistory restored=FaultHistory.decode(history.encode());
        check(restored.encode().equals(history.encode())&&restored.errors()==500,"history round trip retains counts and entries");
        restored.add(System.currentTimeMillis(),FaultKind.QUOTE,Style.LEWD,"raw private-sample\ntext");
        check(!restored.encode().contains("private-sample")&&restored.report().contains("引用读取"),"unsafe details never persist");
        boolean bad=false;try{FaultHistory.decode(history.encode().replace("LINGFAULT1","BAD"));}catch(IllegalArgumentException e){bad=true;}
        check(bad,"corrupt history rejected");
        check(FaultKind.fromMessage("配置已变化，取消旧转换")==FaultKind.CANCELLED&&FaultKind.fromMessage("网页转换超时")==FaultKind.WEB_TIMEOUT&&FaultKind.fromMessage("网页免费额度已用完")==FaultKind.WEB_QUOTA,"web errors classified");
        System.out.println("TOTAL faults "+checks);
    }
}
