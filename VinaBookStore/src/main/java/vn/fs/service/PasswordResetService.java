package vn.fs.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.fs.entity.*;
import vn.fs.repository.*;

/** Only a digest is stored. Every request replaces the user's previous reset link. */
@Service
public class PasswordResetService {
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final SendMailService mail;
    private final EntityManager em;
    private final String publicBase;
    private final SecureRandom random=new SecureRandom();
    private final Map<String,Long> requested=new ConcurrentHashMap<>();
    public PasswordResetService(UserRepository users,PasswordResetTokenRepository tokens,
            PasswordEncoder encoder,SendMailService mail,EntityManager em,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBase) {
        this.users=users;this.tokens=tokens;this.encoder=encoder;this.mail=mail;this.em=em;
        URI uri=URI.create(publicBase);
        if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null)
            throw new IllegalArgumentException("Invalid public base URL");
        this.publicBase=publicBase.replaceAll("/+$","");
    }
    @Transactional
    public void request(String input) {
        String email=input==null?"":input.trim().toLowerCase(Locale.ROOT);
        if(email.length()>254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))return;
        long now=System.currentTimeMillis();String key=hash(email);
        if(requested.size()>=10000)requested.entrySet().removeIf(e->now-e.getValue()>600000);
        if(requested.size()>=10000 && !requested.containsKey(key))return;
        boolean[] accepted={false};
        requested.compute(key,(k,last)->{if(last==null || now-last>=60000){accepted[0]=true;return now;}return last;});
        if(requested.size()>10000)requested.entrySet().removeIf(e->now-e.getValue()>600000);
        if(!accepted[0])return;
        User user=users.findByEmail(email).orElse(null);
        if(user==null || !Boolean.TRUE.equals(user.getStatus()))return;
        em.refresh(user,LockModeType.PESSIMISTIC_WRITE);
        if(!Boolean.TRUE.equals(user.getStatus()))return;
        byte[] bytes=new byte[32];random.nextBytes(bytes);String raw=HexFormat.of().formatHex(bytes);
        PasswordResetToken token=new PasswordResetToken();token.setUserId(user.getUserId());
        token.setTokenHash(hash(raw));token.setExpiresAt(Instant.now().plusSeconds(900));tokens.saveAndFlush(token);
        user.setToken(null);users.save(user);
        String body="<h2>Đặt lại mật khẩu VinaBook</h2><p>Liên kết có hiệu lực 15 phút và chỉ dùng một lần.</p><a href=\""
            +publicBase+"/forgot-password/"+raw+"\">Đặt lại mật khẩu</a><p>Nếu bạn không yêu cầu, hãy bỏ qua email này.</p>";
        // The encrypted outbox row and token commit together; SMTP runs separately.
        mail.queue(email,"Đặt lại mật khẩu VinaBook",body);
    }
    @Transactional(readOnly=true)
    public Optional<User> validUser(String raw) {
        if(!validFormat(raw))return Optional.empty();
        return tokens.findByTokenHash(hash(raw)).filter(t->t.getExpiresAt().isAfter(Instant.now()))
            .flatMap(t->users.findById(t.getUserId())).filter(u->Boolean.TRUE.equals(u.getStatus()));
    }
    @Transactional
    public boolean consume(String raw,String password) {
        if(!validFormat(raw) || password==null || password.length()<6 || password.length()>128)return false;
        String digest=hash(raw);
        PasswordResetToken candidate=tokens.findByTokenHash(digest).orElse(null);
        if(candidate==null)return false;
        User user=users.findById(candidate.getUserId()).orElse(null);
        if(user==null)return false;
        // Both issuing and consuming lock the user first, avoiding opposite lock order.
        em.refresh(user,LockModeType.PESSIMISTIC_WRITE);
        if(!Boolean.TRUE.equals(user.getStatus()))return false;
        PasswordResetToken token=tokens.lockByHash(digest).orElse(null);
        if(token==null || !token.getUserId().equals(user.getUserId()))return false;
        em.refresh(token,LockModeType.PESSIMISTIC_WRITE);
        if(!digest.equals(token.getTokenHash()) || !token.getExpiresAt().isAfter(Instant.now()))return false;
        user.setPassword(encoder.encode(password));user.setToken(null);users.save(user);
        tokens.delete(token);tokens.flush();return true;
    }
    private boolean validFormat(String value){return value!=null && value.matches("[a-f0-9]{64}");}
    static String hash(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
