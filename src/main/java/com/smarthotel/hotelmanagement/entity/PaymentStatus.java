package com.smarthotel.hotelmanagement.entity;

/** Payment status for a booking. */
public enum PaymentStatus {
    PENDING,   // Awaiting payment (Pay now not yet done)
    PAID,      // Paid via gateway or pay-at-hotel confirmed
    PAY_AT_HOTEL,
    REFUNDED,
    FAILED
}
