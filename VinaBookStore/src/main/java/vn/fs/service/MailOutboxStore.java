package vn.fs.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.fs.dto.MailInfo;
import vn.fs.entity.MailOutbox;
import vn.fs.repository.MailOutboxRepository;

/** Durable delivery jobs; recipient, reset links and message bodies are encrypted at rest. */
@Service
public class MailOutboxStore {
    public record Delivery(String id,String claimToken,MailInfo mail) {}
    private final MailOutboxRepository rows;
    private final ObjectMapper mapper;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private vn.fs.automation.AutomationNoticeRepository automationNotices;
    private final SecretKeySpec key;
    private final SecureRandom random=new SecureRandom();
    public MailOutboxStore(MailOutboxRepository rows,ObjectMapper mapper,String secret){this(rows,mapper,secret,secret);}
    @org.springframework.beans.factory.annotation.Autowired
    public MailOutboxStore(MailOutboxRepository rows,ObjectMapper mapper,
            @Value("${mail.outbox.key:}") String configured,@Value("${bezkoder.app.jwtSecret}") String jwtSecret) {
        this.rows=rows;this.mapper=mapper;
        String secret=configured==null || configured.isBlank()?jwtSecret:configured;
        if(secret==null || secret.length()<32)throw new IllegalArgumentException("Mail outbox key must have at least 32 characters");
        try{key=new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(("vinabook-mail-outbox:"+secret).getBytes(StandardCharsets.UTF_8)),"AES");}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    @Transactional public void enqueue(MailInfo mail) { enqueueJob(mail); }
    public String enqueueJob(MailInfo mail) {
        if(rows.countByStateIn(List.of("PENDING","SENDING"))>=1000)throw new IllegalStateException("Mail outbox capacity reached");
        var row=new MailOutbox();row.setId(UUID.randomUUID().toString());
        row.setCreatedAt(System.currentTimeMillis());row.setDueAt(row.getCreatedAt());row.setPayload(encrypt(mail));rows.save(row);return row.getId();
    }
    @Transactional public Optional<Delivery> claim() {
        long now=System.currentTimeMillis();var ready=rows.ready(now,now-300000,PageRequest.of(0,1));
        if(ready.isEmpty())return Optional.empty();
        var row=ready.get(0);
        if(row.getAttempts()>=3){row.setState("FAILED");row.setPayload("");automationDelivery(row.getId(),"FAILED");return Optional.empty();}
        MailInfo mail;
        try{mail=decrypt(row.getPayload());}
        catch(RuntimeException corrupt){row.setState("FAILED");row.setPayload("");automationDelivery(row.getId(),"FAILED");return Optional.empty();}
        row.setState("SENDING");row.setClaimedAt(now);row.setClaimToken(UUID.randomUUID().toString());row.setAttempts(row.getAttempts()+1);
        return Optional.of(new Delivery(row.getId(),row.getClaimToken(),mail));
    }
    @Transactional public void finish(Delivery job,boolean sent) {
        rows.locked(job.id()).filter(row->"SENDING".equals(row.getState()) && job.claimToken().equals(row.getClaimToken())).ifPresent(row->{
            row.setState(sent?"SENT":row.getAttempts()>=3?"FAILED":"PENDING");
            row.setDueAt(System.currentTimeMillis()+5000L*(1L<<Math.min(row.getAttempts(),3)));
            row.setClaimToken(null);if(sent || "FAILED".equals(row.getState()))row.setPayload("");
            automationDelivery(row.getId(),sent?"SENT":"FAILED".equals(row.getState())?"FAILED":"QUEUED");
        });
    }
    @Transactional public void discard(Delivery job) {
        rows.locked(job.id()).filter(row->"SENDING".equals(row.getState()) && job.claimToken().equals(row.getClaimToken())).ifPresent(row->{row.setState("CANCELLED");row.setPayload("");row.setClaimToken(null);});
    }
    private void automationDelivery(String id,String state){if(automationNotices!=null)automationNotices.findByMailJobId(id).ifPresent(n->{n.setEmailState(state);automationNotices.save(n);});}
    @Scheduled(fixedDelay=3600000,initialDelay=3600000) @Transactional
    public void purge(){rows.purge(System.currentTimeMillis()-7L*86400000);}
    private String encrypt(MailInfo mail) {
        try {
            byte[] iv=new byte[12];random.nextBytes(iv);var cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));
            byte[] body=cipher.doFinal(mapper.writeValueAsBytes(mail));byte[] result=Arrays.copyOf(iv,iv.length+body.length);
            System.arraycopy(body,0,result,iv.length,body.length);return Base64.getEncoder().encodeToString(result);
        }catch(Exception invalid){throw new IllegalStateException("Cannot queue email",invalid);}
    }
    private MailInfo decrypt(String encoded) {
        try {
            byte[] bytes=Base64.getDecoder().decode(encoded);if(bytes.length<28)throw new IllegalArgumentException();
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOf(bytes,12)));
            return mapper.readValue(cipher.doFinal(Arrays.copyOfRange(bytes,12,bytes.length)),MailInfo.class);
        }catch(Exception invalid){throw new IllegalStateException("Cannot restore email job",invalid);}
    }
}
