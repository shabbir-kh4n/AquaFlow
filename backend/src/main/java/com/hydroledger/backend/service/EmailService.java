package com.hydroledger.backend.service;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.hydroledger.backend.entity.Resident;

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

        send(message, resident.getEmail(), "set-password");
    }

    public void sendInvoiceReadyEmail(Resident resident, String periodMonth, BigDecimal totalAmount, String link) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(resident.getEmail());
        message.setSubject("Your HydroLedger bill for " + periodMonth + " is ready");
        message.setText(
                "Hi " + resident.getFullName() + ",\n\n"
                        + "Your water bill for " + periodMonth + " has been generated: ₹" + totalAmount + ".\n\n"
                        + "View the full itemized breakdown here:\n\n"
                        + link + "\n\n"
                        + "If you have questions about this bill, contact your community admin."
        );

        send(message, resident.getEmail(), "invoice-ready");
    }

    public void sendAlertEmail(Resident resident, String alertTypeLabel, String message, String link) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(resident.getEmail());
        mailMessage.setSubject("HydroLedger alert: " + alertTypeLabel);
        mailMessage.setText(
                "Hi " + resident.getFullName() + ",\n\n"
                        + message + "\n\n"
                        + "View your alerts here:\n\n"
                        + link + "\n\n"
                        + "If you believe this is unexpected, contact your community admin."
        );

        send(mailMessage, resident.getEmail(), "alert");
    }

    private void send(SimpleMailMessage message, String recipient, String emailType) {
        try {
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("Failed to send {} email to {}", emailType, recipient, ex);
        }
    }
}
