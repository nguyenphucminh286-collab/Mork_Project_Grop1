package vn.edu.fpt.savora.service;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Spring Boot Email Service (Nâng cấp từ EmailUtils.java và config.properties của SE2034-SWP391-G6)
 */
@Service
public class SavoraEmailService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Value("${email.sender:facc6604@gmail.com}")
    private String senderEmail;

    @Value("${email.password:edav mted bvdh tjhu}")
    private String senderPassword;

    public void sendEmail(String to, String subject, String htmlContent) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.ssl.trust", "smtp.gmail.com");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        final String finalSender = (senderEmail != null && !senderEmail.isBlank())
                ? senderEmail.trim()
                : System.getenv("MODA_EMAIL_SENDER");
        final String finalPass = (senderPassword != null && !senderPassword.isBlank())
                ? senderPassword.trim()
                : System.getenv("MODA_EMAIL_PASSWORD");

        if (finalSender == null || finalSender.isBlank() || finalPass == null || finalPass.isBlank()) {
            throw new MessagingException("Missing Gmail SMTP credentials (email.sender / email.password).");
        }

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(finalSender, finalPass);
            }
        });

        Message email = new MimeMessage(session);
        email.setFrom(new InternetAddress(finalSender));
        email.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        email.setSubject(subject);
        email.setContent(htmlContent, "text/html; charset=UTF-8");

        Transport.send(email);
    }

    public void sendFeedbackConfirmationEmail(String userName, String userEmail, String userMessage) {
        String safeName = escapeHtml(userName != null && !userName.isBlank() ? userName.trim() : "Valued Guest");
        String safeEmail = escapeHtml(userEmail != null ? userEmail.trim() : "");
        String safeMessage = escapeHtml(userMessage != null ? userMessage.trim() : "");
        String submittedAt = LocalDateTime.now().format(DT_FMT);

        String subject = "We value your feedback and will get back to you soon! — Savora Fine Dining";

        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                </head>
                <body style="margin:0;padding:0;background-color:#f8f4ee;font-family:'Helvetica Neue',Helvetica,Arial,sans-serif;color:#241f1c;">
                    <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f8f4ee;padding:32px 16px;">
                        <tr>
                            <td align="center">
                                <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%%;background-color:#ffffff;border-radius:18px;overflow:hidden;border:1px solid #e6ded3;box-shadow:0 12px 32px rgba(24,21,18,0.08);">
                                    <!-- Top Luxury Header -->
                                    <tr>
                                        <td style="background:linear-gradient(135deg,#181512 0%%,#3d2314 100%%);padding:28px 32px;text-align:center;color:#fff8f0;">
                                            <div style="font-size:11px;font-weight:700;letter-spacing:2.5px;color:#d4a373;text-transform:uppercase;margin-bottom:6px;">
                                                ✦ SAVORA FINE DINING &amp; LOUNGE ✦
                                            </div>
                                            <h1 style="margin:0;font-family:Georgia,serif;font-size:24px;font-weight:700;color:#ffffff;letter-spacing:0.5px;">
                                                Guest Feedback Confirmation
                                            </h1>
                                            <div style="font-size:12px;color:#dfd5c7;margin-top:6px;">
                                                Hoa Lac Hi-Tech Park, Hanoi • Concierge Hotline: 1900 6868
                                            </div>
                                        </td>
                                    </tr>

                                    <!-- Main Body -->
                                    <tr>
                                        <td style="padding:30px 32px;">
                                            <p style="margin:0 0 14px;font-size:16px;font-weight:700;color:#241f1c;">
                                                Dear %s,
                                            </p>

                                            <!-- Highlighted Required Message -->
                                            <div style="background-color:#fff8f0;border-left:4px solid #b85c38;padding:16px 20px;border-radius:10px;margin:16px 0 22px;">
                                                <p style="margin:0;font-size:17px;font-weight:700;color:#8f4223;line-height:1.45;">
                                                    “We value your feedback and will get back to you soon!”
                                                </p>
                                            </div>

                                            <p style="margin:0 0 18px;font-size:14px;line-height:1.65;color:#4a423d;">
                                                Thank you for taking the time to share your thoughts with <strong>Savora Fine Dining &amp; Lounge</strong>. Our Executive Concierge &amp; Culinary Team have received your message and are reviewing it carefully.
                                            </p>

                                            <!-- Feedback Summary Card -->
                                            <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#faf6f0;border:1px solid #e6ded3;border-radius:12px;padding:16px 20px;margin-bottom:22px;">
                                                <tr>
                                                    <td style="padding-bottom:8px;font-size:12px;font-weight:700;color:#8f4223;text-transform:uppercase;letter-spacing:0.8px;">
                                                        📋 Your Submitted Feedback Summary
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td style="font-size:13px;color:#241f1c;padding:4px 0;">
                                                        <strong>Guest Name:</strong> %s
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td style="font-size:13px;color:#241f1c;padding:4px 0;">
                                                        <strong>Email Address:</strong> %s
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td style="font-size:13px;color:#241f1c;padding:4px 0;">
                                                        <strong>Submitted At:</strong> %s
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td style="font-size:13px;color:#241f1c;padding:10px 0 4px;border-top:1px dashed #dcd2c6;margin-top:6px;">
                                                        <strong>Your Message:</strong>
                                                        <div style="margin-top:6px;padding:10px 12px;background:#ffffff;border-radius:8px;border:1px solid #e6ded3;color:#3d3530;font-style:italic;line-height:1.55;">
                                                            “%s”
                                                        </div>
                                                    </td>
                                                </tr>
                                            </table>

                                            <p style="margin:0;font-size:13px;line-height:1.6;color:#6e655f;">
                                                Warmest regards,<br>
                                                <strong style="color:#241f1c;">Savora Guest Relations &amp; Executive Concierge</strong>
                                            </p>
                                        </td>
                                    </tr>

                                    <!-- Footer -->
                                    <tr>
                                        <td style="background-color:#181512;padding:18px 32px;text-align:center;font-size:12px;color:#b4a99c;">
                                            © 2026 Savora Fine Dining &amp; Lounge • Hoa Lac Hi-Tech Park, Thach That, Hanoi<br>
                                            Open Daily: 10:00 AM – 10:30 PM • Hotline: <strong style="color:#f3c68f;">1900 6868</strong>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(safeName, safeName, safeEmail, submittedAt, safeMessage);

        try {
            sendEmail(userEmail.trim(), subject, html);
        } catch (MessagingException e) {
            throw new IllegalArgumentException("Unable to send confirmation email: " + e.getMessage());
        }
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
