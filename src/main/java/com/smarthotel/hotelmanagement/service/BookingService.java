package com.smarthotel.hotelmanagement.service;

import com.smarthotel.hotelmanagement.dto.BookingNotificationContext;
import com.smarthotel.hotelmanagement.entity.Booking;
import com.smarthotel.hotelmanagement.entity.BookingStatus;
import com.smarthotel.hotelmanagement.entity.Guest;
import com.smarthotel.hotelmanagement.entity.Room;
import com.smarthotel.hotelmanagement.entity.PaymentMethod;
import com.smarthotel.hotelmanagement.entity.PaymentStatus;
import com.smarthotel.hotelmanagement.repository.BookingRepository;
import com.smarthotel.hotelmanagement.repository.GuestRepository;
import com.smarthotel.hotelmanagement.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final GuestRepository guestRepository;
    private final NotificationService notificationService;
    private final CancellationPolicyService cancellationPolicyService;
    private final AuditService auditService;

    public BookingService(BookingRepository bookingRepository,
                          RoomRepository roomRepository,
                          GuestRepository guestRepository,
                          NotificationService notificationService,
                          CancellationPolicyService cancellationPolicyService,
                          AuditService auditService) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.guestRepository = guestRepository;
        this.notificationService = notificationService;
        this.cancellationPolicyService = cancellationPolicyService;
        this.auditService = auditService;
    }

    public Booking createBooking(Booking booking) {
        if (booking.getCheckInDate() == null || booking.getCheckOutDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check-in and check-out dates are required");
        }

        LocalDate checkIn = booking.getCheckInDate();
        LocalDate checkOut = booking.getCheckOutDate();

        if (!checkOut.isAfter(checkIn)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check-out date must be after check-in date");
        }

        if (booking.getRoom() == null || booking.getRoom().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room id is required");
        }

        if (booking.getGuest() == null || booking.getGuest().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Guest id is required");
        }

        Room room = roomRepository.findById(booking.getRoom().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        Guest guest = guestRepository.findById(booking.getGuest().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Guest not found"));

        if (Boolean.FALSE.equals(room.getAvailable())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room is not available");
        }

        Long roomId = room.getId();
        if (bookingRepository.existsOverlappingBooking(roomId, checkIn, checkOut)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room is already booked for the selected dates. Please choose different dates or another room.");
        }

        booking.setRoom(room);
        booking.setGuest(guest);

        long nights = checkIn.until(checkOut).getDays();
        if (nights <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stay duration must be at least 1 night");
        }

        double basePrice = room.getPrice() != null ? room.getPrice() : 0.0;
        double totalCost = basePrice * nights;

        booking.setStatus(BookingStatus.BOOKED);
        booking.setTotalCost(totalCost);
        if (booking.getPaymentStatus() == null) booking.setPaymentStatus(PaymentStatus.PAY_AT_HOTEL);
        if (booking.getPaymentMethod() == null) booking.setPaymentMethod(PaymentMethod.PAY_AT_HOTEL);

        Booking saved = bookingRepository.save(booking);

        room.setAvailable(Boolean.FALSE);
        roomRepository.save(room);

        auditService.log("admin", "BOOKING_CREATE", "Booking", saved.getId(), "guest=" + (guest.getEmail() != null ? guest.getEmail() : ""));
        notificationService.sendBookingConfirmationAsync(buildNotificationContext(saved));
        return saved;
    }

    private BookingNotificationContext buildNotificationContext(Booking saved) {
        Guest g = saved.getGuest();
        Room r = saved.getRoom();
        String hotelName = (r != null && r.getHotel() != null) ? r.getHotel().getName() : "Smart Hotel";
        return new BookingNotificationContext(
                g != null ? g.getName() : null,
                g != null ? g.getEmail() : null,
                g != null ? g.getPhone() : null,
                hotelName,
                saved.getCheckInDate(),
                saved.getCheckOutDate(),
                "BK-" + saved.getId(),
                saved.getTotalCost()
        );
    }

    public List<Booking> getAllBookings() {
        return bookingRepository.findAllWithRoomAndGuest();
    }

    public Booking updateStatus(Long id, BookingStatus status) {
        return updateStatus(id, status, null);
    }

    public Booking updateStatus(Long id, BookingStatus status, String auditUserEmail) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        booking.setStatus(status);
        if (status == BookingStatus.CANCELLED) {
            double refund = cancellationPolicyService.calculateRefund(
                    booking.getTotalCost() != null ? booking.getTotalCost() : 0,
                    booking.getCheckInDate(),
                    booking.getCheckOutDate());
            booking.setRefundAmount(refund);
            log.info("Booking cancelled: id={}, refund={}", id, refund);
        }
        if (status == BookingStatus.CANCELLED || status == BookingStatus.COMPLETED) {
            Room room = booking.getRoom();
            if (room != null) {
                room.setAvailable(Boolean.TRUE);
                roomRepository.save(room);
            }
        }
        auditService.log(auditUserEmail != null ? auditUserEmail : "system", "BOOKING_STATUS_UPDATE", "Booking", id,
                "status=" + status + (booking.getRefundAmount() != null ? ", refund=" + booking.getRefundAmount() : ""));
        Booking saved = bookingRepository.save(booking);
        if (status == BookingStatus.CANCELLED) {
            notificationService.sendCancellationNotificationAsync(buildNotificationContext(saved), saved.getRefundAmount());
        }
        return saved;
    }
}

