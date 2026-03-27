package com.smarthotel.hotelmanagement.service;

import com.smarthotel.hotelmanagement.dto.BookingNotificationContext;
import com.smarthotel.hotelmanagement.dto.CreateBookingApiResult;
import com.stripe.model.checkout.Session;
import com.smarthotel.hotelmanagement.entity.Booking;
import com.smarthotel.hotelmanagement.entity.BookingStatus;
import com.smarthotel.hotelmanagement.entity.Guest;
import com.smarthotel.hotelmanagement.entity.Room;
import com.smarthotel.hotelmanagement.entity.User;
import com.smarthotel.hotelmanagement.entity.PaymentMethod;
import com.smarthotel.hotelmanagement.entity.PaymentStatus;
import com.smarthotel.hotelmanagement.entity.DiscountCode;
import com.smarthotel.hotelmanagement.repository.BookingRepository;
import com.smarthotel.hotelmanagement.repository.GuestRepository;
import com.smarthotel.hotelmanagement.repository.RoomRepository;
import com.smarthotel.hotelmanagement.repository.UserRepository;
import org.springframework.http.HttpStatus;
import com.smarthotel.hotelmanagement.config.BookingTransactionConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CustomerBookingService {

    private static final Logger log = LoggerFactory.getLogger(CustomerBookingService.class);

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final GuestRepository guestRepository;
    private final NotificationService notificationService;
    private final BookingService bookingService;
    private final StripePaymentService stripePaymentService;
    private final DiscountCodeService discountCodeService;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public CustomerBookingService(BookingRepository bookingRepository,
                                  RoomRepository roomRepository,
                                  UserRepository userRepository,
                                  GuestRepository guestRepository,
                                  NotificationService notificationService,
                                  BookingService bookingService,
                                  StripePaymentService stripePaymentService,
                                  DiscountCodeService discountCodeService,
                                  AuditService auditService,
                                  @Qualifier(BookingTransactionConfig.BOOKING_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.guestRepository = guestRepository;
        this.notificationService = notificationService;
        this.bookingService = bookingService;
        this.stripePaymentService = stripePaymentService;
        this.discountCodeService = discountCodeService;
        this.auditService = auditService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Creates a booking. For Stripe, we only create a Checkout Session and redirect — <strong>no DB row lock</strong>
     * and no booking row until payment succeeds (see {@link #verifyStripePayment} / webhook).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CreateBookingApiResult createBookingForUser(String userEmail, Long roomId,
                                        LocalDate checkIn, LocalDate checkOut, String guestPhone,
                                        String paymentMethodStr, String specialRequests, String discountCodeStr, String idempotencyKey) {
        if (checkIn == null || checkOut == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check-in and check-out dates are required");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check-out date must be after check-in date");
        }

        userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (StringUtils.hasText(idempotencyKey)) {
            var existing = bookingRepository.findByGuest_EmailAndIdempotencyKey(userEmail, idempotencyKey.trim());
            if (existing.isPresent()) {
                Booking b = existing.get();
                return CreateBookingApiResult.forPayAtHotel(b);
            }
        }

        boolean payNow = "STRIPE".equalsIgnoreCase(paymentMethodStr) && stripePaymentService.isEnabled();

        if (payNow) {
            // Read-only checks + Stripe session only (booking is created after payment in verify / webhook).
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
            if (Boolean.FALSE.equals(room.getAvailable())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room is not available");
            }
            if (bookingRepository.existsOverlappingBooking(roomId, checkIn, checkOut)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Room is already booked for the selected dates. Please choose different dates or another room.");
            }
            long nights = checkIn.until(checkOut).getDays();
            if (nights <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stay duration must be at least 1 night");
            }
            PricingResult pricing = computeStayPricing(room, checkIn, checkOut, discountCodeStr);
            if (pricing.totalCost() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid price for this stay");
            }

            Map<String, String> meta = new HashMap<>();
            meta.put("room_id", String.valueOf(roomId));
            meta.put("check_in", checkIn.toString());
            meta.put("check_out", checkOut.toString());
            meta.put("guest_email", userEmail);
            if (StringUtils.hasText(idempotencyKey)) {
                meta.put("idempotency_key", idempotencyKey.trim());
            }
            if (StringUtils.hasText(specialRequests)) {
                meta.put("special_requests", specialRequests.trim());
            }
            if (StringUtils.hasText(guestPhone)) {
                meta.put("guest_phone", guestPhone.trim());
            }
            if (StringUtils.hasText(pricing.discountCodeUsed())) {
                meta.put("discount_code", pricing.discountCodeUsed());
            }

            StripePaymentService.SessionResult sessionResult;
            try {
                sessionResult = stripePaymentService.createCheckoutSessionForIntent(
                        pricing.totalCost(), userEmail, meta);
            } catch (RuntimeException e) {
                log.warn("Stripe checkout failed: {}", e.getMessage());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Could not reach Stripe. Check network and API keys.", e);
            }
            if (sessionResult == null || sessionResult.getUrl() == null || sessionResult.getUrl().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Could not start Stripe checkout. Check Stripe keys and that Stripe is enabled.");
            }
            log.info("Stripe Checkout session created for user={}, roomId={}, sessionId={} (booking created after payment)",
                    userEmail, roomId, sessionResult.getSessionId());
            return CreateBookingApiResult.forStripeRedirect(sessionResult.getUrl(), sessionResult.getSessionId());
        }

        Booking saved = transactionTemplate.execute(status -> createPayAtHotelBookingInTransaction(
                userEmail, roomId, checkIn, checkOut, guestPhone, specialRequests, discountCodeStr, idempotencyKey));
        finishBookingAfterSave(userEmail, roomId, checkIn, checkOut, saved);
        return CreateBookingApiResult.forPayAtHotel(saved);
    }

    private record PricingResult(double totalCost, String discountCodeUsed) {}

    private PricingResult computeStayPricing(Room room, LocalDate checkIn, LocalDate checkOut, String discountCodeStr) {
        long nights = checkIn.until(checkOut).getDays();
        double basePrice = room.getPrice() != null ? room.getPrice() : 0.0;
        double totalCost = basePrice * nights;
        String discountCodeUsed = null;
        if (StringUtils.hasText(discountCodeStr)) {
            var dcOpt = discountCodeService.findValid(discountCodeStr.trim(), checkIn, (int) nights);
            if (dcOpt.isPresent()) {
                DiscountCode dc = dcOpt.get();
                totalCost = discountCodeService.applyDiscount(totalCost, dc);
                discountCodeUsed = dc.getCode();
            }
        }
        return new PricingResult(totalCost, discountCodeUsed);
    }

    private Booking createPayAtHotelBookingInTransaction(String userEmail, Long roomId,
                                        LocalDate checkIn, LocalDate checkOut, String guestPhone,
                                        String specialRequests, String discountCodeStr, String idempotencyKey) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        Room room = roomRepository.findWithLockById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        if (Boolean.FALSE.equals(room.getAvailable())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room is not available");
        }

        if (bookingRepository.existsOverlappingBooking(roomId, checkIn, checkOut)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room is already booked for the selected dates. Please choose different dates or another room.");
        }

        long nights = checkIn.until(checkOut).getDays();
        if (nights <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stay duration must be at least 1 night");
        }

        double basePrice = room.getPrice() != null ? room.getPrice() : 0.0;
        double totalCost = basePrice * nights;
        String discountCodeUsed = null;
        if (StringUtils.hasText(discountCodeStr)) {
            var dcOpt = discountCodeService.findValid(discountCodeStr.trim(), checkIn, (int) nights);
            if (dcOpt.isPresent()) {
                DiscountCode dc = dcOpt.get();
                totalCost = discountCodeService.applyDiscount(totalCost, dc);
                discountCodeUsed = dc.getCode();
            }
        }

        Guest guest = guestRepository.findByEmail(userEmail)
                .orElseGet(() -> {
                    // If guestPhone isn't provided on the booking request, fall back to the phone saved on the logged-in user.
                    String phone = StringUtils.hasText(guestPhone)
                            ? guestPhone
                            : (StringUtils.hasText(user.getPhone()) ? user.getPhone() : "N/A");
                    Guest g = new Guest(user.getFullName(), user.getEmail(), phone);
                    return guestRepository.save(g);
                });
        if (StringUtils.hasText(guestPhone)) {
            guest.setPhone(guestPhone);
            guestRepository.save(guest);
        } else if (StringUtils.hasText(user.getPhone())
                && (!StringUtils.hasText(guest.getPhone()) || "N/A".equalsIgnoreCase(guest.getPhone()))) {
            // Ensure we keep guest phone in sync with the user's phone when the booking request omits it.
            guest.setPhone(user.getPhone());
            guestRepository.save(guest);
        }

        Booking booking = new Booking();
        booking.setRoom(room);
        booking.setGuest(guest);
        booking.setCheckInDate(checkIn);
        booking.setCheckOutDate(checkOut);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setTotalCost(totalCost);
        booking.setSpecialRequests(StringUtils.hasText(specialRequests) ? specialRequests.trim() : null);
        booking.setDiscountCode(discountCodeUsed);
        booking.setIdempotencyKey(StringUtils.hasText(idempotencyKey) ? idempotencyKey.trim() : null);
        booking.setPaymentCurrency("INR");
        booking.setPaymentMethod(PaymentMethod.PAY_AT_HOTEL);
        booking.setPaymentStatus(PaymentStatus.PAY_AT_HOTEL);
        booking.setPaymentProvider("HOTEL");

        Booking saved = bookingRepository.save(booking);

        room.setAvailable(Boolean.FALSE);
        roomRepository.save(room);

        return saved;
    }

    private void finishBookingAfterSave(String userEmail, Long roomId, LocalDate checkIn, LocalDate checkOut, Booking saved) {
        log.info("Booking created: id={}, user={}, roomId={}, checkIn={}, checkOut={}, total={}, payment={}",
                saved.getId(), userEmail, roomId, checkIn, checkOut, saved.getTotalCost(), saved.getPaymentStatus());
        auditService.log(userEmail, "BOOKING_CREATED", "BOOKING", saved.getId(),
                "payment=" + saved.getPaymentMethod() + ", total=" + saved.getTotalCost());
        notificationService.sendBookingConfirmationAsync(buildNotificationContext(saved));
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

    public List<Booking> getBookingsForUser(String userEmail) {
        userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        return bookingRepository.findByGuest_EmailOrderByCheckInDateDesc(userEmail).stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED && b.getStatus() != BookingStatus.COMPLETED)
                .toList();
    }

    public List<Booking> getBookingHistoryForUser(String userEmail) {
        userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        return bookingRepository.findByGuest_EmailOrderByCheckInDateDesc(userEmail).stream()
                .filter(b -> b.getStatus() == BookingStatus.CANCELLED || b.getStatus() == BookingStatus.COMPLETED)
                .toList();
    }

    /**
     * Cancel a booking if it belongs to the given user (guest email matches). Frees the room.
     */
    @Transactional
    public Booking cancelBookingByUser(String userEmail, Long bookingId) {
        userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (booking.getGuest() == null || !userEmail.equalsIgnoreCase(booking.getGuest().getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only cancel your own bookings");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking is already cancelled");
        }
        Booking updated = bookingService.updateStatus(bookingId, BookingStatus.CANCELLED, userEmail);
        auditService.log(userEmail, "BOOKING_CANCELLED", "BOOKING", bookingId, "cancelled_by_customer");
        return updated;
    }

    /**
     * After Stripe redirect: retrieve the Checkout Session server-side, verify paid + metadata + amount,
     * then insert the booking (short {@code FOR UPDATE} only here).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Booking verifyStripePayment(String userEmail, String sessionId) {
        userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        Session session = stripePaymentService.retrieveCheckoutSession(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Stripe session");
        }
        if (!"paid".equalsIgnoreCase(session.getPaymentStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment not completed for this session");
        }
        return transactionTemplate.execute(status -> materializeBookingFromPaidSession(session, userEmail));
    }

    /**
     * Called from Stripe webhook (signature verified). Creates the booking if the user never hit verify.
     */
    public void syncBookingFromStripeCheckoutSession(Session session) {
        if (session == null || !"paid".equalsIgnoreCase(session.getPaymentStatus())) {
            return;
        }
        Map<String, String> meta = session.getMetadata();
        if (meta == null) {
            log.warn("Stripe session {} has no metadata; skip booking sync", session.getId());
            return;
        }
        String email = meta.get("guest_email");
        if (!StringUtils.hasText(email)) {
            log.warn("Stripe session {} missing guest_email metadata", session.getId());
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> {
                materializeBookingFromPaidSession(session, email.trim());
            });
        } catch (Exception e) {
            log.error("Webhook booking sync failed for session {}", session.getId(), e);
        }
    }

    /**
     * Inserts booking + marks room unavailable after Stripe confirms payment. Idempotent by session id.
     */
    private Booking materializeBookingFromPaidSession(Session session, String authenticatedEmail) {
        Map<String, String> meta = session.getMetadata();
        if (meta == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Stripe session metadata");
        }
        String metaEmail = meta.get("guest_email");
        if (!StringUtils.hasText(metaEmail) || !authenticatedEmail.equalsIgnoreCase(metaEmail.trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This payment does not belong to your account");
        }

        Optional<Booking> existingSession = bookingRepository.findByPaymentSessionIdOrRazorpayOrderId(session.getId(), session.getId());
        if (existingSession.isPresent()) {
            return ensurePaidAndReturn(existingSession.get(), session.getId());
        }

        String idem = meta.get("idempotency_key");
        if (StringUtils.hasText(idem)) {
            Optional<Booking> idemBooking = bookingRepository.findByGuest_EmailAndIdempotencyKey(authenticatedEmail, idem.trim());
            if (idemBooking.isPresent()) {
                return ensurePaidAndReturn(idemBooking.get(), session.getId());
            }
        }

        long expectedPaise;
        try {
            expectedPaise = Long.parseLong(meta.get("total_paise"));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pricing metadata from Stripe session");
        }
        if (session.getAmountTotal() == null || session.getAmountTotal() != expectedPaise) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment amount does not match booking");
        }

        long roomId;
        LocalDate checkIn;
        LocalDate checkOut;
        try {
            roomId = Long.parseLong(meta.get("room_id"));
            checkIn = LocalDate.parse(meta.get("check_in"));
            checkOut = LocalDate.parse(meta.get("check_out"));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid room or date metadata");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid stay dates in session");
        }

        String discountCodeStr = meta.get("discount_code");
        String specialRequests = meta.get("special_requests");
        String guestPhone = meta.get("guest_phone");

        User user = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        Room room = roomRepository.findWithLockById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        if (Boolean.FALSE.equals(room.getAvailable())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is no longer available. A refund may be required.");
        }
        if (bookingRepository.existsOverlappingBooking(roomId, checkIn, checkOut)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room was booked by someone else for these dates.");
        }

        PricingResult pricing = computeStayPricing(room, checkIn, checkOut, discountCodeStr);
        if (Math.round(pricing.totalCost() * 100) != expectedPaise) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stored price does not match payment (possible tampering).");
        }

        Guest guest = guestRepository.findByEmail(authenticatedEmail)
                .orElseGet(() -> guestRepository.save(new Guest(user.getFullName(), user.getEmail(),
                        StringUtils.hasText(guestPhone)
                                ? guestPhone
                                : (StringUtils.hasText(user.getPhone()) ? user.getPhone() : "N/A"))));
        if (StringUtils.hasText(guestPhone)) {
            guest.setPhone(guestPhone);
            guestRepository.save(guest);
        } else if (StringUtils.hasText(user.getPhone())
                && (!StringUtils.hasText(guest.getPhone()) || "N/A".equalsIgnoreCase(guest.getPhone()))) {
            guest.setPhone(user.getPhone());
            guestRepository.save(guest);
        }

        Booking booking = new Booking();
        booking.setRoom(room);
        booking.setGuest(guest);
        booking.setCheckInDate(checkIn);
        booking.setCheckOutDate(checkOut);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setTotalCost(pricing.totalCost());
        booking.setSpecialRequests(StringUtils.hasText(specialRequests) ? specialRequests.trim() : null);
        booking.setDiscountCode(pricing.discountCodeUsed());
        booking.setIdempotencyKey(StringUtils.hasText(idem) ? idem.trim() : null);
        booking.setPaymentCurrency("INR");
        booking.setPaymentMethod(PaymentMethod.STRIPE);
        booking.setPaymentStatus(PaymentStatus.PAID);
        booking.setPaymentProvider("STRIPE");
        booking.setRazorpayOrderId(session.getId());
        booking.setPaymentSessionId(session.getId());
        booking.setRazorpayPaymentId(session.getId());
        booking.setPaymentExternalId(session.getId());
        booking.setPaidAt(LocalDateTime.now());

        Booking saved = bookingRepository.save(booking);
        room.setAvailable(Boolean.FALSE);
        roomRepository.save(room);

        finishBookingAfterSave(authenticatedEmail, roomId, checkIn, checkOut, saved);
        auditService.log(authenticatedEmail, "PAYMENT_VERIFIED", "BOOKING", saved.getId(), "session=" + session.getId());
        return saved;
    }

    private Booking ensurePaidAndReturn(Booking booking, String sessionId) {
        if (booking.getPaymentStatus() != PaymentStatus.PAID) {
            booking.setPaymentStatus(PaymentStatus.PAID);
            booking.setPaymentSessionId(sessionId);
            booking.setRazorpayPaymentId(sessionId);
            booking.setPaidAt(LocalDateTime.now());
            bookingRepository.save(booking);
        }
        return booking;
    }

    /** @deprecated Use {@link #syncBookingFromStripeCheckoutSession(Session)} */
    @Deprecated
    @Transactional
    public void markPaidFromWebhook(String sessionId) {
        Session session = stripePaymentService.retrieveCheckoutSession(sessionId);
        if (session != null) {
            syncBookingFromStripeCheckoutSession(session);
        }
    }
}

