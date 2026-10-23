/*
 * (C) Copyright 2022. All Rights Reserved.
 *
 * @author DongTHD
 * @date Mar 10, 2022
*/
package vn.fs.service.implement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import vn.fs.dto.MailInfo;
import vn.fs.service.SendMailService;

@Service
public class SendMailServiceImplement implements SendMailService {

	@Autowired
	JavaMailSender sender;

    @Autowired private vn.fs.service.MailOutboxStore outbox;
    @Autowired(required=false) private vn.fs.automation.AutomationDeliveryGuard automationGuard;
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(SendMailServiceImplement.class);

	@Override
	public void send(MailInfo mail) throws MessagingException, IOException {
		// Tạo message
		MimeMessage message = sender.createMimeMessage();
		// Sử dụng Helper để thiết lập các thông tin cần thiết cho message
		MimeMessageHelper helper = new MimeMessageHelper(message, true, "utf-8");
		helper.setFrom(mail.getFrom());
		helper.setTo(mail.getTo());
		helper.setSubject(mail.getSubject());
		helper.setText(mail.getBody(), true);
		helper.setReplyTo(mail.getFrom());

		if (mail.getAttachments() != null) {
			FileSystemResource file = new FileSystemResource(new File(mail.getAttachments()));
			helper.addAttachment(mail.getAttachments(), file);
		}

		// Gửi message đến SMTP server
		sender.send(message);

	}

	@Override
	public void queue(MailInfo mail) {
        outbox.enqueue(mail);
	}

	@Override
	public void queue(String to, String subject, String body) {
		queue(new MailInfo(to, subject, body));
	}

	@Override
	@Scheduled(fixedDelay = 5000)
	public void run() {
        for(int i=0;i<5;i++) {
            var pending=outbox.claim();if(pending.isEmpty())break;
            var job=pending.get();boolean sent=false;
            if(automationGuard!=null && !automationGuard.allow(job.id())){outbox.discard(job);continue;}
            try {this.send(job.mail());sent=true;}
            catch(Exception failed){log.warn("mail_delivery_retry job={} exceptionType={}",job.id(),failed.getClass().getSimpleName());}
            outbox.finish(job,sent);
        }
	}

}
