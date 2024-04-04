package com.startingblock.domain.mail.application;

import com.startingblock.domain.mail.dto.MailRequestDto;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.UnsupportedEncodingException;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    public void sendEmailToReceiver(final MailRequestDto dto) throws MessagingException, UnsupportedEncodingException {

        MimeMessage message = mailSender.createMimeMessage();

        message.addRecipients(MimeMessage.RecipientType.TO, dto.getEmail());
        message.setFrom(new InternetAddress("startingblock.synapse@gmail.com", "스타팅블록"));
        message.setSubject("공고에 대한 새로운 문의가 있습니다.");
        message.setText(setMailContext(dto.getAnnouncement(), dto.getLink()), "utf-8", "html");  // 내용설정
        log.info(dto.getEmail() + "에 메일 전송");
        mailSender.send(message);
    }

    private String setMailContext(final String announcement, final String link) { // 타임리프 설정하는 코드
        Context context = new Context();
        context.setVariable("announcement", announcement); // Template에 전달할 데이터 설정
        context.setVariable("link", link);
        return templateEngine.process("mail", context); // mail.html
    }
}
