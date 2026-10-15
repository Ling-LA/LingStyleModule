package io.github.ling.style.store;

import android.content.Context;
import android.util.AtomicFile;
import io.github.ling.style.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** App-private atomic persistence; writes run off the UI thread and coalesce bursts. */
public final class FaultStore {
    // AtomicFile provides crash recovery, not locking between a reader and an active writer.
    private static final Object FILE_LOCK=new Object();
    public final FaultHistory history;
    private final AtomicFile file;private final Executor executor;
    private final AtomicBoolean pending=new AtomicBoolean();
    private volatile String storage="";
    private volatile boolean writeFailed;
    public FaultStore(Context context,Executor executor){
        this.executor=executor;file=new AtomicFile(new File(context.getFilesDir(),"ling-style-faults-v1.tsv"));
        FaultHistory loaded=new FaultHistory();
        synchronized(FILE_LOCK){try(InputStream in=file.openRead()){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[2048];int n;
            while((n=in.read(b))!=-1){if(bytes.size()+n>65536)throw new IOException("oversized");bytes.write(b,0,n);}
            loaded=FaultHistory.decode(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
        }catch(FileNotFoundException absent){}catch(Exception invalid){storage="旧历史文件无法读取，已开始新记录。\n";}}
        history=loaded;
    }
    public void add(FaultKind kind,Style style,String code){history.add(System.currentTimeMillis(),kind,style,code);schedule();}
    private void schedule(){if(pending.compareAndSet(false,true))executor.execute(()->{
        long version=history.version();FileOutputStream out=null;
        try{synchronized(FILE_LOCK){
            try{out=file.startWrite();out.write(history.encode().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);writeFailed=false;}
            catch(Exception e){if(out!=null)file.failWrite(out);writeFailed=true;}
        }}finally{pending.set(false);if(history.version()!=version)schedule();}
    });}
    public String report(){return storage+(writeFailed?"历史保存失败，当前记录仍在内存中。\n":"")+history.report();}
}
