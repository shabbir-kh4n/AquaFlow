package com.hydroledger.backend.service;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.hydroledger.backend.entity.Resident;

import jakarta.mail.internet.MimeMessage;
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

    public void sendPasswordResetEmail(String recipientEmail, String recipientName, String link) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipientEmail);
        message.setSubject("Reset your HydroLedger password");
        message.setText(
                "Hi " + recipientName + ",\n\n"
                        + "We received a request to reset your HydroLedger password. Choose a new one here:\n\n"
                        + link + "\n\n"
                        + "This link expires in 1 hour and can be used once.\n\n"
                        + "If you didn't ask for this, you can ignore this email - your password won't change."
        );

        send(message, recipientEmail, "password-reset");
    }

    public void sendInvoiceReadyEmail(Resident resident, String periodMonth, BigDecimal totalAmount, String link,
            byte[] invoicePdf) {
        String text =
                "Hi " + resident.getFullName() + ",\n\n"
                        + "Your water bill for " + periodMonth + " has been generated: ₹" + totalAmount + ".\n\n"
                        + "The itemized invoice is attached as a PDF, or view it online here:\n\n"
                        + link + "\n\n"
                        + "If you have questions about this bill, contact your community admin.";

        if (invoicePdf == null || invoicePdf.length == 0) {
            // Defensive fallback only - PDF generation isn't expected to fail independently of the
            // invoice data that's already in hand at this point, but never let a rendering issue
            // suppress the "your bill is ready" notification entirely.
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(resident.getEmail());
            message.setSubject("Your HydroLedger bill for " + periodMonth + " is ready");
            message.setText(text);
            send(message, resident.getEmail(), "invoice-ready");
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
            helper.setTo(resident.getEmail());
            helper.setSubject("Your HydroLedger bill for " + periodMonth + " is ready");
            helper.setText(text);
            helper.addAttachment("invoice-" + periodMonth + ".pdf", new ByteArrayResource(invoicePdf));
            mailSender.send(mimeMessage);
        } catch (Exception ex) {
            log.error("Failed to send invoice-ready email to {}", resident.getEmail(), ex);
        }
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

    public void sendSupportTicketUpdateEmail(String recipientEmail, String recipientName, String subject,
            String updateMessage, String link) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(recipientEmail);
        mailMessage.setSubject("HydroLedger support ticket update: " + subject);
        mailMessage.setText(
                "Hi " + recipientName + ",\n\n"
                        + updateMessage + "\n\n"
                        + "View your ticket here:\n\n"
                        + link
        );

        send(mailMessage, recipientEmail, "support-ticket-update");
    }

    private void send(SimpleMailMessage message, String recipient, String emailType) {
        try {
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("Failed to send {} email to {}", emailType, recipient, ex);
        }
    }
}
