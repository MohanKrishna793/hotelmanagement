package com.smarthotel.hotelmanagement.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Map;

/**
 * Stripe integration in TEST MODE: no real money is charged.
 * Sign up at https://dashboard.stripe.com/register (email only); get test keys from Developers → API keys.
 * No PAN/KYC required for test mode.
 */
@Service
public class StripePaymentService {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentService.class);

    /** Prevent the HTTP client from hanging indefinitely (which blocks the booking API and the UI). */
    private static final RequestOptions STRIPE_HTTP = RequestOptions.builder()
            .setConnectTimeout(12_000)
            .setReadTimeout(45_000)
            .build();

    @Value("${app.stripe.secret-key:}")
    private String secretKey;

    @Value("${app.stripe.publishable-key:}")
    private String publishableKey;

    @Value("${app.stripe.enabled:false}")
    private boolean enabled;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.stripe.webhook-secret:}")
    private String webhookSecret;

    @PostConstruct
    void init() {
        if (secretKey != null && !secretKey.isBlank()) {
            Stripe.apiKey = secretKey;
        }
    }

    public boolean isEnabled() {
        return enabled && secretKey != null && !secretKey.isBlank();
    }

    public String getPublishableKey() {
        return publishableKey != null ? publishableKey : "";
    }

    /**
     * Create Checkout Session <strong>before</strong> any DB booking row exists. All booking inputs are carried
     * in Stripe metadata and verified server-side after payment (amount + metadata).
     * This avoids {@code SELECT ... FOR UPDATE} on {@code rooms} until after the user returns from Stripe.
     */
    public SessionResult createCheckoutSessionForIntent(double amountInr, String customerEmail,
                                                        Map<String, String> bookingMetadata) {
        if (!isEnabled()) return null;
        long amountPaise = Math.round(amountInr * 100);
        if (amountPaise <= 0) return null;

        String successUrl = baseUrl.replaceAll("/$", "") + "/index.html?session_id={CHECKOUT_SESSION_ID}#payment-success";
        String cancelUrl = baseUrl.replaceAll("/$", "") + "/index.html#payment-cancelled";

        try {
            SessionCreateParams.LineItem.PriceData priceData = SessionCreateParams.LineItem.PriceData.builder()
                    .setCurrency("inr")
                    .setUnitAmount(amountPaise)
                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                            .setName("Room booking – Smart Hotel")
                            .build())
                    .build();

            SessionCreateParams.Builder b = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setCustomerEmail(customerEmail)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(priceData)
                            .build())
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl);

            Map<String, String> meta = bookingMetadata != null ? bookingMetadata : Map.of();
            for (Map.Entry<String, String> e : meta.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                // Server always sets amount/currency after this loop (do not trust client map).
                if ("total_paise".equalsIgnoreCase(e.getKey()) || "currency".equalsIgnoreCase(e.getKey())) {
                    continue;
                }
                String v = e.getValue();
                if (v.length() > 450) {
                    v = v.substring(0, 450);
                }
                b.putMetadata(e.getKey(), v);
            }
            b.putMetadata("total_paise", String.valueOf(amountPaise));
            b.putMetadata("currency", "inr");

            String idem = meta.get("idempotency_key");
            if (idem != null && !idem.isBlank()) {
                String ref = idem.length() > 200 ? idem.substring(0, 200) : idem;
                b.setClientReferenceId(ref);
            }

            Session session = Session.create(b.build(), STRIPE_HTTP);
            if (session != null) {
                return new SessionResult(session.getId(), session.getUrl());
            }
        } catch (StripeException e) {
            log.warn("Stripe create session failed: {}", e.getMessage());
        }
        return null;
    }

    /** Load a Checkout Session from Stripe (server-side secret key — never expose to the browser). */
    public Session retrieveCheckoutSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        try {
            return Session.retrieve(sessionId, STRIPE_HTTP);
        } catch (StripeException e) {
            log.warn("Stripe retrieve session failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * @deprecated Booking is created after payment; use {@link #retrieveCheckoutSession(String)} and metadata validation.
     */
    @Deprecated
    public Long verifySessionAndGetBookingId(String sessionId) {
        Session session = retrieveCheckoutSession(sessionId);
        if (session == null) return null;
        if (!"paid".equalsIgnoreCase(session.getPaymentStatus())) return null;
        Map<String, String> metadata = session.getMetadata();
        if (metadata == null || !metadata.containsKey("booking_id")) return null;
        try {
            return Long.parseLong(metadata.get("booking_id"));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Event constructWebhookEvent(String payload, String signatureHeader) throws Exception {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("Stripe webhook secret is not configured.");
        }
        return Webhook.constructEvent(payload, signatureHeader, webhookSecret);
    }

    public static class SessionResult {
        private final String sessionId;
        private final String url;

        public SessionResult(String sessionId, String url) {
            this.sessionId = sessionId;
            this.url = url;
        }

        public String getSessionId() { return sessionId; }
        public String getUrl() { return url; }
    }
}
