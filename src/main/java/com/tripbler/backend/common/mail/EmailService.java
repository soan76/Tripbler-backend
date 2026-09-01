package com.tripbler.backend.common.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String senderEmail;

    public EmailService(
        JavaMailSender mailSender,
        @Value("${spring.mail.username}") String senderEmail
    ) {
        this.mailSender = mailSender;
        this.senderEmail = senderEmail;
    }

    // 아이디 찾기용 인증코드를 이메일로 발송한다.
    public void sendFindIdVerificationCode(
        String recipientEmail,
        String verificationCode
    ) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(recipientEmail);
        message.setSubject("[Tripbler] 아이디 찾기 인증코드");
        message.setText(
            "Tripbler 아이디 찾기 인증코드는 "
                + verificationCode
                + " 입니다.\n\n"
                + "인증코드는 5분 동안 유효합니다."
        );

        mailSender.send(message);
    }
}