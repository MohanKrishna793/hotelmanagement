package com.smarthotel.hotelmanagement.entity;

/** How the guest chose to pay. */
public enum PaymentMethod {
    STRIPE,        // Pay now (Stripe test mode = no real money; no PAN/KYC required)
    PAY_AT_HOTEL
}
