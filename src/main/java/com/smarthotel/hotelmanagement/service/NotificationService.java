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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Sends post-booking notifications: email (SMTP) and WhatsApp (Twilio).
 * All sends are asynchronous so booking response is not blocked.
 * Credentials from environment variables / application.properties; no hardcoding.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}$");
    /** E.164: leading +, then 10–15 digits (Twilio expects +... usually). */
    private static final Pattern PHONE_E164_PATTERN = Pattern.compile("^\\+[0-9]{10,15}$");

    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.mail.from:}")
    private String mailFrom;

    @Value("${app.mail.fromName:Smart Hotel}")
    private String mailFromName;

    @Value("${brevo.api.key:}")
    private String brevoApiKey;

    @Value("${brevo.api.url:https://api.brevo.com/v3/smtp/email}")
    private String brevoApiUrl;

    @Value("${app.twilio.account-sid:}")
    private String twilioAccountSid;

    @Value("${app.twilio.auth-token:}")
    private String twilioAuthToken;

    @Value("${app.twilio.whatsapp-from:}")
    private String twilioWhatsAppFrom;

    @Value("${app.support.email:support@smarthotel.com}")
    private String supportEmail;

    @Value("${app.support.phone:}")
    private String supportPhone;

    @Value("${app.whatsapp.sandbox-join:join watch-swept}")
    private String whatsappSandboxJoin;

    // In WhatsApp Sandbox, "business initiated" messages can fail unless the recipient joined the sandbox.
    // For registration, we primarily rely on the email onboarding instruction instead.
    @Value("${app.whatsapp.send-registration:false}")
    private boolean sendRegistrationWhatsApp;

    @Value("${app.twilio.registration-template-content-sid:}")
    private String registrationTemplateContentSid;

    @Value("${app.twilio.registration-template-variable-key:}")
    private String registrationTemplateVariableKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public NotificationService(JavaMailSender mailSender, RestTemplate restTemplate) {
        this.mailSender = mailSender;
        this.restTemplate = restTemplate;
    }

    /**
     * Sends welcome email and WhatsApp after registration (to real email and phone).
     * Call after user is saved. Runs async so registration response is not delayed.
     */
    @Async
    public void sendRegistrationWelcomeAsync(String fullName, String email, String phone) {
        if (!StringUtils.hasText(email) || !isValidEmail(email)) return;
        sendRegistrationEmail(fullName != null ? fullName : "Guest", email);
        if (!sendRegistrationWhatsApp) {
            log.debug("Registration WhatsApp skipped: app.whatsapp.send-registration=false");
            return;
        }
        if (StringUtils.hasText(phone)) {
            String normalized = normalizePhone(phone);
            if (isValidE164Phone(normalized)) {
                // Sandbox: if a Content Template is configured, use it for business-initiated messages.
                // Otherwise fall back to free-form "Body" (may require user to "join" / be within window).
                if (StringUtils.hasText(registrationTemplateContentSid)) {
                    sendWhatsAppTemplateMessage(normalized, fullName, registrationTemplateContentSid, registrationTemplateVariableKey);
                } else {
                    sendWhatsAppMessage(normalized, buildRegistrationWhatsAppMessage(fullName));
                }
            } else {
                log.warn("Invalid phone for registration WhatsApp: {}", phone);
            }
        }
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
        // WhatsApp sandbox "From" number (US) used for onboarding chats.
        // Format typically: "whatsapp:+14155238886"
        String sandboxFrom = StringUtils.hasText(twilioWhatsAppFrom) ? twilioWhatsAppFrom.trim() : "";
        String sandboxFromDisplay = sandboxFrom;
        // Make the default US sample number more readable: whatsapp:+1 4155238886
        if (sandboxFrom.startsWith("whatsapp:+") && sandboxFrom.endsWith("14155238886")) {
            sandboxFromDisplay = "whatsapp:+1 4155238886";
        }
        String joinInstruction = "To chat with our WhatsApp chatbot, send exactly: "
                + whatsappSandboxJoin
                + " to the WhatsApp number: "
                + sandboxFromDisplay;
        String joinBlock = StringUtils.hasText(sandboxFrom)
                ? joinInstruction
                : "Send \"" + whatsappSandboxJoin + "\" to our WhatsApp sandbox number (example: whatsapp:+14155238886).";

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
                  <p style="font-size:14px;line-height:1.9;margin:22px 0 10px;">
                    <strong style="color:#1a1410;">WhatsApp chatbot setup:</strong><br/>
                    %s
                  </p>
                  <p style="font-size:12px;line-height:1.8;margin:0;color:#9a8878;">
                    After sending the setup text above, you can chat with our bot anytime.
                  </p>
                  <div style="text-align:center;margin:26px 0 30px;">
                    <a href="http://localhost:8080" style="display:inline-block;background:#1a1410;color:#f5edd8;text-decoration:none;padding:14px 30px;font-size:11px;letter-spacing:2px;text-transform:uppercase;border:1px solid #3a2f22;">
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
                joinBlock,
                supportEmail,
                supportEmail,
                StringUtils.hasText(supportPhone) ? " Support phone: " + supportPhone : ""
        );
    }

    private String buildRegistrationWhatsAppMessage(String name) {
        String base = "Hello " + (name != null && !name.isBlank() ? name : "there") + " 👋 Welcome to Smart Hotel Management! Your account is ready. Log in to book rooms.";
        return base + "\n\nTo chat with our bot on WhatsApp, type and send exactly:\n" + whatsappSandboxJoin + "\n(Sandbox membership lasts 72 hours. You can rejoin anytime.)";
    }

    /**
     * Sends confirmation email and WhatsApp (if phone valid) asynchronously.
     * Call this after booking is successfully persisted. Failures are logged and do not affect the booking.
     */
    @Async
    public void sendBookingConfirmationAsync(BookingNotificationContext ctx) {
        if (ctx == null) return;
        sendConfirmationEmail(ctx);
        sendConfirmationWhatsApp(ctx);
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

    public void sendConfirmationWhatsApp(BookingNotificationContext ctx) {
        if (!StringUtils.hasText(twilioAccountSid) || !StringUtils.hasText(twilioAuthToken)
                || !StringUtils.hasText(twilioWhatsAppFrom)) {
            log.debug("Twilio WhatsApp not configured; skipping WhatsApp notification.");
            return;
        }
        String phone = ctx.guestPhone();
        if (!StringUtils.hasText(phone)) {
            log.debug("No guest phone for WhatsApp; skipping.");
            return;
        }
        String normalized = normalizePhone(phone);
        if (!isValidE164Phone(normalized)) {
            log.warn("Invalid guest phone for WhatsApp (use country code): {}", phone);
            return;
        }
        try {
            sendTwilioWhatsApp(normalized, buildWhatsAppMessage(ctx));
            log.info("WhatsApp confirmation sent to {}", normalized);
        } catch (Exception e) {
            log.error("Failed to send WhatsApp confirmation to {}: {}", normalized, e.getMessage());
        }
    }

    @Async
    public void sendCancellationNotificationAsync(BookingNotificationContext ctx, Double refundAmount) {
        if (ctx == null || !StringUtils.hasText(ctx.guestEmail()) || !isValidEmail(ctx.guestEmail())) return;
        sendCancellationEmail(ctx, refundAmount != null ? refundAmount : 0);
        if (StringUtils.hasText(ctx.guestPhone())) {
            String normalized = normalizePhone(ctx.guestPhone());
            if (isValidE164Phone(normalized)) {
                String body = "Your booking at " + ctx.hotelName() + " (Ref: " + ctx.bookingReference() + ") has been cancelled. Refund amount: ₹" + String.format("%.2f", refundAmount != null ? refundAmount : 0) + ". " + whatsappSandboxJoin;
                sendWhatsAppMessage(normalized, body);
            }
        }
    }

    private void sendCancellationEmail(BookingNotificationContext ctx, double refund) {
        String to = ctx.guestEmail();
        if (!StringUtils.hasText(to) || !isValidEmail(to)) return;
        String name = StringUtils.hasText(ctx.guestName()) ? ctx.guestName() : "Guest";
        String html = "<p>Dear " + name + ",</p><p>Your booking at " + ctx.hotelName() + " (Ref: " + ctx.bookingReference() + ") has been cancelled.</p><p><strong>Refund amount: ₹" + String.format("%.2f", refund) + "</strong></p><p>Thank you.</p>";
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

    /**
     * Send a WhatsApp message to a given number (e.g. for chatbot replies).
     * Used when the WhatsApp webhook receives an incoming message and we reply via Twilio API.
     * @param toE164OrWhatsapp From webhook: use as-is (e.g. "whatsapp:+919876543210"); or plain number with country code.
     * @param body Message text to send.
     */
    public void sendWhatsAppMessage(String toE164OrWhatsapp, String body) {
        if (!StringUtils.hasText(twilioAccountSid) || !StringUtils.hasText(twilioAuthToken)
                || !StringUtils.hasText(twilioWhatsAppFrom)) {
            log.debug("Twilio WhatsApp not configured; skipping send.");
            return;
        }
        if (!StringUtils.hasText(toE164OrWhatsapp) || !StringUtils.hasText(body)) {
            log.warn("Missing to or body for WhatsApp message; skipping.");
            return;
        }
        String normalized = normalizePhone(toE164OrWhatsapp);
        log.debug("WhatsApp send rawTo='{}' normalizedTo='{}'", toE164OrWhatsapp, normalized);
        if (!isValidE164Phone(normalized)) {
            log.warn("Invalid phone for WhatsApp (use country code, e.g. +91 for India): {}", toE164OrWhatsapp);
            return;
        }
        try {
            sendTwilioWhatsApp(normalized, body);
            log.info("WhatsApp message sent to {}", normalized);
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message to {}: {}", normalized, e.getMessage());
        }
    }

    private String buildEmailBody(BookingNotificationContext ctx) {
        String name = StringUtils.hasText(ctx.guestName()) ? ctx.guestName() : "Guest";
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
                    <a href="http://localhost:8080" style="display:inline-block;background:#1a1410;color:#f5edd8;text-decoration:none;padding:14px 30px;font-size:11px;letter-spacing:2px;text-transform:uppercase;border:1px solid #3a2f22;">
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
                supportEmail,
                supportEmail,
                StringUtils.hasText(supportPhone) ? " Support phone: " + supportPhone : ""
            );
    }

    private String buildWhatsAppMessage(BookingNotificationContext ctx) {
        double total = ctx.totalCost() != null ? ctx.totalCost() : 0.0;
        String base = """
            Your booking is successful! Hello %s! Booking confirmed at %s. Check-in: %s, Check-out: %s. Ref: %s. BILL - Amount due: ₹%.2f. Thank you for choosing Smart Hotel Management.
            """.formatted(
                StringUtils.hasText(ctx.guestName()) ? ctx.guestName() : "Guest",
                ctx.hotelName(),
                ctx.checkInDate(),
                ctx.checkOutDate(),
                ctx.bookingReference(),
                total
            ).replaceAll("\\s+", " ").trim();
        return base + "\n\nTo chat with our bot on WhatsApp, type and send exactly:\n" + whatsappSandboxJoin + "\n(Sandbox membership lasts 72 hours. You can rejoin anytime.)";
    }

    private void sendTwilioWhatsApp(String toE164, String body) {
        String url = "https://api.twilio.com/2010-04-01/Accounts/" + twilioAccountSid + "/Messages.json";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("From", twilioWhatsAppFrom.startsWith("whatsapp:") ? twilioWhatsAppFrom : "whatsapp:" + twilioWhatsAppFrom);
        form.add("To", toE164.startsWith("whatsapp:") ? toE164 : "whatsapp:" + toE164);
        form.add("Body", body);

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBasicAuth(twilioAccountSid, twilioAuthToken);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);

        org.springframework.http.HttpEntity<MultiValueMap<String, String>> entity =
                new org.springframework.http.HttpEntity<>(form, headers);
        org.springframework.http.ResponseEntity<Map> response;
        try {
            response = restTemplate.postForEntity(url, entity, Map.class);
        } catch (HttpStatusCodeException e) {
            // Twilio returns useful error info (e.g. "To address is not allowed", invalid format, etc.)
            log.error("Twilio WhatsApp API rejected. to={}, httpStatus={}, responseBody={}",
                    toE164, e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
        Map<String, Object> responseBody = response.getBody();
        // Twilio usually returns: {"sid":"SM...","status":"queued", ...}
        if (responseBody != null && !responseBody.isEmpty()) {
            Object sid = responseBody.get("sid");
            Object status = responseBody.get("status");
            log.info("Twilio WhatsApp API accepted: sid={}, status={}, httpStatus={}",
                    sid, status, response.getStatusCodeValue());
        } else {
            log.info("Twilio WhatsApp API accepted: httpStatus={}", response.getStatusCodeValue());
        }
    }

    private void sendWhatsAppTemplateMessage(String toE164, String fullName, String contentSid, String variableKey) {
        if (!StringUtils.hasText(toE164) || !StringUtils.hasText(contentSid)) return;

        // Template variables are optional; if variableKey isn't provided, we send without ContentVariables.
        String contentVariablesJson = buildTemplateVariablesJson(variableKey, fullName);

        try {
            sendTwilioWhatsAppTemplate(toE164, contentSid, contentVariablesJson);
            log.info("WhatsApp registration template sent to {} (contentSid={})", toE164, contentSid);
        } catch (Exception e) {
            log.error("Failed to send WhatsApp registration template to {} (contentSid={}): {}",
                    toE164, contentSid, e.getMessage());
        }
    }

    private void sendTwilioWhatsAppTemplate(String toE164, String contentSid, String contentVariablesJson) {
        String url = "https://api.twilio.com/2010-04-01/Accounts/" + twilioAccountSid + "/Messages.json";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("From", twilioWhatsAppFrom.startsWith("whatsapp:") ? twilioWhatsAppFrom : "whatsapp:" + twilioWhatsAppFrom);
        form.add("To", toE164.startsWith("whatsapp:") ? toE164 : "whatsapp:" + toE164);
        form.add("ContentSid", contentSid);
        if (StringUtils.hasText(contentVariablesJson)) {
            form.add("ContentVariables", contentVariablesJson);
        }

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBasicAuth(twilioAccountSid, twilioAuthToken);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);

        org.springframework.http.HttpEntity<MultiValueMap<String, String>> entity =
                new org.springframework.http.HttpEntity<>(form, headers);

        org.springframework.http.ResponseEntity<Map> response;
        try {
            response = restTemplate.postForEntity(url, entity, Map.class);
        } catch (HttpStatusCodeException e) {
            // Twilio returns useful error info for template failures too.
            log.error("Twilio WhatsApp template API rejected. to={}, contentSid={}, httpStatus={}, responseBody={}",
                    toE164, contentSid, e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }

        Map<String, Object> responseBody = response.getBody();
        if (responseBody != null && !responseBody.isEmpty()) {
            Object sid = responseBody.get("sid");
            Object status = responseBody.get("status");
            log.info("Twilio WhatsApp template API accepted: sid={}, status={}, httpStatus={}",
                    sid, status, response.getStatusCodeValue());
        } else {
            log.info("Twilio WhatsApp template API accepted: httpStatus={}", response.getStatusCodeValue());
        }
    }

    private String buildTemplateVariablesJson(String variableKey, String fullName) {
        if (!StringUtils.hasText(variableKey) || !StringUtils.hasText(fullName)) return null;
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put(variableKey.trim(), fullName.trim());
        try {
            return objectMapper.writeValueAsString(vars);
        } catch (JsonProcessingException e) {
            log.warn("Failed to JSON-encode ContentVariables (variableKey={}, fullNameLen={}): {}",
                    variableKey, fullName.length(), e.getMessage());
            return null;
        }
    }

    private static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    private static boolean isValidE164Phone(String phone) {
        return phone != null && PHONE_E164_PATTERN.matcher(phone).matches();
    }

    private static String normalizePhone(String phone) {
        if (phone == null) return null;
        String normalized = phone.trim();
        if (normalized.regionMatches(true, 0, "whatsapp:", 0, 9)) {
            normalized = normalized.substring(9).trim();
        }

        // Convert international dialing prefix to E.164-like format.
        if (normalized.startsWith("00")) {
            normalized = "+" + normalized.substring(2);
        }

        // Keep '+' if present; otherwise assume digits.
        if (normalized.startsWith("+")) {
            String digits = normalized.substring(1).replaceAll("\\D", "");
            return "+" + digits;
        }

        String digits = normalized.replaceAll("\\D", "");
        if (digits.isEmpty()) return digits;

        // If India local with leading 0 (e.g. 0767...), strip it.
        if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }

        // India local number: 10 digits => +91...
        if (digits.length() == 10) {
            return "+91" + digits;
        }

        // If it already includes country code but user omitted '+', just add '+'.
        if (digits.length() >= 11 && digits.length() <= 15) {
            return "+" + digits;
        }

        // Let validation fail for anything else (too short/too long).
        return digits;
    }
}
