package io.github.ling.style.core;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Two model/image workers; the WebView remains serialized on the main thread. */
public final class ReplyWorkers {
    private ReplyWorkers(){}
    public static ThreadPoolExecutor create(){
        return new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32));
    }
}
