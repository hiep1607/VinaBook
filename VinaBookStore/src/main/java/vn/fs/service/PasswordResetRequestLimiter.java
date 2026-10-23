package vn.fs.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetRequestLimiter {
    private record Window(long started,int count) {}
    private final Map<String,Window> clients=new ConcurrentHashMap<>();
    public boolean allow(String address) {
        long now=System.currentTimeMillis();
        if(clients.size()>=10000)clients.entrySet().removeIf(e->now-e.getValue().started>900000);
        if(clients.size()>=10000 && !clients.containsKey(address))return false;
        return clients.compute(address,(key,current)->current==null || now-current.started>900000?new Window(now,1):new Window(current.started,current.count+1)).count<=5;
    }
}
