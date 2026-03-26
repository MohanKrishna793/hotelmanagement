package com.smarthotel.hotelmanagement.controller;

import com.smarthotel.hotelmanagement.service.StripePaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Stripe publishable key for frontend (test mode = no real money; no PAN/KYC required). */
@RestController
@RequestMapping("/api/customer")
public class PaymentController {

    private final StripePaymentService stripePaymentService;

    public PaymentController(StripePaymentService stripePaymentService) {
        this.stripePaymentService = stripePaymentService;
    }

    @GetMapping("/stripe-key")
    public Map<String, String> getStripeKey() {
        return Map.of(
                "publishableKey", stripePaymentService.isEnabled() ? stripePaymentService.getPublishableKey() : "",
                "enabled", String.valueOf(stripePaymentService.isEnabled())
        );
    }
}
