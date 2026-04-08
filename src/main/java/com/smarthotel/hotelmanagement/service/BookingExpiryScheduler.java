package com.smarthotel.hotelmanagement.service;

import com.smarthotel.hotelmanagement.entity.Booking;
import com.smarthotel.hotelmanagement.entity.BookingStatus;
import com.smarthotel.hotelmanagement.repository.BookingRepository;
import com.smarthotel.hotelmanagement.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Runs every day at midnight (00:05) to mark bookings as COMPLETED
 * when their check-out date has passed, and frees the room.
 */
@Component
public class BookingExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryScheduler.class);

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final AuditService auditService;

    public BookingExpiryScheduler(BookingRepository bookingRepository,
                                  RoomRepository roomRepository,
                                  AuditService auditService) {
        this.bookingRepository = bookingRepository;
        this.roomRepository    = roomRepository;
        this.auditService      = auditService;
    }

    /**
     * Scheduled: runs at 00:05 every day.
     * Also triggered once on startup via expireNow() so existing stale bookings
     * are cleaned immediately after deploy.
     */
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void expireOverdueBookings() {
        LocalDate today = LocalDate.now();
        List<Booking> expired = bookingRepository.findExpiredActiveBookings(today);

        if (expired.isEmpty()) {
            log.info("[BookingExpiry] No overdue bookings found for {}", today);
            return;
        }

        log.info("[BookingExpiry] Marking {} booking(s) as COMPLETED (checkout < {})", expired.size(), today);

        for (Booking booking : expired) {
            booking.setStatus(BookingStatus.COMPLETED);
            bookingRepository.save(booking);

            // Free the room so it becomes bookable again
            if (booking.getRoom() != null) {
                booking.getRoom().setAvailable(Boolean.TRUE);
                roomRepository.save(booking.getRoom());
            }

            auditService.log("system", "BOOKING_AUTO_COMPLETED", "Booking", booking.getId(),
                    "checkout=" + booking.getCheckOutDate() + " autoExpired=true");
        }

        log.info("[BookingExpiry] Done. {} booking(s) auto-completed.", expired.size());
    }

    /**
     * Runs once on startup (10 seconds after app is ready) so stale bookings
     * from before the last deploy are cleaned immediately.
     */
    @Scheduled(initialDelay = 10_000, fixedDelay = Long.MAX_VALUE)
    @Transactional
    public void expireOnStartup() {
        log.info("[BookingExpiry] Startup expiry check...");
        expireOverdueBookings();
    }
}
