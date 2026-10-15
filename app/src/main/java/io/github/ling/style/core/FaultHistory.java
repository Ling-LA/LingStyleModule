package io.github.ling.style.core;

import java.util.*;
import java.text.SimpleDateFormat;

/** Bounded, content-free history. Counts survive eviction of older individual events. */
public final class FaultHistory {
    public static final int LIMIT=200;
    public static final class Entry {
        public final long at;public final FaultKind kind;public final Style style;public final String code;
        Entry(long at,FaultKind kind,Style style,String code){this.at=at;this.kind=kind;this.style=style;this.code=code;}
    }
    private final ArrayDeque<Entry> recent=new ArrayDeque<>();
    private final EnumMap<FaultKind,Long> counts=new EnumMap<>(FaultKind.class);
    private long since=System.currentTimeMillis(),version;
    public synchronized void add(long at,FaultKind kind,Style style,String code){
        recent.addLast(new Entry(at,kind,style,ReplyFault.safeCode(code)));
        while(recent.size()>LIMIT)recent.removeFirst();counts.put(kind,count(kind)+1);version++;
    }
    public synchronized long count(FaultKind kind){return counts.getOrDefault(kind,0L);}
    public synchronized long version(){return version;}
    public synchronized List<Entry> entries(){return new ArrayList<>(recent);}
    public synchronized long errors(){long n=0;for(FaultKind kind:FaultKind.values())if(kind!=FaultKind.CANCELLED)n+=count(kind);return n;}
    public synchronized String encode(){
        StringBuilder s=new StringBuilder("LINGFAULT1\t").append(since).append('\n');
        for(FaultKind kind:FaultKind.values())s.append("C\t").append(kind.name()).append('\t').append(count(kind)).append('\n');
        for(Entry e:recent)s.append("E\t").append(e.at).append('\t').append(e.kind.name()).append('\t').append(e.style.name()).append('\t').append(e.code).append('\n');
        return s.toString();
    }
    public static FaultHistory decode(String text){
        if(text.length()>65536)throw new IllegalArgumentException("oversized history");
        String[] rows=text.split("\n");String[] header=rows[0].split("\t");
        if(header.length!=2||!header[0].equals("LINGFAULT1"))throw new IllegalArgumentException("history version");
        FaultHistory h=new FaultHistory();h.since=Long.parseLong(header[1]);if(h.since<=0)throw new IllegalArgumentException("history time");
        for(int i=1;i<rows.length;i++){
            String[] f=rows[i].split("\t");
            if(f.length==3&&f[0].equals("C")){FaultKind kind=FaultKind.valueOf(f[1]);long n=Long.parseLong(f[2]);if(n<0||h.counts.containsKey(kind))throw new IllegalArgumentException("history count");h.counts.put(kind,n);}
            else if(f.length==5&&f[0].equals("E")){
                long at=Long.parseLong(f[1]);if(at<=0||!f[4].equals(ReplyFault.safeCode(f[4]))||h.recent.size()>=LIMIT)throw new IllegalArgumentException("history entry");
                h.recent.add(new Entry(at,FaultKind.valueOf(f[2]),Style.valueOf(f[3]),f[4]));
            }else throw new IllegalArgumentException("history row");
        }
        if(h.counts.size()!=FaultKind.values().length)throw new IllegalArgumentException("incomplete history");
        for(FaultKind kind:FaultKind.values()){long n=0;for(Entry e:h.recent)if(e.kind==kind)n++;if(h.count(kind)<n)throw new IllegalArgumentException("history count mismatch");}
        return h;
    }
    public synchronized String report(){
        SimpleDateFormat date=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.getDefault());
        StringBuilder s=new StringBuilder("从 ").append(date.format(new Date(since))).append(" 开始记录\n累计异常 ").append(errors()).append(" · 配置取消 ").append(count(FaultKind.CANCELLED));
        for(FaultKind kind:FaultKind.values())if(count(kind)>0)s.append('\n').append(kind.title).append("：").append(count(kind));
        s.append("\n\n最近 ").append(recent.size()).append(" 条（最多 200 条，重启保留）\n只记录时间、风格、分类和错误代码，不含聊天、账号、密钥或接口正文。升级前的旧异常无法补录。\n");
        Iterator<Entry> it=recent.descendingIterator();while(it.hasNext()){Entry e=it.next();s.append('\n').append(date.format(new Date(e.at))).append(" · ").append(e.style.title).append(" · ").append(e.kind.title).append('\n').append(e.code).append('\n');}
        return s.toString();
    }
}
