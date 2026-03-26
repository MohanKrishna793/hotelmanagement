package com.smarthotel.hotelmanagement.security;

import com.smarthotel.hotelmanagement.repository.BookingRepository;
import com.smarthotel.hotelmanagement.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * When app.reset.users-and-bookings=true, deletes all bookings and all users on startup.
 * Set to false after one run. Useful for clearing test data; roles are kept so admin can be re-created.
 */
@Component
@Order(0)
public class UsersAndBookingsReset implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UsersAndBookingsReset.class);

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    @Value("${app.reset.users-and-bookings:false}")
    private boolean resetUsersAndBookings;

    public UsersAndBookingsReset(BookingRepository bookingRepository, UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        if (!resetUsersAndBookings) return;
        log.warn("app.reset.users-and-bookings=true: deleting all bookings and all users.");
        long bookings = bookingRepository.count();
        long users = userRepository.count();
        bookingRepository.deleteAll();
        userRepository.deleteAll();
        log.info("Deleted {} bookings and {} users. Set app.reset.users-and-bookings=false and restart.", bookings, users);
    }
}
