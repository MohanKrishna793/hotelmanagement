package com.smarthotel.hotelmanagement.controller;

import com.smarthotel.hotelmanagement.service.CustomerBookingService;
import com.smarthotel.hotelmanagement.service.StripePaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {

    @Mock
    private StripePaymentService stripePaymentService;

    @Mock
    private CustomerBookingService customerBookingService;

    private StripeWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new StripeWebhookController(stripePaymentService, customerBookingService);
    }

    @Test
    void shouldReturnOkForValidEvent() throws Exception {
        Event event = new Event();
        event.setType("checkout.session.completed");
        when(stripePaymentService.constructWebhookEvent(anyString(), anyString())).thenReturn(event);

        ResponseEntity<?> response = controller.handleStripeWebhook("t=1,v1=sig", "{\"id\":\"evt_test\"}");
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void shouldReturnBadRequestForInvalidSignature() throws Exception {
        doThrow(new SignatureVerificationException("bad sig", "sig"))
                .when(stripePaymentService).constructWebhookEvent(anyString(), anyString());

        ResponseEntity<?> response = controller.handleStripeWebhook("bad", "{\"id\":\"evt_test\"}");
        assertEquals(400, response.getStatusCode().value());
    }
}
