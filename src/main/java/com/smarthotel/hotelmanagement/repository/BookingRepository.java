package com.smarthotel.hotelmanagement.repository;

import com.smarthotel.hotelmanagement.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = {"room", "room.hotel", "guest"})
    @Query("SELECT b FROM Booking b")
    List<Booking> findAllWithRoomAndGuest();

    @EntityGraph(attributePaths = {"room", "room.hotel", "guest"})
    List<Booking> findByGuest_EmailOrderByCheckInDateDesc(String guestEmail);

    @EntityGraph(attributePaths = {"room", "room.hotel", "guest"})
    Optional<Booking> findByGuest_EmailAndIdempotencyKey(String guestEmail, String idempotencyKey);

    Optional<Booking> findByPaymentSessionIdOrRazorpayOrderId(String paymentSessionId, String razorpayOrderId);

    /** Returns true if the room has any active booking overlapping the given date range. */
    @Query("SELECT COUNT(b) > 0 FROM Booking b WHERE b.room.id = :roomId AND b.status IN ('BOOKED', 'CHECKED_IN') " +
           "AND b.checkInDate < :checkOut AND b.checkOutDate > :checkIn")
    boolean existsOverlappingBooking(@Param("roomId") Long roomId,
                                     @Param("checkIn") LocalDate checkIn,
                                     @Param("checkOut") LocalDate checkOut);

    /** Room IDs that are booked for the given hotel in the date range (for availability by dates). */
    @Query("SELECT DISTINCT b.room.id FROM Booking b WHERE b.room.hotel.id = :hotelId AND b.status IN ('BOOKED', 'CHECKED_IN') " +
           "AND b.checkInDate < :checkOut AND b.checkOutDate > :checkIn")
    List<Long> findBookedRoomIdsForHotelInRange(@Param("hotelId") Long hotelId,
                                                @Param("checkIn") LocalDate checkIn,
                                                @Param("checkOut") LocalDate checkOut);
}

