package io.github.ling.style.hook;
import android.content.Context;
import android.os.SystemClock;
import io.github.ling.style.engine.ImageCodec;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
/** Copies image descriptors, obtains bytes through QQ's own authenticated downloader. */
public final class IncomingImages {
    private static final long ORIGINAL_DOWNLOAD_LIMIT=12*1024*1024;
    public static final class Picture {
        final long element,size;final String path;final List<String> thumbnails;
        Picture(long e,long s,String p,List<String> thumbs){element=e;size=s;path=p;thumbnails=Collections.unmodifiableList(new ArrayList<>(thumbs));}
    }
    private static final class Pending {
        final CountDownLatch done=new CountDownLatch(1);final File target;final int downloadType;volatile String path="";volatile long error;
        Pending(File f,int type){target=f;downloadType=type;}
    }
    private final Host host;private final Context context;
    private final ConcurrentHashMap<String,Pending> pending=new ConcurrentHashMap<>();
    public volatile String status="图片接收待命";
    public IncomingImages(Context c,Host h){context=c;host=h;}
    public static Picture snapshot(Object element)throws Exception{
        Object p=Host.field(element,"picElement");
        if(Boolean.TRUE.equals(Host.field(p,"isFlashPic")))throw new IOException("不处理闪照");
        List<String> thumbs=new ArrayList<>();Object paths=Host.field(p,"thumbPath");
        if(paths instanceof Map)for(Object value:((Map<?,?>)paths).values())if(value instanceof String)thumbs.add((String)value);
        return new Picture(Host.number(element,"elementId"),Host.number(p,"fileSize"),Host.text(p,"sourcePath"),thumbs);
    }
    private static String key(String owner,long message,long element){return owner+":"+message+":"+element;}
    public void downloaded(Object info,String owner){
        Pending p=pending.get(key(owner,Host.number(info,"msgId"),Host.number(info,"msgElementId")));if(p==null)return;
        if(Host.number(info,"fileDownType")!=p.downloadType)return;
        long thumbSize=Host.number(info,"thumbSize");if(p.downloadType==2&&thumbSize!=0&&thumbSize!=750)return;
        p.error=Host.number(info,"fileErrCode");p.path=Host.text(info,"filePath");p.done.countDown();
    }
    public List<String> load(String owner,int chatType,String peer,long message,List<Picture> pictures)throws Exception{
        return load(owner,chatType,peer,message,pictures,false);
    }
    public List<String> load(String owner,int chatType,String peer,long message,List<Picture> pictures,boolean historical)throws Exception{
        List<String> result=new ArrayList<>();int thumbnailsUsed=0;long deadline=SystemClock.elapsedRealtime()+20000;
        for(Picture picture:pictures){
            if(!owner.equals(host.account()))throw new IOException("账号已切换，图片任务已取消");
            boolean thumbnail=historical||picture.size>ORIGINAL_DOWNLOAD_LIMIT;
            File local=allowed(picture.path);
            if(local!=null&&local.length()>ImageCodec.MAX_SOURCE)thumbnail=true;
            if(local==null||!local.isFile()||local.length()==0||local.length()>ImageCodec.MAX_SOURCE)local=null;
            if(local==null&&thumbnail){
                long pixels=0;
                for(String path:picture.thumbnails){File candidate=allowed(path);if(candidate==null||!candidate.isFile()||candidate.length()==0||candidate.length()>ImageCodec.MAX_SOURCE)continue;
                    android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(candidate.getPath(),bounds);
                    long count=(long)bounds.outWidth*bounds.outHeight;
                    if(Math.max(bounds.outWidth,bounds.outHeight)>=512&&count>pixels){pixels=count;local=candidate;}
                }
            }
            if(local!=null){result.add(ImageCodec.encode(local));if(thumbnail)thumbnailsUsed++;continue;}
            if(picture.element==0)throw new IOException("缺少图片元素标识");
            File dir=new File(context.getCacheDir(),"ling_style_images");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("图片缓存不可用");
            int downloadType=thumbnail?2:1;
            File target=File.createTempFile("vision-",".image",dir);Pending p=new Pending(target,downloadType);String k=key(owner,message,picture.element);
            pending.put(k,p);Object request=null;
            try{
                request=host.type("com.tencent.qqnt.kernel.nativeinterface.RichMediaElementGetReq").getConstructor().newInstance();
                Host.set(request,"msgId",message);Host.set(request,"peerUid",peer);Host.set(request,"chatType",chatType);
                Host.set(request,"elementId",picture.element);Host.set(request,"downloadType",downloadType);Host.set(request,"downSourceType",1);
                // Same thumbnail request as QQ 9.3.50 MultiPicMsgItem: type 2, size 750.
                Host.set(request,"thumbSize",thumbnail?750:0);Host.set(request,"triggerType",1);Host.set(request,"filePath",target.getAbsolutePath());
                status=thumbnail?(historical?"通过 QQ 获取引用图片缩略图":"原图较大，通过 QQ 获取缩略图"):"通过 QQ 下载消息图片";Host.call(host.kernel(),"getRichMediaElement",request);
                long remain=deadline-SystemClock.elapsedRealtime();
                if(remain<=0||!p.done.await(remain,TimeUnit.MILLISECONDS))throw new IOException("QQ 图片下载超时");
                if(p.error!=0)throw new IOException("QQ 图片下载失败，错误码 "+p.error);
                local=target.isFile()&&target.length()>0?target:allowed(p.path);
                long settle=Math.min(deadline,SystemClock.elapsedRealtime()+1500);
                while((local==null||!local.isFile()||local.length()==0)&&SystemClock.elapsedRealtime()<settle){
                    Thread.sleep(100);
                    if(target.isFile()&&target.length()>0)local=target;
                    else if(thumbnail)for(String path:picture.thumbnails){File cached=allowed(path);if(cached!=null&&cached.isFile()&&cached.length()>0&&(local==null||!local.isFile()||cached.length()>local.length()))local=cached;}
                }
                if(local==null)throw new IOException("QQ 未返回可用图片文件");
                status="QQ 图片文件检查 · 存在="+local.isFile()+" · 字节数="+local.length();
                result.add(ImageCodec.encode(local));
                if(thumbnail)thumbnailsUsed++;
            }finally{pending.remove(k,p);if(request!=null&&p.done.getCount()!=0)try{Host.call(host.kernel(),"cancelGetRichMediaElement",request);}catch(Exception ignored){}target.delete();}
        }
        status="已准备 "+result.size()+" 张图片（压缩并移除元数据）"+(historical?" · 历史引用":"")+(thumbnailsUsed>0?" · 缓存/缩略图适配 "+thumbnailsUsed+" 张":"");return Collections.unmodifiableList(result);
    }
    private File allowed(String path)throws IOException{
        if(path==null||path.isEmpty())return null;File file=new File(path).getCanonicalFile();
        File external=context.getExternalFilesDir(null);
        for(File root:new File[]{new File(context.getApplicationInfo().dataDir),external==null?null:external.getParentFile()})
            if(root!=null&&file.toPath().startsWith(root.getCanonicalFile().toPath()))return file;
        return null;
    }
}
