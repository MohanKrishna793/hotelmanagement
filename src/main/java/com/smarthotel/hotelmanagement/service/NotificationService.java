package com.smarthotel.hotelmanagement.service;

import com.smarthotel.hotelmanagement.dto.BookingNotificationContext;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Sends email notifications for registration, login-related events, and bookings.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}$");

    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.mail.from:}")
    private String mailFrom;

    @Value("${app.mail.fromName:Smart Hotel}")
    private String mailFromName;

    @Value("${app.base-url:https://hotelmanagement-production-o2db.up.railway.app}")
    private String appBaseUrl;

    @Value("${brevo.api.key:}")
    private String brevoApiKey;

    @Value("${brevo.api.url:https://api.brevo.com/v3/smtp/email}")
    private String brevoApiUrl;

    @Value("${app.support.email:support@smarthotel.com}")
    private String supportEmail;

    @Value("${app.support.phone:}")
    private String supportPhone;

    public NotificationService(JavaMailSender mailSender, RestTemplate restTemplate) {
        this.mailSender = mailSender;
        this.restTemplate = restTemplate;
    }

    @Async
    public void sendRegistrationWelcomeAsync(String fullName, String email, String phone) {
        if (!StringUtils.hasText(email) || !isValidEmail(email)) return;
        sendRegistrationEmail(fullName != null ? fullName : "Guest", email);
    }

    private void sendRegistrationEmail(String name, String toEmail) {
        sendEmailUsingConfiguredProvider(
                toEmail,
                "Welcome to Smart Hotel Management",
                buildRegistrationEmailBody(name),
                "registration welcome"
        );
    }

    private String buildRegistrationEmailBody(String name) {
        String safeName = StringUtils.hasText(name) ? name.trim() : "Guest";
        String appUrl = normalizeAppBaseUrl();
        return """
            <div style="margin:0;padding:0;background:#0f0c09;font-family:Arial,sans-serif;">
              <div style="max-width:640px;margin:0 auto;background:#faf8f4;">
                <div style="background:#1a1410;padding:28px 32px;border-bottom:1px solid #3a2f22;">
                  <div style="font-family:Georgia,serif;font-size:28px;letter-spacing:2px;color:#f5edd8;">SMART HOTEL</div>
                  <div style="font-size:11px;letter-spacing:3px;color:#c9a96e;text-transform:uppercase;">Management · Est. 2024</div>
                </div>
                <div style="background:linear-gradient(135deg,#1a1208 0%%,#2e1f0e 45%%,#0e0c0a 100%%);padding:34px 32px;">
                  <div style="font-size:11px;letter-spacing:3px;color:#c9a96e;text-transform:uppercase;">Registration Successful</div>
                  <h1 style="margin:10px 0 0;font-family:Georgia,serif;font-size:42px;line-height:1.1;color:#f5edd8;font-weight:400;">
                    Welcome,<br/><span style="font-style:italic;color:#c9a96e;">Distinguished Guest</span>
                  </h1>
                </div>
                <div style="padding:34px 32px;color:#4a3f35;">
                  <p style="font-family:Georgia,serif;font-size:30px;line-height:1.2;margin:0 0 14px;color:#1a1410;">
                    Dear <span style="font-style:italic;color:#8b6a3e;">%s</span>,
                  </p>
                  <p style="font-size:15px;line-height:1.9;margin:0 0 14px;">
                    Thank you for registering with Smart Hotel Management. Your account is now active and ready for premium bookings.
                  </p>
                  <p style="font-size:15px;line-height:1.9;margin:0 0 20px;">
                    You can log in to discover curated hotels, make reservations, and manage your trips seamlessly.
                  </p>
                  <div style="text-align:center;margin:26px 0 30px;">
                    <a href="%s" style="display:inline-block;background:#1a1410;color:#f5edd8;text-decoration:none;padding:14px 30px;font-size:11px;letter-spacing:2px;text-transform:uppercase;border:1px solid #3a2f22;">
                      Explore & Book Now
                    </a>
                  </div>
                  <p style="font-style:italic;font-size:16px;line-height:1.6;color:#8b6a3e;margin:0 0 18px;">
                    "Every great journey begins with a trusted stay."
                  </p>
                  <p style="margin:0;font-size:12px;line-height:1.8;color:#9a8878;">
                    <strong style="color:#1a1410;">Smart Hotel Management Team</strong><br/>
                    Concierge · Reservations · Guest Relations
                  </p>
                </div>
                <div style="background:#1a1410;padding:20px 32px;text-align:center;">
                  <div style="font-size:10px;letter-spacing:2px;color:#7a6a5a;">Support</div>
                  <a href="mailto:%s" style="font-size:12px;color:#c9a96e;text-decoration:none;letter-spacing:1px;">%s</a>
                  <div style="margin-top:10px;font-size:10px;color:#4a3f35;line-height:1.6;">Automated welcome notification from Smart Hotel Management.%s</div>
                </div>
              </div>
            </div>
            """.formatted(
                safeName,
                appUrl,
                supportEmail,
                supportEmail,
                StringUtils.hasText(supportPhone) ? " Support phone: " + supportPhone : ""
        );
    }

    @Async
    public void sendBookingConfirmationAsync(BookingNotificationContext ctx) {
        if (ctx == null) return;
        sendConfirmationEmail(ctx);
    }

    public void sendConfirmationEmail(BookingNotificationContext ctx) {
        String email = ctx.guestEmail();
        if (!StringUtils.hasText(email) || !isValidEmail(email)) {
            log.warn("Invalid or missing guest email for notification: {}", email != null ? "present" : "null");
            return;
        }
        sendEmailUsingConfiguredProvider(
                email,
                "Booking Confirmation & Bill – Smart Hotel",
                buildEmailBody(ctx),
                "booking confirmation"
        );
    }

    @Async
    public void sendCancellationNotificationAsync(BookingNotificationContext ctx, Double refundAmount) {
        if (ctx == null || !StringUtils.hasText(ctx.guestEmail()) || !isValidEmail(ctx.guestEmail())) return;
        sendCancellationEmail(ctx, refundAmount != null ? refundAmount : 0);
    }

    private void sendCancellationEmail(BookingNotificationContext ctx, double refund) {
        String to = ctx.guestEmail();
        if (!StringUtils.hasText(to) || !isValidEmail(to)) return;
        String name = StringUtils.hasText(ctx.guestName()) ? ctx.guestName() : "Guest";
        String appUrl = normalizeAppBaseUrl();
        String bookingRef = StringUtils.hasText(ctx.bookingReference()) ? ctx.bookingReference() : "-";
        String hotelName = StringUtils.hasText(ctx.hotelName()) ? ctx.hotelName() : "your selected hotel";
        String html = """
            <div style="margin:0;padding:0;background:#0f0c09;font-family:Arial,sans-serif;">
              <div style="max-width:640px;margin:0 auto;background:#faf8f4;">
                <div style="background:#1a1410;padding:28px 32px;border-bottom:1px solid #3a2f22;">
                  <div style="font-family:Georgia,serif;font-size:28px;letter-spacing:2px;color:#f5edd8;">SMART HOTEL</div>
                  <div style="font-size:11px;letter-spacing:3px;color:#c9a96e;text-transform:uppercase;">Management · Est. 2024</div>
                </div>
                <div style="background:linear-gradient(150deg,#2a0f0f 0%%,#3b1818 50%%,#0e0c0a 100%%);padding:30px 32px;">
                  <div style="font-size:11px;letter-spacing:3px;color:#f1a6a6;text-transform:uppercase;">Booking Cancelled</div>
                  <h1 style="margin:10px 0 0;font-family:Georgia,serif;font-size:40px;line-height:1.1;color:#f5edd8;font-weight:400;">
                    Reservation<br/><span style="font-style:italic;color:#f1a6a6;">Cancelled</span>
                  </h1>
                </div>
                <div style="padding:30px 32px;color:#4a3f35;">
                  <p style="font-family:Georgia,serif;font-size:28px;line-height:1.2;margin:0 0 14px;color:#1a1410;">
                    Dear <span style="font-style:italic;color:#8b6a3e;">%s</span>,
                  </p>
                  <p style="font-size:15px;line-height:1.9;margin:0 0 12px;">
                    Your booking at <strong>%s</strong> has been cancelled.
                  </p>
                  <p style="font-size:15px;line-height:1.9;margin:0 0 16px;">
                    Reference: <strong>%s</strong>
                  </p>
                  <div style="margin:18px 0 22px;padding:14px 16px;background:#f5f0e8;border:1px solid #e2d9c8;">
                    <div style="font-size:11px;letter-spacing:1px;text-transform:uppercase;color:#6f5a45;">Refund Amount</div>
                    <div style="margin-top:4px;font-size:26px;color:#1a1410;font-weight:700;">₹%.2f</div>
                  </div>
                  <div style="text-align:center;margin:24px 0 28px;">
                    <a href="%s" style="display:inline-block;background:#1a1410;color:#f5edd8;text-decoration:none;padding:14px 30px;font-size:11px;letter-spacing:2px;text-transform:uppercase;border:1px solid #3a2f22;">
                      Explore & Book Again
                    </a>
                  </div>
                  <p style="font-style:italic;font-size:16px;line-height:1.6;color:#8b6a3e;margin:0 0 16px;">
                    "We look forward to hosting you soon."
                  </p>
                  <p style="margin:0;font-size:12px;line-height:1.8;color:#9a8878;">
                    <strong style="color:#1a1410;">Smart Hotel Management Team</strong><br/>
                    Concierge · Reservations · Guest Relations
                  </p>
                </div>
                <div style="background:#1a1410;padding:20px 32px;text-align:center;">
                  <div style="font-size:10px;letter-spacing:2px;color:#7a6a5a;">Support</div>
                  <a href="mailto:%s" style="font-size:12px;color:#c9a96e;text-decoration:none;letter-spacing:1px;">%s</a>
                  <div style="margin-top:10px;font-size:10px;color:#4a3f35;line-height:1.6;">Automated cancellation notification from Smart Hotel Management.%s</div>
                </div>
              </div>
            </div>
            """.formatted(
                name,
                hotelName,
                bookingRef,
                refund,
                appUrl,
                supportEmail,
                supportEmail,
                StringUtils.hasText(supportPhone) ? " Support phone: " + supportPhone : ""
        );
        sendEmailUsingConfiguredProvider(to, "Booking Cancelled – Smart Hotel", html, "booking cancellation");
    }

    public boolean sendEmailUsingConfiguredProvider(String toEmail, String subject, String htmlBody, String context) {
        if (!StringUtils.hasText(toEmail) || !isValidEmail(toEmail)) return false;
        String effectiveFrom = StringUtils.hasText(mailFrom) ? mailFrom.trim()
                : (StringUtils.hasText(mailUsername) ? mailUsername.trim() : null);
        if (!StringUtils.hasText(effectiveFrom)) {
            log.warn("Email skipped ({}): MAIL_FROM/SMTP_USERNAME missing.", context);
            return false;
        }

        if (StringUtils.hasText(brevoApiKey)) {
            try {
                sendViaBrevoApi(effectiveFrom, toEmail, subject, htmlBody);
                log.info("{} email sent to {} via Brevo API", context, toEmail);
                return true;
            } catch (Exception e) {
                log.error("Brevo API send failed for {} to {}: {}", context, toEmail, e.getMessage());
            }
        }

        if (!StringUtils.hasText(mailUsername)) {
            log.warn("Email skipped ({}): SMTP_USERNAME missing and Brevo API unavailable.", context);
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(effectiveFrom, mailFromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("{} email sent to {} via SMTP", context, toEmail);
            return true;
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send {} email to {} via SMTP: {}", context, toEmail, e.getMessage());
            return false;
        }
    }

    private void sendViaBrevoApi(String fromEmail, String toEmail, String subject, String htmlBody) {
        String url = StringUtils.hasText(brevoApiUrl) ? brevoApiUrl : "https://api.brevo.com/v3/smtp/email";
        Map<String, Object> payload = Map.of(
                "sender", Map.of("name", StringUtils.hasText(mailFromName) ? mailFromName : "Smart Hotel", "email", fromEmail),
                "to", java.util.List.of(Map.of("email", toEmail)),
                "subject", subject,
                "htmlContent", htmlBody
        );
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("api-key", brevoApiKey.trim());
        org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(payload, headers);
        try {
            restTemplate.postForEntity(url, entity, Map.class);
        } catch (HttpStatusCodeException e) {
            log.error("Brevo API rejected email. status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    private String buildEmailBody(BookingNotificationContext ctx) {
        String name = StringUtils.hasText(ctx.guestName()) ? ctx.guestName() : "Guest";
        String appUrl = normalizeAppBaseUrl();
        double total = ctx.totalCost() != null ? ctx.totalCost() : 0.0;
        long nights = 0;
        if (ctx.checkInDate() != null && ctx.checkOutDate() != null) {
            nights = java.time.temporal.ChronoUnit.DAYS.between(ctx.checkInDate(), ctx.checkOutDate());
        }
        String checkInShort = ctx.checkInDate() != null ? ctx.checkInDate().toString() : "-";
        String checkOutShort = ctx.checkOutDate() != null ? ctx.checkOutDate().toString() : "-";
        String bookingRef = StringUtils.hasText(ctx.bookingReference()) ? ctx.bookingReference() : "-";
        return """
            <div style="margin:0;padding:0;background:#0f0c09;font-family:Arial,sans-serif;">
              <div style="max-width:640px;margin:0 auto;background:#faf8f4;">
                <div style="background:#1a1410;padding:28px 32px;border-bottom:1px solid #3a2f22;">
                  <div style="font-family:Georgia,serif;font-size:28px;letter-spacing:2px;color:#f5edd8;">SMART HOTEL</div>
                  <div style="font-size:11px;letter-spacing:3px;color:#c9a96e;text-transform:uppercase;">Management · Est. 2024</div>
                </div>

                <div style="background:linear-gradient(150deg,#1c1508 0%%,#2e1f0e 50%%,#0e0c0a 100%%);padding:30px 32px;">
                  <div style="font-size:11px;letter-spacing:3px;color:#c9a96e;text-transform:uppercase;">Booking Confirmation & Bill</div>
                  <h1 style="margin:10px 0 0;font-family:Georgia,serif;font-size:40px;line-height:1.1;color:#f5edd8;font-weight:400;">
                    Your stay is<br/><span style="font-style:italic;color:#c9a96e;">Reserved</span>
                  </h1>
                </div>

                <div style="padding:30px 32px;color:#4a3f35;">
                  <p style="font-family:Georgia,serif;font-size:28px;line-height:1.2;margin:0 0 14px;color:#1a1410;">
                    Dear <span style="font-style:italic;color:#8b6a3e;">%s</span>,
                  </p>
                  <p style="font-size:15px;line-height:1.9;margin:0 0 18px;">
                    Your booking has been confirmed. Please find your reservation details and billing summary below.
                  </p>

                  <table style="border-collapse:collapse;width:100%%;margin:20px 0;border:1px solid #e2d9c8;">
                    <tr style="background:#1a1410;color:#c9a96e;">
                      <td style="padding:12px 14px;font-size:11px;letter-spacing:1px;text-transform:uppercase;">Reservation Details</td>
                      <td style="padding:12px 14px;text-align:right;font-size:11px;letter-spacing:1px;">%s</td>
                    </tr>
                    <tr style="background:#f5f0e8;"><td style="padding:12px 14px;border-top:1px solid #e2d9c8;"><strong>Hotel</strong></td><td style="padding:12px 14px;border-top:1px solid #e2d9c8;">%s</td></tr>
                    <tr><td style="padding:12px 14px;border-top:1px solid #e2d9c8;"><strong>Check-in</strong></td><td style="padding:12px 14px;border-top:1px solid #e2d9c8;">%s</td></tr>
                    <tr><td style="padding:12px 14px;border-top:1px solid #e2d9c8;"><strong>Check-out</strong></td><td style="padding:12px 14px;border-top:1px solid #e2d9c8;">%s</td></tr>
                    <tr style="background:#f0ece3;"><td style="padding:12px 14px;border-top:1px solid #e2d9c8;"><strong>Nights</strong></td><td style="padding:12px 14px;border-top:1px solid #e2d9c8;">%d</td></tr>
                    <tr style="background:#1a1410;color:#c9a96e;"><td style="padding:12px 14px;border-top:1px solid #3a2f22;"><strong>Amount Due</strong></td><td style="padding:12px 14px;border-top:1px solid #3a2f22;font-size:20px;"><strong>₹%.2f</strong></td></tr>
                  </table>

                  <div style="text-align:center;margin:24px 0 28px;">
                    <a href="%s" style="display:inline-block;background:#1a1410;color:#f5edd8;text-decoration:none;padding:14px 30px;font-size:11px;letter-spacing:2px;text-transform:uppercase;border:1px solid #3a2f22;">
                      View My Booking
                    </a>
                  </div>

                  <p style="font-style:italic;font-size:16px;line-height:1.6;color:#8b6a3e;margin:0 0 16px;">
                    "We are honoured to be your host."
                  </p>
                  <p style="margin:0;font-size:12px;line-height:1.8;color:#9a8878;">
                    <strong style="color:#1a1410;">Smart Hotel Management Team</strong><br/>
                    Concierge · Reservations · Guest Relations
                  </p>
                </div>

                <div style="background:#1a1410;padding:20px 32px;text-align:center;">
                  <div style="font-size:10px;letter-spacing:2px;color:#7a6a5a;">For any queries</div>
                  <a href="mailto:%s" style="font-size:12px;color:#c9a96e;text-decoration:none;letter-spacing:1px;">%s</a>
                  <div style="margin-top:10px;font-size:10px;color:#4a3f35;line-height:1.6;">This is an automated message. Please do not reply directly.%s</div>
                </div>
            </div>
            """.formatted(
                name,
                bookingRef,
                ctx.hotelName(),
                checkInShort,
                checkOutShort,
                nights,
                total,
                appUrl,
                supportEmail,
                supportEmail,
                StringUtils.hasText(supportPhone) ? " Support phone: " + supportPhone : ""
            );
    }

    private String normalizeAppBaseUrl() {
        String base = StringUtils.hasText(appBaseUrl) ? appBaseUrl.trim() : "https://hotelmanagement-production-o2db.up.railway.app";
        return base.replaceAll("/+$", "");
    }

    private static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }
}
