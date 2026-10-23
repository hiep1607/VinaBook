package vn.fs.service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.fs.config.CurrentUserAccess;

/** A single-use ticket avoids putting the account's long-lived JWT in a URL. */
@Service
public class NotificationSocketTickets {
    private record Ticket(String email,long expires) {}
    private final Map<String,Ticket> tickets=new ConcurrentHashMap<>();
    private final CurrentUserAccess access;
    public NotificationSocketTickets(CurrentUserAccess access){this.access=access;}
    public Map<String,String> issue() {
        if(!access.isAdmin())throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        long now=System.currentTimeMillis();tickets.values().removeIf(t->t.expires<now);
        if(tickets.size()>=2000)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        String ticket=UUID.randomUUID().toString()+UUID.randomUUID();
        tickets.put(ticket,new Ticket(access.currentEmail(),now+30000));return Map.of("ticket",ticket);
    }
    public String consume(String ticket) {
        if(ticket==null || ticket.length()>80)return null;
        Ticket found=tickets.remove(ticket);
        return found!=null && found.expires>=System.currentTimeMillis()?found.email:null;
    }
}
