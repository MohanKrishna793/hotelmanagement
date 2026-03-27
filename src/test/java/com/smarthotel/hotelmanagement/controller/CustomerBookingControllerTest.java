package com.smarthotel.hotelmanagement.controller;

import com.smarthotel.hotelmanagement.dto.CreateBookingApiResult;
import com.smarthotel.hotelmanagement.entity.Booking;
import com.smarthotel.hotelmanagement.service.CustomerBookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerBookingControllerTest {

    @Mock
    private CustomerBookingService customerBookingService;

    @Mock
    private Authentication authentication;

    private CustomerBookingController controller;

    @BeforeEach
    void setUp() {
        controller = new CustomerBookingController(customerBookingService);
        when(authentication.getName()).thenReturn("test@example.com");
    }

    @Test
    void createBookingDelegatesToService() {
        Booking booking = new Booking();
        CustomerBookingController.CreateBookingRequest req = new CustomerBookingController.CreateBookingRequest();
        req.setRoomId(1L);
        req.setCheckInDate(LocalDate.now().plusDays(1));
        req.setCheckOutDate(LocalDate.now().plusDays(2));
        req.setPaymentMethod("PAY_AT_HOTEL");
        when(customerBookingService.createBookingForUser(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(CreateBookingApiResult.forPayAtHotel(booking));

        Object out = controller.createBooking(authentication, "https://hotelmanagement-production-b642.up.railway.app", "idemp-1", req);
        assertEquals(booking, out);
    }

    @Test
    void createBookingStripeReturnsCheckoutPayload() {
        CustomerBookingController.CreateBookingRequest req = new CustomerBookingController.CreateBookingRequest();
        req.setRoomId(1L);
        req.setCheckInDate(LocalDate.now().plusDays(1));
        req.setCheckOutDate(LocalDate.now().plusDays(2));
        req.setPaymentMethod("STRIPE");
        when(customerBookingService.createBookingForUser(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(CreateBookingApiResult.forStripeRedirect("https://checkout.stripe.com/test", "cs_test_1"));

        Object out = controller.createBooking(authentication, "https://hotelmanagement-production-b642.up.railway.app", "idemp-1", req);
        assertInstanceOf(Map.class, out);
        @SuppressWarnings("unchecked")
        Map<String, String> m = (Map<String, String>) out;
        assertEquals("https://checkout.stripe.com/test", m.get("stripeSessionUrl"));
    }

    @Test
    void verifyStripeDelegatesToService() {
        Booking booking = new Booking();
        when(customerBookingService.verifyStripePayment("test@example.com", "cs_test_123", "https://hotelmanagement-production-b642.up.railway.app"))
                .thenReturn(booking);
        Booking out = controller.verifyStripePayment(
                authentication,
                "https://hotelmanagement-production-b642.up.railway.app",
                Map.of("session_id", "cs_test_123")
        );
        assertEquals(booking, out);
    }

    @Test
    void getMyBookingsDelegatesToService() {
        when(customerBookingService.getBookingsForUser("test@example.com")).thenReturn(List.of(new Booking()));
        assertEquals(1, controller.getMyBookings(authentication).size());
    }

    @Test
    void cancelBookingDelegatesToService() {
        Booking booking = new Booking();
        when(customerBookingService.cancelBookingByUser(eq("test@example.com"), eq(1L))).thenReturn(booking);
        assertEquals(booking, controller.cancelBooking(authentication, 1L));
    }
}
