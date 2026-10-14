package io.github.ling.style.hook;
import io.github.ling.style.core.*;
import java.util.*;

/** Pure JVM checks exercise the production reflection adapter, with QQ-shaped fixtures. */
public final class ReplyChecks {
    static int count;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);count++;System.out.println("PASS "+label);}
    static class Xml {Map<String,String> templParam=new HashMap<>();String content="戳了戳";}
    static class Json {long busiId=1061;String jsonStr="";}
    static class Gray {Xml xmlElement=new Xml();Json jsonGrayTipElement;}
    static class Element {int elementType=8;Gray grayTipElement=new Gray();long source;String text;}
    static class Record {List<Element> elements=new ArrayList<>();}
    static class Api {
        int quoteCalls,atCalls;
        public Element createTextElement(String text){Element e=new Element();e.elementType=1;e.text=text;return e;}
        public Element createReplyElement(long id){quoteCalls++;Element e=new Element();e.elementType=7;e.source=id;return e;}
        public Element createAtTextElement(String text,String uid,int type){atCalls++;return createTextElement(uid);}
    }
    public static void main(String[] args)throws Exception{
        int allowed=0;for(int roll=0;roll<100;roll++)if(Policy.allowImageComment(true,true,"comment",roll))allowed++;
        check(allowed==30,"30 of 100 equally likely outcomes allow an unsolicited image comment");
        check(Policy.allowImageComment(true,true,"comment",29)&&!Policy.allowImageComment(true,true,"comment",30),"probability boundary");
        for(int roll=0;roll<100;roll++){
            if(!Policy.allowImageComment(false,true,"comment",roll)||!Policy.allowImageComment(true,true,"answer",roll)
                ||!Policy.allowImageComment(true,false,"comment",roll))throw new AssertionError("unrelated reply sampled");
        }
        check(true,"private and mentioned replies, explicit answers and text comments are not sampled");
        Record record=new Record();Element e=new Element();record.elements.add(e);
        e.grayTipElement.xmlElement.templParam.put("uin_str1","20002");e.grayTipElement.xmlElement.templParam.put("uin_str2","10001");
        PokeEvent poke=PokeEvent.from(record);
        check(poke!=null&&poke.targets("10001")&&poke.actor.equals("20002"),"gray-tip operator replaces missing/system sender");
        check(!poke.targets("20002")&&!poke.targets("30003"),"own poke and pokes aimed at another person ignored");
        e.elementType=1;check(PokeEvent.from(record)==null,"ordinary text cannot spoof a poke");e.elementType=8;
        e.grayTipElement.xmlElement.content="撤回一条消息";check(PokeEvent.from(record)==null,"unrelated gray tip rejected");
        e.grayTipElement.xmlElement.content="";e.grayTipElement.xmlElement.templParam.put("action_str","拍了拍");
        check(PokeEvent.from(record)!=null,"structured action supports customized poke text");
        e.grayTipElement.xmlElement.templParam.remove("uin_str2");check(PokeEvent.from(record)==null,"unknown target never inferred from name/text");
        e.grayTipElement.xmlElement.templParam.put("uin_str2","10001");e.grayTipElement.xmlElement.templParam.put("uin_str1","0");
        check(PokeEvent.from(record)==null,"unknown actor rejected");
        check(PokeEvent.from(null)==null&&PokeEvent.from(new Record())==null,"malformed events fail closed");
        Policy.Seen seen=new Policy.Seen();check(seen.first("10001","2:group",123,1)&&!seen.first("10001","2:group",123,2),"poke receive/add/poll channels share message dedupe");
        Api api=new Api();
        ArrayList<Object> group=Host.autoElements(api,2,-987,null,"","reply");
        check(group.size()==2&&((Element)group.get(0)).elementType==7&&((Element)group.get(0)).source==-987&&((Element)group.get(1)).text.equals("reply"),"group reply quotes exact triggering message including signed IDs");
        check(api.quoteCalls==1&&api.atCalls==0,"ordinary group reply uses native quote once");
        check(Host.autoElements(api,1,55,null,"","private").size()==1&&Host.autoElements(api,100,55,null,"","private").size()==1&&api.quoteCalls==1,"both private chat types retain plain text");
        check(Host.autoElements(api,2,55,"u_actor","20002","poke").size()==2&&api.atCalls==1&&api.quoteCalls==1,"group poke addresses actor without quoting a system tip");
        check(Host.autoElements(api,1,55,"u_actor","20002","poke").size()==1&&api.atCalls==1,"private poke remains plain text");
        boolean rejected=false;try{Host.autoElements(api,2,0,null,"","bad");}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"missing group source fails instead of sending unquoted text");
        Api broken=new Api(){public Element createReplyElement(long id){return null;}};
        rejected=false;try{Host.autoElements(broken,2,55,null,"","bad");}catch(IllegalStateException expected){rejected=true;}
        check(rejected,"native quote failure does not fall back to an unquoted reply");
        if(args.length>0){
            e.grayTipElement.xmlElement=null;Json j=new Json();e.grayTipElement.jsonGrayTipElement=j;
            j.jsonStr="{\"items\":[{\"uid\":\"u_actor\"},{\"txt\":\"custom action\"},{\"uid\":\"u_self\"}]}";
            java.util.function.Function<String,String> resolver=uid->uid.equals("u_actor")?"20002":uid.equals("u_self")?"10001":"";
            check(PokeEvent.from(record,resolver).targets("10001"),"native JSON 1061 identifies actor and target regardless of custom action");
            check(!PokeEvent.from(record,resolver).targets("20002"),"JSON own poke rejected");
            j.busiId=51;check(PokeEvent.from(record,resolver)==null,"non-poke JSON business ID rejected");j.busiId=1061;
            check(PokeEvent.from(record,uid->"")==null,"unresolved JSON actor cannot reply");
            j.jsonStr="{\"items\":[{\"uid\":\"u_actor\"}]}";check(PokeEvent.from(record,resolver)==null,"JSON missing target rejected");
            j.jsonStr="{\"items\":[{\"uid\":\"u_actor\"},{\"uid\":\"u_self\"},{\"uid\":\"u_actor\"}]}";check(PokeEvent.from(record,resolver)==null,"ambiguous JSON identities rejected");
            j.jsonStr="broken";check(PokeEvent.from(record,resolver)==null,"malformed JSON ignored");
        }else System.out.println("SKIP JSON gray-tip checks: pass --json-jar with a JVM org.json implementation");
        System.out.println("TOTAL reply policy "+count);
    }
}
