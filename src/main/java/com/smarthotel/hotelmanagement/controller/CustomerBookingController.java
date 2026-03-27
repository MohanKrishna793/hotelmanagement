package com.smarthotel.hotelmanagement.controller;

import com.smarthotel.hotelmanagement.dto.CreateBookingApiResult;
import com.smarthotel.hotelmanagement.entity.Booking;
import com.smarthotel.hotelmanagement.service.CustomerBookingService;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customer/bookings")
public class CustomerBookingController {

    private final CustomerBookingService customerBookingService;

    public CustomerBookingController(CustomerBookingService customerBookingService) {
        this.customerBookingService = customerBookingService;
    }

    public static class CreateBookingRequest {
        @NotNull
        private Long roomId;
        @NotNull
        private LocalDate checkInDate;
        @NotNull
        private LocalDate checkOutDate;
        private String guestPhone;
        /** STRIPE = Pay now (test mode), PAY_AT_HOTEL = pay on arrival */
        private String paymentMethod;
        private String specialRequests;
        private String discountCode;

        public Long getRoomId() { return roomId; }
        public void setRoomId(Long roomId) { this.roomId = roomId; }
        public LocalDate getCheckInDate() { return checkInDate; }
        public void setCheckInDate(LocalDate checkInDate) { this.checkInDate = checkInDate; }
        public LocalDate getCheckOutDate() { return checkOutDate; }
        public void setCheckOutDate(LocalDate checkOutDate) { this.checkOutDate = checkOutDate; }
        public String getGuestPhone() { return guestPhone; }
        public void setGuestPhone(String guestPhone) { this.guestPhone = guestPhone; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public String getSpecialRequests() { return specialRequests; }
        public void setSpecialRequests(String specialRequests) { this.specialRequests = specialRequests; }
        public String getDiscountCode() { return discountCode; }
        public void setDiscountCode(String discountCode) { this.discountCode = discountCode; }
    }

    @PostMapping("/verify-stripe")
    public Booking verifyStripePayment(Authentication authentication,
                                       @RequestBody java.util.Map<String, String> body) {
        String email = authentication.getName();
        String sessionId = body != null ? body.get("session_id") : null;
        return customerBookingService.verifyStripePayment(email, sessionId);
    }

    @PostMapping
    public Object createBooking(Authentication authentication,
                                 @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                 @RequestBody CreateBookingRequest request) {
        String email = authentication.getName();
        CreateBookingApiResult result = customerBookingService.createBookingForUser(
                email,
                request.getRoomId(),
                request.getCheckInDate(),
                request.getCheckOutDate(),
                request.getGuestPhone(),
                request.getPaymentMethod(),
                request.getSpecialRequests(),
                request.getDiscountCode(),
                idempotencyKey
        );
        if (result.isStripeRedirect()) {
            return Map.of(
                    "stripeSessionUrl", result.getStripeSessionUrl(),
                    "stripeSessionId", result.getStripeSessionId() != null ? result.getStripeSessionId() : ""
            );
        }
        return result.getBooking();
    }

    @GetMapping
    public List<Booking> getMyBookings(Authentication authentication) {
        String email = authentication.getName();
        return customerBookingService.getBookingsForUser(email);
    }

    @GetMapping("/history")
    public List<Booking> getBookingHistory(Authentication authentication) {
        String email = authentication.getName();
        return customerBookingService.getBookingHistoryForUser(email);
    }

    @PatchMapping("/{id}/cancel")
    public Booking cancelBooking(Authentication authentication, @PathVariable Long id) {
        String email = authentication.getName();
        return customerBookingService.cancelBookingByUser(email, id);
    }
}

