package com.smarthotel.hotelmanagement.dto;

import com.smarthotel.hotelmanagement.entity.Booking;

/**
 * Pay-at-hotel returns a persisted {@link Booking}; Stripe "pay now" only returns checkout URLs
 * (booking is created after successful payment using Stripe session metadata).
 */
public class CreateBookingApiResult {

    private final Booking booking;
    private final String stripeSessionUrl;
    private final String stripeSessionId;

    private CreateBookingApiResult(Booking booking, String stripeSessionUrl, String stripeSessionId) {
        this.booking = booking;
        this.stripeSessionUrl = stripeSessionUrl;
        this.stripeSessionId = stripeSessionId;
    }

    public static CreateBookingApiResult forPayAtHotel(Booking booking) {
        return new CreateBookingApiResult(booking, null, null);
    }

    public static CreateBookingApiResult forStripeRedirect(String stripeSessionUrl, String stripeSessionId) {
        return new CreateBookingApiResult(null, stripeSessionUrl, stripeSessionId);
    }

    public boolean isStripeRedirect() {
        return stripeSessionUrl != null && !stripeSessionUrl.isBlank();
    }

    public Booking getBooking() {
        return booking;
    }

    public String getStripeSessionUrl() {
        return stripeSessionUrl;
    }

    public String getStripeSessionId() {
        return stripeSessionId;
    }
}
