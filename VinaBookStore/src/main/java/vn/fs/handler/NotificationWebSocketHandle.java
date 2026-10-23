package vn.fs.handler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class NotificationWebSocketHandle extends TextWebSocketHandler {
    private final Set<WebSocketSession> sessions=ConcurrentHashMap.newKeySet();
    private final vn.fs.service.implement.UserDetailsServiceImpl users;
    public NotificationWebSocketHandle(vn.fs.service.implement.UserDetailsServiceImpl users){this.users=users;}
    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if(session.getAttributes().get("email")==null){session.close(CloseStatus.POLICY_VIOLATION);return;}
        sessions.add(session);
    }
    @Override protected void handleTextMessage(WebSocketSession session,TextMessage message) throws Exception {
        session.close(CloseStatus.POLICY_VIOLATION);sessions.remove(session);
    }
    @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status){sessions.remove(session);}
    public void publish(String json) {
        TextMessage message=new TextMessage(json);
        for(WebSocketSession session:sessions) {
            try {
                var user=users.loadUserByUsername(String.valueOf(session.getAttributes().get("email")));
                if(!user.isEnabled() || user.getAuthorities().stream().noneMatch(role->"ROLE_ADMIN".equals(role.getAuthority()))) {
                    sessions.remove(session);session.close(CloseStatus.POLICY_VIOLATION);continue;
                }
                synchronized(session){if(session.isOpen())session.sendMessage(message);else sessions.remove(session);}
            }
            catch(Exception failed){sessions.remove(session);try{session.close();}catch(Exception ignored){}}
        }
    }
}
