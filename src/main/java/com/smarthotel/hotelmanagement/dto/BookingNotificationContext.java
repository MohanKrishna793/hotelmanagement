package com.smarthotel.hotelmanagement.dto;

import java.time.LocalDate;

/**
 * Data required to send post-booking notifications (email and WhatsApp).
 * Built after a booking is saved so async notification does not depend on persistence layer.
 */
public record BookingNotificationContext(
        String guestName,
        String guestEmail,
        String guestPhone,
        String hotelName,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        String bookingReference,
        Double totalCost
) {}
