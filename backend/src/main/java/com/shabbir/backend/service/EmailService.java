package com.shabbir.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.shabbir.backend.entity.Resident;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    public void sendSetPasswordEmail(Resident resident, String link) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(resident.getEmail());
        message.setSubject("Set your HydroLedger password");
        message.setText(
                "Hi " + resident.getFullName() + ",\n\n"
                        + "Your community admin has added you to HydroLedger. Set your password to activate your account:\n\n"
                        + link + "\n\n"
                        + "This link expires in 24 hours.\n\n"
                        + "If you weren't expecting this, you can ignore this email."
        );

        try {
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("Failed to send set-password email to {}", resident.getEmail(), ex);
        }
    }
}
