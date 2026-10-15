package io.github.ling.style.hook;

import android.content.Context;
import android.os.*;
import android.widget.Toast;
import de.robv.android.xposed.*;
import io.github.ling.style.core.*;
import io.github.ling.style.engine.*;
import io.github.ling.style.store.Settings;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class ModuleRuntime {
    private static final String BYPASS_EXTRA="io.github.ling.style.originalSlashText";
    public final Settings settings;
    public final WebTranslator translator;
    public final ReplyClient replies=new ReplyClient();
    public final Context context;
    public final Host host;
    public final BackgroundSupport background;
    public final IncomingImages images;
    public final ActiveChat activeChat;
    public final io.github.ling.style.store.FaultStore faults;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor work=ReplyWorkers.create();
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    private final Policy.Seen seen=new Policy.Seen();
    private final Set<String> busy=ConcurrentHashMap.newKeySet();
    private final LinkedHashMap<String,Long> lastReply=new LinkedHashMap<>();
    private final LinkedHashMap<String,Long> lastParticipation=new LinkedHashMap<>();
    private final LinkedHashMap<String,Long> lastAmbientSent=new LinkedHashMap<>();
    private final ThreadLocal<Boolean> internal=new ThreadLocal<>();
    private Object registeredService,listener;
    private long listenerHandle;
    private String listenerAccount="";
    private volatile Method sendMethod;
    private long rateWindow;
    private volatile String autoTrace="尚无自动回复事件";
    private volatile long receiveAt,acceptAt,modelAt,webAt,sendAt,ackAt;
    private final AtomicLong acknowledged=new AtomicLong();
    private final AtomicLong recvCallbacks=new AtomicLong(),addCallbacks=new AtomicLong(),fileCallbacks=new AtomicLong();
    private final AtomicLong pollCallbacks=new AtomicLong(),pollFailures=new AtomicLong();
    private volatile long activeSince=System.currentTimeMillis()/1000L;
    private volatile boolean wasAuto;
    private long lastPoll,lastSync;private int pollCursor;
    private volatile String pollState="未补查",receiveShape="尚无消息";
    private int rateCount;
    public volatile String status="等待 QQ 内核";
    public volatile String sendState="尚无发送事件";
    private volatile String prefixState="";
    public volatile boolean hookInstalled,kernelInstalled;
    public final AtomicLong received=new AtomicLong(),duplicates=new AtomicLong(),submitted=new AtomicLong(),errors=new AtomicLong();
    public ModuleRuntime(Context context,ClassLoader loader){
        this.context=context;host=new Host(loader);activeChat=new ActiveChat(context,host);settings=new Settings(context);faults=new io.github.ling.style.store.FaultStore(context,timer);translator=new WebTranslator(context);background=new BackgroundSupport(this);images=new IncomingImages(context,host);settings.onAutoChanged=()->{wasAuto=settings.auto();if(wasAuto)activeSince=System.currentTimeMillis()/1000L;background.sync();};
    }
    public void start(){timer.scheduleWithFixedDelay(()->{refresh();background.sync();pollAllowedGroups();writeDiagnostic();},0,5,TimeUnit.SECONDS);}
    public void error(String message){FaultKind kind=FaultKind.fromMessage(message);recordFailure(kind,kind.name().toLowerCase(java.util.Locale.ROOT),message,settings.style());}
    private void recordFailure(FaultKind kind,String code,String message,Style style){
        status=message;if(kind!=FaultKind.CANCELLED)errors.incrementAndGet();faults.add(kind,style,code);
        XposedBridge.log("[LingStyle] ["+kind.name()+":"+ReplyFault.safeCode(code)+"] "+message);autoTrace=message;writeDiagnostic();
    }
    private void failure(Throwable e,FaultKind stage,Style style){
        ReplyFault known=e instanceof ReplyFault?(ReplyFault)e:null;
        recordFailure(known==null?stage:known.kind,known==null?stage.name().toLowerCase(java.util.Locale.ROOT)+"."+e.getClass().getSimpleName():known.code,
            stage.title+"失败："+(e instanceof java.io.IOException?e.getMessage():e.getClass().getSimpleName()),style);
    }
    public void notice(String text){main.post(()->Toast.makeText(context,text,Toast.LENGTH_SHORT).show());}
    private synchronized void refresh(){
        try{
            String account=host.account();if(!account.equals(settings.account())){replies.clear();activeSince=System.currentTimeMillis()/1000L;wasAuto=false;}settings.selectAccount(account);
            if(!settings.known())return;
            Object service=host.kernel();
            if(registeredService==service&&account.equals(listenerAccount))return;
            if(registeredService!=null){try{Host.call(registeredService,"removeKernelMsgListener",listenerHandle);}catch(Exception ignored){}kernelInstalled=false;}
            Class<?> api=host.type("com.tencent.qqnt.kernel.nativeinterface.IKernelMsgListener");
            Object callback=Proxy.newProxyInstance(api.getClassLoader(),new Class[]{api},(p,m,args)->{
                if(m.getName().equals("hashCode"))return System.identityHashCode(p);
                if(m.getName().equals("equals"))return args!=null&&p==args[0];
                if(m.getName().equals("toString"))return "LingStyle kernel listener";
                if(m.getName().equals("onRichMediaDownloadComplete")&&args!=null&&args.length>0&&account.equals(host.account()))images.downloaded(args[0],account);
                String event=m.getName();
                if(event.equals("onRecvMsg"))recvCallbacks.incrementAndGet();
                if(event.equals("onMsgInfoListAdd"))addCallbacks.incrementAndGet();
                if(event.equals("onFileMsgCome"))fileCallbacks.incrementAndGet();
                if((event.equals("onRecvMsg")||event.equals("onMsgInfoListAdd")||event.equals("onFileMsgCome"))&&args!=null&&account.equals(host.account())){
                    for(Object a:args)if(a instanceof List){for(Object record:((List<?>)a).toArray())accept(record,account);break;}
                }
                if(m.getReturnType()==boolean.class)return false;
                if(m.getReturnType()==int.class)return 0;
                if(m.getReturnType()==long.class)return 0L;
                return null;
            });
            Object handle=Host.call(service,"addKernelMsgListener",callback);
            listenerHandle=((Number)handle).longValue();registeredService=service;listener=callback;listenerAccount=account;kernelInstalled=true;
            status="内核接收已连接";
        }catch(Throwable e){status="内核等待："+e.getClass().getSimpleName();}
    }
    public void attachSend(Method method){sendMethod=method;hookInstalled=true;}
    /** Some QQ builds defer UI-oriented callbacks in background. Read only explicitly allowed groups. */
    private void pollAllowedGroups(){
        Settings.Snapshot config=settings.snapshot();
        if(!config.auto||!config.known){wasAuto=false;return;}
        if(!wasAuto){wasAuto=true;activeSince=System.currentTimeMillis()/1000L;}
        if(background.isVisible()||(!config.privateReplies&&config.groups.isEmpty())||SystemClock.elapsedRealtime()-lastPoll<10000)return;
        lastPoll=SystemClock.elapsedRealtime();
        if(lastPoll-lastSync>=30000){
            lastSync=lastPoll;
            try{Host.call(host.kernel(),"startMsgSync");}catch(Exception failure){pollState="消息同步调用失败："+failure.getClass().getSimpleName();}
        }
        List<String> groups=new ArrayList<>(config.groups);Collections.sort(groups);
        for(int i=0;i<Math.min(4,groups.size());i++){
            String group=groups.get(Math.floorMod(pollCursor++,groups.size()));if(!group.matches("[1-9][0-9]+")||Policy.blocked(config.blacklist,group))continue;
            try{
                Class<?> api=host.type("com.tencent.qqnt.kernel.nativeinterface.IMsgOperateCallback");
                Object callback=Proxy.newProxyInstance(api.getClassLoader(),new Class[]{api},(p,m,args)->{
                    if(m.getName().equals("hashCode"))return System.identityHashCode(p);
                    if(m.getName().equals("equals"))return args!=null&&p==args[0];
                    if(m.getName().equals("toString"))return "LingStyle allowed-group read";
                    if(m.getName().equals("onResult")&&args!=null&&args.length>=3){
                        pollCallbacks.incrementAndGet();int code=((Number)args[0]).intValue();
                        if(code!=0){pollFailures.incrementAndGet();pollState="群消息补查错误码 "+code+" · "+safeNativeError(args[1]);return null;}
                        if(args[2] instanceof List){List<?> records=new ArrayList<>((List<?>)args[2]);
                            records.sort(Comparator.comparingLong(r->Host.number(r,"msgTime")));
                            pollState="群消息补查返回 "+records.size()+" 条";
                            if(!records.isEmpty())pollState+=" · 最新消息年龄秒 "+(System.currentTimeMillis()/1000L-Host.number(records.get(records.size()-1),"msgTime"));
                            if(same(config.account,config.revision)&&settings.auto())for(Object record:records){
                                if(Host.number(record,"chatType")==2&&group.equals(Host.text(record,"peerUid"))&&Host.number(record,"msgTime")>=activeSince)accept(record,config.account);
                            }
                        }
                    }
                    return null;
                });
                Host.call(host.kernel(),"getMsgs",host.contact(2,group),0L,10,true,callback);
            }catch(Exception failure){pollFailures.incrementAndGet();pollState="补查调用失败："+failure.getClass().getSimpleName();}
        }
    }
    private static String safeNativeError(Object value){
        String text=String.valueOf(value).replaceAll("https?://[^\\s]+","[url]").replaceAll("[0-9]{5,}|u_[A-Za-z0-9_-]+","[id]");
        return text.substring(0,Math.min(160,text.length()));
    }
    public String diagnostics(){return status+"\n发送接口 "+hookInstalled+" · 内核监听 "+kernelInstalled+"\n接收 "+received.get()+" · 去重 "+duplicates.get()+" · 提交 "+submitted.get()+" · 本次异常 "+errors.get()+" · 历史异常 "+faults.history.errors()+"\n"+sendState+"\n"+prefixState+"\n"+translator.status+"\n"+replies.status+"\n"+replies.visionStatus+"\n"+images.status+"\n"+background.status+"\n"+autoTrace+" · QQ 确认 "+acknowledged.get()+"\n"+pollState+"\n"+activeChat.status();}
    private boolean blocked(String... ids){return Policy.blocked(settings.blacklist(),ids);}
    private boolean same(String owner,long revision){return owner.equals(host.account())&&owner.equals(settings.account())&&revision==settings.revision.get();}

    /** Intercepts only ordinary text. Replay is guarded once and invokes this same API. */
    public void observeOutgoing(XC_MethodHook.MethodHookParam p){
        if(Boolean.TRUE.equals(internal.get()))return;
        String original=Host.plain(p.args[2]);
        prefixState="入口斜线="+Policy.bypass(original);
        if(Policy.bypass(original))p.setObjectExtra(BYPASS_EXTRA,Policy.unprefix(original));
    }
    public void outgoing(XC_MethodHook.MethodHookParam p){
        Settings.Snapshot config=settings.snapshot();
        if(Boolean.TRUE.equals(internal.get()))return;
        sendState="发送入口已触发";
        if(!config.known||!config.account.equals(host.account())){sendState="账号尚未同步";return;}
        Object bypass=p.getObjectExtra(BYPASS_EXTRA);
        prefixState+=" · 快照="+(bypass instanceof String)+" · 当前斜线="+Policy.bypass(Host.plain(p.args[2]));
        if(bypass instanceof String){
            try{p.args[2]=host.elements((String)bypass);sendState="斜线跳过";}
            catch(Exception e){error("跳过转换失败："+e.getClass().getSimpleName());}
            return;
        }
        String text=Host.plain(p.args[2]);if(text==null||text.isEmpty()){
            sendState="非普通纯文本："+Host.shape(p.args[2]);return;
        }
        Object contact=p.args[1];int type=(int)Host.number(contact,"chatType");
        if(type!=1&&type!=2&&type!=100){sendState="不支持的会话类型 "+type;return;}
        String uid=Host.text(contact,"peerUid"),uin=type==2?uid:host.uin(uid);
        try{
            if(Policy.bypass(text)){sendState="斜线跳过";p.args[2]=host.elements(Policy.unprefix(text));return;}
            if(!config.enabled||Policy.blocked(config.blacklist,uid,uin)||text.length()>4000){sendState="开关、黑名单或长度规则跳过";return;}
            // If numeric blacklist entries cannot be checked for this private UID, fail closed.
            if(type!=2&&uin.isEmpty()&&!config.blacklist.isEmpty())return;
            final Object target=p.thisObject;final Method method=(Method)p.method;
            final Object[] args=p.args.clone();args[2]=new ArrayList<>((List<?>)p.args[2]);
            if(p.args[3] instanceof Map)args[3]=new HashMap<>((Map<?,?>)p.args[3]);
            final String owner=config.account;final long revision=config.revision;final Style style=config.style;
            final Policy.Once once=new Policy.Once();final PowerManager.WakeLock wake=wake();
            p.setResult(null);
            sendState="已进入网页转换队列";
            WebTranslator.Callback replay=(result,failure)->{
                if(!once.claim())return;
                try{
                    if(!owner.equals(host.account())){error("账号已切换，待发送内容已取消");notice("账号已切换，请在正确账号重新发送");return;}
                    if(failure==null&&same(owner,revision)&&settings.enabled()&&!blocked(uid,uin))args[2]=host.elements(result);
                    else if(failure!=null){error(failure);notice("风格转换失败，已按原文发送");}
                    invokeSend(method,target,args);
                }catch(Throwable e){error("提交失败："+e.getClass().getSimpleName());notice("发送失败，请检查 QQ 消息状态");}
                finally{release(wake);}
            };
            try{translator.translate(style,text,replay);}catch(Throwable dispatch){replay.complete(null,"转换任务无法启动");}
        }catch(Throwable e){error("转换入口异常："+e.getClass().getSimpleName());}
    }
    private void invokeSend(Method method,Object target,Object[] args)throws Throwable{
        internal.set(true);
        try{method.setAccessible(true);method.invoke(target,args);submitted.incrementAndGet();}
        finally{internal.remove();}
    }
    private void accept(Object record,String account){
        if(record==null)return;received.incrementAndGet();receiveAt=System.currentTimeMillis();
        try{
            Object shape=Host.field(record,"elements");List<Long> types=new ArrayList<>();if(shape instanceof List)for(Object e:(List<?>)shape)types.add(Host.number(e,"elementType"));
            receiveShape="会话类型="+Host.number(record,"chatType")+", 发送类型="+Host.number(record,"sendType")+", 元素="+types+", 消息年龄秒="+((System.currentTimeMillis()/1000)-Host.number(record,"msgTime"));
            // Copy only primitive/string fields; never keep mutable native MsgRecord in queues.
            int type=(int)Host.number(record,"chatType");
            if(type!=1&&type!=2&&type!=100)return;
            PokeEvent poke=PokeEvent.from(record,host::uin);
            if(poke!=null&&!poke.targets(account))return;
            if(poke==null&&(Host.number(record,"sendType")!=0||Host.text(record,"senderUin").equals(account)))return;
            if(!Policy.fresh(Host.number(record,"msgTime"),System.currentTimeMillis()))return;
            if(Host.number(record,"msgTime")<activeSince)return;
            try{if(Boolean.TRUE.equals(Host.field(record,"isImportMsg")))return;}catch(Exception ignored){}
            String uid=Host.text(record,"peerUid"),peer=Host.text(record,"peerUin"),sender=Host.text(record,"senderUin"),senderUid=Host.text(record,"senderUid");
            if(!peer.matches("[1-9][0-9]+"))peer=host.uin(uid);
            if(!sender.matches("[1-9][0-9]+"))sender=host.uin(senderUid);
            if(poke!=null){sender=poke.actor;senderUid=host.uid(sender);if(senderUid.isEmpty())return;}
            long id=Host.number(record,"msgId");
            Settings.Snapshot config=settings.snapshot();
            if(!account.equals(config.account)||!config.auto||Policy.blocked(config.blacklist,uid,peer,sender,senderUid)||uid.isEmpty())return;
            if(poke!=null&&!config.pokeReplies)return;
            if(!config.blacklist.isEmpty()&&(peer.isEmpty()||sender.isEmpty()))return;
            if(account.equals(host.uin(senderUid)))return;
            if(type!=2&&(!config.privateReplies||!host.friend(uid)))return;
            if(poke!=null&&type!=2&&!sender.equals(peer))return;
            if(type==2&&!config.groups.contains(peer))return;
            Object raw=Host.field(record,"elements");if(!(raw instanceof List))return;
            StringBuilder text=new StringBuilder();boolean at=false,atOther=false;
            QuoteRef quote=null;
            List<IncomingImages.Picture> pictures=new ArrayList<>();
            if(poke!=null)text.append("对方刚刚戳了你一下，请用一句简短自然的话回应这次戳一戳。");
            else for(Object element:((List<?>)raw).toArray()){
                if(Host.number(element,"elementType")==7){if(quote!=null)return;quote=QuotedImages.snapshot(element);continue;}
                if(Host.number(element,"elementType")==2){pictures.add(IncomingImages.snapshot(element));if(pictures.size()>VisionPolicy.MAX_IMAGES){autoTrace="每条消息最多识别 2 张图片，已跳过";return;}continue;}
                if(Host.number(element,"elementType")!=1)return;
                Object e=Host.field(element,"textElement");String mention=Host.text(e,"atUid");
                if(Host.mention(e)){if(account.equals(host.uin(mention)))at=true;else atOther=true;continue;}
                text.append(Host.text(e,"content"));
            }
            if(poke==null&&type==2&&config.atOnly&&!at)return;
            if(type==2&&!at&&atOther)return;
            String incoming=text.toString().trim();if((incoming.isEmpty()&&pictures.isEmpty()&&quote==null)||incoming.length()>4000)return;
            String scope=Policy.replyScope(account,type,uid,senderUid,sender);
            if(scope==null){autoTrace="无法确认发言人身份，已跳过回复";return;}
            if(!seen.first(account,type+":"+uid,id,SystemClock.elapsedRealtime())){duplicates.incrementAndGet();return;}
            if(skipActive(config,type,uid))return;
            String lane=account+":"+type+":"+uid;
            final boolean smart=type==2&&!at&&poke==null;
            if(!reserve(lane,smart)){autoTrace="会话冷却、忙碌或速率规则跳过";return;}
            acceptAt=System.currentTimeMillis();autoTrace="已接收待回复消息 · 屏幕="+interactive();writeDiagnostic();
            XposedBridge.log("[LingStyle] auto accepted style="+config.style.name()+" interactive="+interactive());
            final String fUid=uid,fPeer=peer,fSender=sender,fSenderUid=senderUid;
            final QuoteRef reference=quote;
            try{work.execute(()->autoReply(config,type,fUid,fPeer,fSender,fSenderUid,incoming,lane,scope,id,pictures,reference,smart,poke!=null));}
            catch(RejectedExecutionException full){busy.remove(lane);error("自动回复队列已满");}
        }catch(Throwable e){error("接收异常："+e.getClass().getSimpleName());}
    }
    private boolean skipActive(Settings.Snapshot config,int type,String uid){
        if(!activeChat.suppress(config.skipActive,config.account,type,uid))return false;
        autoTrace="正在查看该聊天，已跳过自动回复";writeDiagnostic();return true;
    }
    private synchronized boolean reserve(String lane,boolean smart){
        long now=SystemClock.elapsedRealtime();
        if(now-rateWindow>60000){rateWindow=now;rateCount=0;}
        if(rateCount>=10||busy.contains(lane)||now-lastReply.getOrDefault(lane,0L)<10000)return false;
        if(smart&&now-lastParticipation.getOrDefault(lane,0L)<10000)return false;
        busy.add(lane);rateCount++;if(smart)lastParticipation.put(lane,now);
        while(lastParticipation.size()>256)lastParticipation.remove(lastParticipation.keySet().iterator().next());
        while(lastAmbientSent.size()>256)lastAmbientSent.remove(lastAmbientSent.keySet().iterator().next());
        while(lastReply.size()>256)lastReply.remove(lastReply.keySet().iterator().next());return true;
    }
    private void autoReply(Settings.Snapshot config,int type,String uid,String peer,String sender,String senderUid,String input,String lane,String scope,long message,List<IncomingImages.Picture> pictures,QuoteRef quote,boolean smart,boolean poke){
        String owner=config.account;long revision=config.revision;Style style=config.style;
        FaultKind phase=FaultKind.MODEL;boolean hasImages=!pictures.isEmpty()||(quote!=null&&quote.imageHint);
        PowerManager.WakeLock wake=wake();
        try{
            if(!same(owner,revision)||!settings.auto()||skipActive(config,type,uid)){busy.remove(lane);release(wake);return;}
            WebTranslator.Preload preload=translator.preload(owner,revision,style,()->same(owner,revision)&&settings.auto());
            QuotedImages.Source source=null;
            if(quote!=null){phase=FaultKind.QUOTE;autoTrace="读取当前会话内的引用内容";source=new QuotedImages(host).resolve(config,type,uid,message,quote);}
            String modelInput=QuotedText.context(input,source==null?"":source.text);
            hasImages=!pictures.isEmpty()||(source!=null&&!source.pictures.isEmpty());
            List<String> imageData=Collections.emptyList();
            if(hasImages){
                phase=FaultKind.IMAGE;
                replies.requireVision(config.endpoint,config.model,config.key);autoTrace="准备图片 · 屏幕="+interactive();writeDiagnostic();
                if(pictures.size()+(source==null?0:source.pictures.size())>VisionPolicy.MAX_IMAGES)throw new java.io.IOException("当前消息及引用合计超过 2 张图片，已跳过");
                List<String> loaded=new ArrayList<>();
                if(source!=null&&!source.pictures.isEmpty())loaded.addAll(images.load(owner,type,uid,source.message,source.pictures,true));
                if(!pictures.isEmpty())loaded.addAll(images.load(owner,type,uid,message,pictures));
                imageData=loaded;
                if(!same(owner,revision)||!settings.auto()||skipActive(config,type,uid)){busy.remove(lane);release(wake);return;}
            }
            if(!same(owner,revision)||!settings.auto()||skipActive(config,type,uid)){busy.remove(lane);release(wake);return;}
            phase=FaultKind.MODEL;
            if(modelInput.trim().isEmpty()&&imageData.isEmpty()){autoTrace="引用内容无可用文字或图片，保持安静";busy.remove(lane);release(wake);return;}
            ReplyClient.Answer answer=replies.reply(scope,modelInput,imageData,config.endpoint,config.model,config.key,config.fallback,smart);
            if(answer.silent()){if(same(owner,revision))replies.observeImage(answer);autoTrace="模型判断无需插话，保持安静";busy.remove(lane);release(wake);writeDiagnostic();return;}
            if(!Policy.allowImageComment(smart,!imageData.isEmpty(),answer.intent,ThreadLocalRandom.current().nextInt(100))){
                if(same(owner,revision))replies.observeImage(answer);
                autoTrace="图片主动点评未命中 30% 概率，保留图片供追问";busy.remove(lane);release(wake);writeDiagnostic();return;
            }
            if("comment".equals(answer.intent)){synchronized(this){if(SystemClock.elapsedRealtime()-lastAmbientSent.getOrDefault(lane,0L)<60000){if(same(owner,revision))replies.observeImage(answer);autoTrace="自然点评冷却中，保持安静";busy.remove(lane);release(wake);writeDiagnostic();return;}}}
            if(!same(owner,revision)||!settings.auto()||blocked(uid,peer,sender,senderUid)||skipActive(config,type,uid)){busy.remove(lane);release(wake);return;}
            modelAt=System.currentTimeMillis();autoTrace="模型生成完成 · 屏幕="+interactive();writeDiagnostic();
            translator.translate(preload,answer.text,(result,failure)->{
                if(failure!=null){FaultKind kind=FaultKind.fromMessage(failure);recordFailure(kind,kind.name().toLowerCase(java.util.Locale.ROOT),failure,style);busy.remove(lane);release(wake);return;}
                webAt=System.currentTimeMillis();autoTrace="网页转换完成 · 屏幕="+interactive();writeDiagnostic();
                if(!same(owner,revision)||!settings.auto()||blocked(uid,peer,sender,senderUid)||skipActive(config,type,uid)){busy.remove(lane);release(wake);return;}
                try{
                    final String styled=replies.finishStyle(answer,result);
                    main.post(()->{
                        if(!same(owner,revision)||!settings.auto()||blocked(uid,peer,sender,senderUid)||skipActive(config,type,uid)){busy.remove(lane);release(wake);return;}
                        submitAuto(config,type,uid,lane,message,poke?senderUid:null,answer,styled,wake);
                    });
                }catch(Exception e){busy.remove(lane);release(wake);error("网页风格结果不可发送："+e.getMessage());}
            });
        }catch(Throwable e){if(hasImages&&same(owner,revision))replies.unreadImage(scope,config.endpoint,config.model,config.key);busy.remove(lane);release(wake);failure(e,phase,style);}
    }
    private void submitAuto(Settings.Snapshot config,int type,String uid,String lane,long sourceMessage,String pokeUid,ReplyClient.Answer answer,String result,PowerManager.WakeLock wake){
        if(skipActive(config,type,uid)){busy.remove(lane);release(wake);return;}
        final Policy.Once once=new Policy.Once();final Runnable[] timeout={null};
        final String[] submitFailure={null};
        java.util.function.IntConsumer complete=code->{
            if(!once.claim())return;
            if(timeout[0]!=null)main.removeCallbacks(timeout[0]);
            ackAt=System.currentTimeMillis();
            if(code==0){acknowledged.incrementAndGet();synchronized(this){long now=SystemClock.elapsedRealtime();lastReply.put(lane,now);if("comment".equals(answer.intent))lastAmbientSent.put(lane,now);}if(same(config.account,config.revision))replies.commit(answer,result);status="QQ 已确认自动回复发送成功 · "+config.style.title;autoTrace="QQ 返回成功 · "+answer.intent+" · 屏幕="+interactive();}
            else recordFailure(FaultKind.SEND,code==-998?"send.submit":code==-999?"send.ack_timeout":"send.code."+code,
                code==-998?"自动回复提交异常："+submitFailure[0]:code==-999?"QQ 发送结果确认超时（不自动重发）":"QQ 自动回复返回错误码 "+code,config.style);
            busy.remove(lane);release(wake);writeDiagnostic();
        };
        timeout[0]=()->complete.accept(-999);
        try{
            Method send=sendMethod;if(send==null)throw new IllegalStateException("发送接口尚未就绪");
            Class<?> callbackType=send.getParameterTypes()[4];
            if(!callbackType.isInterface())throw new IllegalStateException("发送回调类型不兼容");
            Object callback=Proxy.newProxyInstance(callbackType.getClassLoader(),new Class[]{callbackType},(p,m,args)->{
                if(m.getName().equals("hashCode"))return System.identityHashCode(p);
                if(m.getName().equals("equals"))return args!=null&&p==args[0];
                if(m.getName().equals("toString"))return "LingStyle send result";
                if(m.getName().equals("onResult")&&args!=null&&args.length>0&&args[0] instanceof Number)complete.accept(((Number)args[0]).intValue());
                return null;
            });
            Object service=host.kernel(),contact=host.contact(type,uid);
            long id=((Number)Host.call(service,"generateMsgUniqueId",type,System.currentTimeMillis())).longValue();
            sendAt=System.currentTimeMillis();autoTrace="等待 QQ 发送结果 · 屏幕="+interactive();main.postDelayed(timeout[0],20000);writeDiagnostic();
            invokeSend(send,service,new Object[]{id,contact,host.autoElements(type,sourceMessage,pokeUid,result),new HashMap<>(),callback});
        }catch(Throwable e){submitFailure[0]=e.getClass().getSimpleName();complete.accept(-998);}
    }
    /** Opt-in diagnostic state contains no peer IDs, message text, key or license. */
    private synchronized void writeDiagnostic(){
        if(!settings.diagnostic())return;
        try{
            java.io.File dir=context.getExternalFilesDir(null);if(dir==null)return;
            org.json.JSONObject j=new org.json.JSONObject().put("heartbeat",System.currentTimeMillis()).put("interactive",interactive())
                .put("received",received.get()).put("acceptedAt",acceptAt).put("receivedAt",receiveAt).put("modelAt",modelAt).put("webAt",webAt).put("sendAt",sendAt).put("ackAt",ackAt)
                .put("recvCallbacks",recvCallbacks.get()).put("addCallbacks",addCallbacks.get()).put("fileCallbacks",fileCallbacks.get())
                .put("pollCallbacks",pollCallbacks.get()).put("pollFailures",pollFailures.get())
                .put("pollState",pollState).put("receiveShape",receiveShape)
                .put("activeChat",activeChat.status()).put("skipActive",settings.skipActive()).put("background",background.status).put("vision",replies.visionStatus).put("images",images.status).put("submitted",submitted.get()).put("acknowledged",acknowledged.get()).put("errors",errors.get()).put("stage",autoTrace).put("status",status).put("web",translator.status).put("errorHistory",faults.history.encode());
            java.io.File tmp=new java.io.File(dir,"LingStyleDiagnostic.tmp"),file=new java.io.File(dir,"LingStyleDiagnostic.json");
            java.nio.file.Files.write(tmp.toPath(),j.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));java.nio.file.Files.move(tmp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception ignored){}
    }
    private PowerManager.WakeLock wake(){
        try{PowerManager pm=(PowerManager)context.getSystemService(Context.POWER_SERVICE);PowerManager.WakeLock w=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"LingStyle:reply");w.setReferenceCounted(false);w.acquire(180000);return w;}
        catch(Throwable e){return null;}
    }
    private boolean interactive(){try{return ((PowerManager)context.getSystemService(Context.POWER_SERVICE)).isInteractive();}catch(Throwable ignored){return true;}}
    private static void release(PowerManager.WakeLock w){try{if(w!=null&&w.isHeld())w.release();}catch(Throwable ignored){}}
}
