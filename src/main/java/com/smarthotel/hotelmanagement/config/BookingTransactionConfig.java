package com.smarthotel.hotelmanagement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Booking flows must commit independently: the default {@link TransactionTemplate} uses
 * {@link TransactionDefinition#PROPAGATION_REQUIRED} and <strong>joins</strong> any outer transaction.
 * If the controller or another layer starts a transaction, {@code SELECT ... FOR UPDATE} on {@code rooms}
 * would stay held until that outer transaction ends (e.g. after Stripe), causing lock contention and
 * {@code canceling statement due to statement timeout} on concurrent booking attempts.
 */
@Configuration
public class BookingTransactionConfig {

    public static final String BOOKING_TRANSACTION_TEMPLATE = "bookingTransactionTemplate";

    @Bean(name = BOOKING_TRANSACTION_TEMPLATE)
    public TransactionTemplate bookingTransactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
