package com.smarthotel.hotelmanagement.service;

import com.smarthotel.hotelmanagement.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Sends login success notification by email.
 * Email: configure spring.mail.* in application.properties.
 */
@Service
public class LoginNotificationService {

    private static final String EMAIL_SUBJECT = "Login Successful – Smart Hotel Management System";

    private final NotificationService notificationService;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    public LoginNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Sends email after successful login.
     * Runs asynchronously so login response is not delayed.
     */
    @Async
    public void sendLoginNotifications(User user) {
        sendLoginNotifications(user, null);
    }

    @Async
    public void sendLoginNotifications(User user, String appBaseUrlOverride) {
        if (user == null) return;
        String name = user.getFullName() != null ? user.getFullName() : "Guest";
        String email = user.getEmail();

        sendLoginEmail(name, email, appBaseUrlOverride);
    }

    private void sendLoginEmail(String name, String toEmail, String appBaseUrlOverride) {
        if (toEmail == null || toEmail.isBlank()) return;
        notificationService.sendEmailUsingConfiguredProvider(toEmail, EMAIL_SUBJECT, buildLoginEmailBody(name, appBaseUrlOverride), "login");
    }

    private String buildLoginEmailBody(String name, String appBaseUrlOverride) {
        String safeName = StringUtils.hasText(name) ? name.trim() : "Guest";
        String appUrl = resolveAppBaseUrl(appBaseUrlOverride);
        return """
            <div style="margin:0;padding:0;background:#0f0c09;font-family:Arial,sans-serif;">
              <div style="max-width:640px;margin:0 auto;background:#faf8f4;">
                <div style="background:#1a1410;padding:28px 32px;border-bottom:1px solid #3a2f22;">
                  <div style="font-family:Georgia,serif;font-size:28px;letter-spacing:2px;color:#f5edd8;">SMART HOTEL</div>
                  <div style="font-size:11px;letter-spacing:3px;color:#c9a96e;text-transform:uppercase;">Management · Est. 2024</div>
                </div>
                <div style="background:linear-gradient(135deg,#1a1208 0%%,#2e1f0e 45%%,#0e0c0a 100%%);padding:34px 32px;">
                  <div style="font-size:11px;letter-spacing:3px;color:#c9a96e;text-transform:uppercase;">Login Successful</div>
                  <h1 style="margin:10px 0 0;font-family:Georgia,serif;font-size:42px;line-height:1.1;color:#f5edd8;font-weight:400;">
                    Welcome back,<br/><span style="font-style:italic;color:#c9a96e;">Distinguished Guest</span>
                  </h1>
                </div>
                <div style="padding:34px 32px;color:#4a3f35;">
                  <p style="font-family:Georgia,serif;font-size:30px;line-height:1.2;margin:0 0 14px;color:#1a1410;">
                    Dear <span style="font-style:italic;color:#8b6a3e;">%s</span>,
                  </p>
                  <p style="font-size:15px;line-height:1.9;margin:0 0 14px;">
                    Thank you for logging in to Smart Hotel Management System. Your account is active and ready to help you discover premium stays and seamless bookings.
                  </p>
                  <p style="font-size:15px;line-height:1.9;margin:0 0 20px;">
                    If this login was not initiated by you, please contact our support team immediately.
                  </p>
                  <div style="text-align:center;margin:26px 0 30px;">
                    <a href="%s" style="display:inline-block;background:#1a1410;color:#f5edd8;text-decoration:none;padding:14px 30px;font-size:11px;letter-spacing:2px;text-transform:uppercase;border:1px solid #3a2f22;">
                      Continue to Smart Hotel
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
                  <a href="mailto:support@smarthotel.com" style="font-size:12px;color:#c9a96e;text-decoration:none;letter-spacing:1px;">support@smarthotel.com</a>
                  <div style="margin-top:10px;font-size:10px;color:#4a3f35;line-height:1.6;">Automated security notification from Smart Hotel Management.</div>
                </div>
              </div>
            </div>
            """.formatted(safeName, appUrl);
    }

    private String normalizeAppBaseUrl() {
        String base = StringUtils.hasText(appBaseUrl) ? appBaseUrl.trim() : "http://localhost:8080";
        return base.replaceAll("/+$", "");
    }

    private String resolveAppBaseUrl(String appBaseUrlOverride) {
        if (StringUtils.hasText(appBaseUrlOverride)) {
            String candidate = appBaseUrlOverride.trim();
            if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
                return candidate.replaceAll("/+$", "");
            }
        }
        return normalizeAppBaseUrl();
    }

}
