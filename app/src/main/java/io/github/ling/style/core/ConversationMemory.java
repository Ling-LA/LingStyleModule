package io.github.ling.style.core;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** RAM-only complete turns, bounded globally and isolated by account/session keys. */
public final class ConversationMemory {
    public static final class Turn {
        public final String user,assistant;
        public final List<String> images;
        public Turn(String u,String a){this(u,a,Collections.emptyList());}
        public Turn(String u,String a,List<String> pictures){user=u;assistant=a;images=Collections.unmodifiableList(new ArrayList<>(pictures));}
        int cost(){return estimate(user)+estimate(assistant)+images.size()*VisionPolicy.IMAGE_TOKENS;}
        int storage(){int n=estimate(user)+estimate(assistant);for(String image:images)n+=image.length()*2;return n;}
    }
    private final LinkedHashMap<String,ArrayDeque<Turn>> sessions=new LinkedHashMap<>(16,.75f,true);
    private final int maxCost;private int used;
    public ConversationMemory(){this(8*1024*1024);}
    public ConversationMemory(int maxCost){this.maxCost=maxCost;}
    // Conservative UTF-8 byte estimate plus role framing; not an exact model tokenizer.
    public static int estimate(String text){return text.getBytes(StandardCharsets.UTF_8).length+16;}
    public synchronized List<Turn> snapshot(String key,int budget){
        ArrayDeque<Turn> turns=sessions.get(key);if(turns==null)return Collections.emptyList();
        LinkedList<Turn> result=new LinkedList<>();int cost=0;Iterator<Turn> it=turns.descendingIterator();
        while(it.hasNext()){Turn t=it.next();if(cost+t.cost()>budget)break;result.addFirst(t);cost+=t.cost();}return result;
    }
    public synchronized void commit(String key,String user,String assistant,int budget){
        commit(key,user,assistant,Collections.emptyList(),budget);
    }
    public synchronized void commit(String key,String user,String assistant,List<String> images,int budget){
        Turn turn=new Turn(user,assistant,images);if(turn.cost()>budget||turn.storage()>maxCost)return;
        ArrayDeque<Turn> turns=sessions.computeIfAbsent(key,k->new ArrayDeque<>());turns.addLast(turn);used+=turn.storage();
        int local=0;for(Turn t:turns)local+=t.cost();
        while(local>budget){Turn removed=turns.removeFirst();local-=removed.cost();used-=removed.storage();}
        while(used>maxCost||sessions.size()>64){String first=sessions.keySet().iterator().next();for(Turn t:sessions.remove(first))used-=t.storage();}
    }
    public synchronized void clear(){sessions.clear();used=0;}
}
