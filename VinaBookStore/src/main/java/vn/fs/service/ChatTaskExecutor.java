package vn.fs.service;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class ChatTaskExecutor extends ThreadPoolExecutor {
    private static final AtomicInteger ids=new AtomicInteger();
    public ChatTaskExecutor(){super(4,8,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),work->{
        Thread thread=new Thread(work,"chat-io-"+ids.incrementAndGet());thread.setDaemon(true);return thread;
    },new AbortPolicy());}
    @PreDestroy public void stop(){shutdownNow();}
}
