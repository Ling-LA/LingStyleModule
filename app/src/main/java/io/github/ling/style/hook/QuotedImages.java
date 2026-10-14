package io.github.ling.style.hook;
import io.github.ling.style.core.*;
import io.github.ling.style.store.Settings;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Resolves only the explicitly quoted record in the current conversation. */
public final class QuotedImages {
    private final Host host;
    public QuotedImages(Host h){host=h;}
    public static final class Source {
        public final long message;
        public final List<IncomingImages.Picture> pictures;
        Source(long id,List<IncomingImages.Picture> pics){message=id;pictures=Collections.unmodifiableList(new ArrayList<>(pics));}
    }
    public static QuoteRef snapshot(Object element)throws Exception{
        Object r=Host.field(element,"replyElement");
        return new QuoteRef(Host.number(r,"sourceMsgIdInRecords"),Host.number(r,"replayMsgId"),Host.number(r,"replayMsgSeq"),
            Boolean.TRUE.equals(Host.field(r,"sourceMsgExpired")),Boolean.TRUE.equals(Host.field(r,"sourceMsgIsIncPic")));
    }
    public Source resolve(Settings.Snapshot config,int type,String peer,long current,QuoteRef ref)throws Exception{
        if(!ref.valid())throw new IOException("引用图片已过期或缺少消息标识");
        // QQ's AIOReplyComponent resolves the source first, then downloads its element
        // through the enclosing reply message. Source records may have synthetic IDs.
        Source source=ref.sequence>0?query(config,type,peer,current,ref,true):null;
        if(source==null||source.pictures.isEmpty()){
            Source fetched=query(config,type,peer,current,ref,false);
            if(fetched!=null)source=fetched;
        }
        if(source==null)throw new IOException("QQ 未能读取被引用的图片，请重新发送图片");
        if(source.pictures.isEmpty())throw new IOException("引用的消息没有可用的普通图片");
        return source;
    }
    private Source query(Settings.Snapshot config,int type,String peer,long current,QuoteRef ref,boolean fetch)throws Exception{
        ArrayList<Long> ids=new ArrayList<>();if(ref.storedId!=0)ids.add(ref.storedId);if(ref.replayId!=0&&!ids.contains(ref.replayId))ids.add(ref.replayId);
        if(!fetch&&ids.isEmpty())return null;
        CountDownLatch done=new CountDownLatch(1);AtomicReference<Source> result=new AtomicReference<>();AtomicReference<Exception> failure=new AtomicReference<>();
        Class<?> api=host.type("com.tencent.qqnt.kernel.nativeinterface.IMsgOperateCallback");
        Object callback=Proxy.newProxyInstance(api.getClassLoader(),new Class[]{api},(p,m,args)->{
            if(m.getName().equals("hashCode"))return System.identityHashCode(p);
            if(m.getName().equals("equals"))return args!=null&&p==args[0];
            if(m.getName().equals("toString"))return "LingStyle quoted image read";
            if(m.getName().equals("onResult"))try{
                if(args!=null&&args.length>=3&&((Number)args[0]).intValue()==0&&args[2] instanceof List&&config.account.equals(host.account())){
                    for(Object record:((List<?>)args[2]).toArray()){
                        if(Host.number(record,"chatType")!=type||!peer.equals(Host.text(record,"peerUid"))||!ref.matches(Host.number(record,"msgId"),Host.number(record,"msgSeq")))continue;
                        String senderUid=Host.text(record,"senderUid"),sender=Host.text(record,"senderUin");if(!sender.matches("[1-9][0-9]+"))sender=host.uin(senderUid);
                        if(Policy.blocked(config.blacklist,sender,senderUid)||(!config.blacklist.isEmpty()&&sender.isEmpty()))throw new IOException("引用来源被黑名单规则跳过");
                        List<IncomingImages.Picture> pictures=new ArrayList<>();Object elements=Host.field(record,"elements");
                        if(elements instanceof List)for(Object e:((List<?>)elements).toArray())if(Host.number(e,"elementType")==2){pictures.add(IncomingImages.snapshot(e));if(pictures.size()>VisionPolicy.MAX_IMAGES)throw new IOException("引用消息超过 2 张图片，已跳过");}
                        result.compareAndSet(null,new Source(fetch?current:Host.number(record,"msgId"),pictures));break;
                    }
                }
            }catch(Exception error){failure.set(error);}finally{done.countDown();}
            return null;
        });
        Object contact=host.contact(type,peer);
        if(fetch)Host.call(host.kernel(),"getSourceOfReplyMsg",contact,current,ref.sequence,callback);
        else Host.call(host.kernel(),"getMsgsByMsgId",contact,ids,callback);
        if(!done.await(6,TimeUnit.SECONDS))return null;
        if(failure.get()!=null)throw failure.get();
        return result.get();
    }
}
