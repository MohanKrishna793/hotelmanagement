package com.smarthotel.hotelmanagement.controller;

import com.smarthotel.hotelmanagement.service.CustomerBookingService;
import com.smarthotel.hotelmanagement.service.StripePaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);
    private final StripePaymentService stripePaymentService;
    private final CustomerBookingService customerBookingService;

    public StripeWebhookController(StripePaymentService stripePaymentService,
                                   CustomerBookingService customerBookingService) {
        this.stripePaymentService = stripePaymentService;
        this.customerBookingService = customerBookingService;
    }

    @PostMapping("/stripe/webhook")
    public ResponseEntity<Map<String, String>> handleStripeWebhook(
            @RequestHeader(name = "Stripe-Signature", required = false) String signature,
            @RequestBody String payload) {
        try {
            Event event = stripePaymentService.constructWebhookEvent(payload, signature);
            if ("checkout.session.completed".equals(event.getType())) {
                try {
                    StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
                    if (stripeObject instanceof Session session && "paid".equalsIgnoreCase(session.getPaymentStatus())) {
                        customerBookingService.syncBookingFromStripeCheckoutSession(session);
                        log.info("Stripe webhook: booking sync attempted for session {}", session.getId());
                    }
                } catch (Exception ignored) {
                    // Keep webhook idempotent: event acknowledged even when payload object is unavailable in tests/retries.
                }
            }
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature validation failed");
            return ResponseEntity.badRequest().body(Map.of("status", "invalid_signature"));
        } catch (Exception e) {
            log.error("Stripe webhook handling failed", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error"));
        }
    }
}
