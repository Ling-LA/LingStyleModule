import io.github.ling.style.core.ReplyWorkers;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class WorkerChecks {
    public static void main(String[] args)throws Exception{
        ThreadPoolExecutor pool=ReplyWorkers.create();
        CountDownLatch started=new CountDownLatch(2),release=new CountDownLatch(1),done=new CountDownLatch(34);
        AtomicInteger running=new AtomicInteger(),peak=new AtomicInteger();
        Runnable task=()->{int n=running.incrementAndGet();peak.accumulateAndGet(n,Math::max);started.countDown();
            try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}
            finally{running.decrementAndGet();done.countDown();}};
        try{
            pool.execute(task);pool.execute(task);
            if(!started.await(3,TimeUnit.SECONDS))throw new AssertionError("second task must start before queue is full");
            for(int i=0;i<32;i++)pool.execute(task);
            if(pool.getQueue().size()!=32||peak.get()!=2)throw new AssertionError("bounded queue / exactly two workers");
            boolean rejected=false;try{pool.execute(task);}catch(RejectedExecutionException expected){rejected=true;}
            if(!rejected)throw new AssertionError("overload must reject, not create a third worker");
            release.countDown();
            if(!done.await(3,TimeUnit.SECONDS)||peak.get()!=2)throw new AssertionError("all accepted jobs finish with at most two workers");
            System.out.println("PASS 4 worker checks");
        }finally{release.countDown();pool.shutdownNow();}
    }
}
