package io.github.ling.style.hook;
import io.github.ling.style.core.ContactChoice;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Reads QQ's own account roster on demand. No contact data leaves the device. */
public final class ContactRoster {
    private final Host host;
    public ContactRoster(Host h){host=h;}
    public List<ContactChoice> load(boolean groups,String owner)throws Exception{
        if(!owner.equals(host.account()))throw new IOException("QQ 账号已切换");
        List<ContactChoice> result=groups?groups():friends();
        if(!owner.equals(host.account()))throw new IOException("QQ 账号已切换");
        result.sort(Comparator.comparing((ContactChoice c)->c.name).thenComparing(c->c.id));return result;
    }
    private List<ContactChoice> friends()throws Exception{
        Object service=host.route("com.tencent.qqnt.ntrelation.friendsinfo.api.IFriendsInfoService");
        Object raw=Host.call(service,"getAllFriend","LingStyle");
        if(!(raw instanceof List))throw new IOException("QQ 未返回好友列表");
        Map<String,ContactChoice> result=new LinkedHashMap<>();
        for(Object item:((List<?>)raw).toArray()){
            if(item instanceof String)item=Host.call(service,"getFriendsSimpleInfoWithUid",item,"LingStyle");
            if(item==null)continue;
            String uid=Host.text(item,"uid"),uin=Host.text(item,"uin");if(!uin.matches("[1-9][0-9]+"))uin=host.uin(uid);
            String id=uin.isEmpty()?uid:uin;if(!id.matches("[1-9][0-9]+|u_[A-Za-z0-9_-]+"))continue;
            String name=Host.text(item,"remark");if(name.isEmpty())name=Host.text(item,"nick");if(name.isEmpty())name=Host.text(item,"friendName");
            result.put(id,new ContactChoice(id,label(name,id),uid,uin));
        }
        return new ArrayList<>(result.values());
    }
    private List<ContactChoice> groups()throws Exception{
        Object kernel=Host.call(host.runtime(),"getRuntimeService",host.type("com.tencent.qqnt.kernel.api.IKernelService"),"");
        Object service=Host.call(Host.call(kernel,"getGroupService"),"getService");
        CountDownLatch done=new CountDownLatch(1);AtomicReference<Exception> failure=new AtomicReference<>();
        Map<String,ContactChoice> result=new ConcurrentHashMap<>();
        Class<?> type=host.type("com.tencent.qqnt.kernel.nativeinterface.IKernelGroupListener");
        Object listener=Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,args)->{
            if(m.getName().equals("hashCode"))return System.identityHashCode(p);if(m.getName().equals("equals"))return args!=null&&p==args[0];if(m.getName().equals("toString"))return "LingStyle roster listener";
            if(m.getName().equals("onGroupListUpdate")&&args!=null)for(Object arg:args)if(arg instanceof List){
                for(Object row:((List<?>)arg).toArray()){String id=Host.text(row,"groupCode");if(id.matches("[1-9][0-9]+"))result.put(id,new ContactChoice(id,label(Host.text(row,"groupName"),id)));}done.countDown();
            }return null;
        });
        long handle=((Number)Host.call(service,"addKernelGroupListener",listener)).longValue();
        try{
            Class<?> cb=host.type("com.tencent.qqnt.kernel.nativeinterface.IOperateCallback");
            Object callback=Proxy.newProxyInstance(cb.getClassLoader(),new Class[]{cb},(p,m,args)->{
                if(m.getName().equals("hashCode"))return System.identityHashCode(p);if(m.getName().equals("equals"))return args!=null&&p==args[0];if(m.getName().equals("toString"))return "LingStyle roster read";
                if(m.getName().equals("onResult")&&args!=null&&args[0] instanceof Number&&((Number)args[0]).intValue()!=0){failure.set(new IOException("QQ 群列表读取失败"));done.countDown();}return null;
            });
            Host.call(service,"getGroupList",false,callback);
            if(!done.await(10,TimeUnit.SECONDS))throw new IOException("读取群列表超时，请稍后重试");
            if(failure.get()!=null)throw failure.get();return new ArrayList<>(result.values());
        }finally{Host.call(service,"removeKernelGroupListener",handle);}
    }
    private static String label(String text,String id){text=text.replaceAll("[\\p{Cntrl}]"," ").trim();return text.isEmpty()?id:text.substring(0,Math.min(text.length(),120));}
}
