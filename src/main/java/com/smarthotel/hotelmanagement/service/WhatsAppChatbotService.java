package com.smarthotel.hotelmanagement.service;

import com.smarthotel.hotelmanagement.entity.Hotel;
import com.smarthotel.hotelmanagement.entity.Room;
import com.smarthotel.hotelmanagement.repository.HotelRepository;
import com.smarthotel.hotelmanagement.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Automated WhatsApp chatbot replies for Smart Hotel Management System.
 * Used when WhatsApp Business API (e.g. Twilio) sends incoming message webhooks to our backend;
 * this service returns the appropriate auto-reply based on user message content.
 * Flow: Greeting → Menu (1–5) → Behavior for each option.
 */
@Service
public class WhatsAppChatbotService {

    private static final String BOT_NAME = "Smart Hotel Management";
    private static final String GREETING = "Hello! 👋 Welcome to " + BOT_NAME + ". How can I help you today?";
    private static final String MENU = """
        1️⃣ Book a Room
        2️⃣ Check Booking Status
        3️⃣ Hotel & Amenities Information
        4️⃣ Check-in / Check-out Details
        5️⃣ Contact Support
        Reply with 1, 2, 3, 4, or 5.

        Quick commands:
        • MENU - show menu
        • CANCEL - cancel current flow
        • AGENT - human support""";
    private static final String ASK_CITY_MSG = "🏨 Let's find you a room!\n\nWhich city or state are you looking to stay in?";
    private static final String ASK_DATES_MSG = "📅 Great! What are your check-in and check-out dates?\n(Reply like: 28 Mar - 30 Mar)";
    private static final String ASK_GUESTS_MSG = "👥 How many guests will be staying?\n(Reply like: 2 adults, 1 child)";
    private static final String ASK_ROOM_TYPE_MSG = """
        🛏 Which room type do you prefer at *%s*?

        1️⃣ Standard  - ₹%s/night
        2️⃣ Deluxe    - ₹%s/night
        3️⃣ Suite     - ₹%s/night

        Reply *1*, *2*, or *3*.""";
    private static final String REPLY_2 = """
            For the best experience, install the *Smart Hotel Management* mobile app (one tap booking, no browser back-and-forth).
            After install, sign in with the same email you used on the web.

            Then share your *registered email* or *Booking ID* here and we will guide you.
            (You can still use the website if you prefer — see links below when you open the menu.)
            """;
    private static final String REPLY_3 = "Top amenities:\n🏊 Pool | 🛁 Spa | 📶 Free WiFi | 🍳 Breakfast | 🅿️ Free Parking | 🏋️ Gym\n\nReply with hotel name for more details, or type MENU.";
    private static final String REPLY_4 = "⏰ *Check-in*: from 2:00 PM | *Check-out*: by 11:00 AM. Early check-in or late check-out may be available on request. Reply 1–5 for more.";
    private static final String REPLY_5 = "📞 *Support*: +91-XXXXXXXXXX (9 AM - 6 PM, Mon-Sat)\n📧 support@smarthotel.com\nReply *AGENT* to escalate to a support executive.";
    private static final String REPLY_AGENT = "A support request has been noted. Our team will contact you soon. In the meantime, you can call +91-XXXXXXXXXX.";
    private static final String REPLY_CANCEL = "No problem. I have cancelled your current flow. Reply *MENU* to start again.";
    private static final String REPLY_HINDI = "Hindi support coming soon. फिलहाल आप MENU टाइप करें और नंबर 1-5 से विकल्प चुनें।";
    private static final String FALLBACK = """
        Hmm, I didn't quite catch that 🤔

        Please reply with one of the shown options, or type:
        MENU / CANCEL / AGENT""";

    private static final Pattern PATTERN_1 = Pattern.compile("\\b(1|one|book|booking)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_2 = Pattern.compile("\\b(2|two|status|booking status)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_3 = Pattern.compile("\\b(3|three|hotel|amenities|info)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_4 = Pattern.compile("\\b(4|four|check[- ]?in|check[- ]?out|timing)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_5 = Pattern.compile("\\b(5|five|support|contact|help)\\b", Pattern.CASE_INSENSITIVE);

    private enum State {
        IDLE,
        AWAIT_CITY,
        AWAIT_DATES,
        AWAIT_GUESTS,
        AWAIT_HOTEL_PICK,
        AWAIT_ROOM_TYPE,
        AWAIT_CONFIRM,
        BOOKING_COMPLETE
    }

    private record HotelOption(String name, double rating, String amenities, int standard, int deluxe, int suite) {
    }

    private static final Map<Integer, String> ROOM_TYPES = Map.of(1, "Standard", 2, "Deluxe", 3, "Suite");

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.mobile.play-store-url:}")
    private String playStoreUrl;

    @Value("${app.mobile.app-store-url:}")
    private String appStoreUrl;

    public WhatsAppChatbotService(HotelRepository hotelRepository, RoomRepository roomRepository) {
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
    }

    private static class SessionData {
        private State state = State.IDLE;
        private String city;
        private String dates;
        private String guests;
        private Integer hotelChoice;
        private Integer roomTypeChoice;
        private final Map<Integer, HotelOption> hotelOptions = new LinkedHashMap<>();
    }

    /**
     * Returns the automated reply for the given incoming message.
     * Handles greeting, menu, and options 1–5 (with keyword matching).
     */
    public String getReply(String incomingMessage) {
        return getReply(incomingMessage, "anonymous");
    }

    /**
     * Overload with sender key so webhook can keep conversation state per WhatsApp user.
     */
    public String getReply(String incomingMessage, String senderKey) {
        if (incomingMessage == null) return GREETING + "\n\n" + withLinks(MENU);
        String msg = incomingMessage.trim();
        if (msg.isEmpty()) return GREETING + "\n\n" + withLinks(MENU);

        String normalized = msg.toUpperCase(Locale.ROOT);
        SessionData session = sessions.computeIfAbsent(senderKey != null ? senderKey : "anonymous", k -> new SessionData());

        if (normalized.startsWith("JOIN ")) {
            session.state = State.IDLE;
            return "You're connected! ✅\n\n" + GREETING + "\n\n" + withLinks(MENU);
        }
        if ("MENU".equals(normalized)) {
            return GREETING + "\n\n" + withLinks(MENU);
        }
        if ("CANCEL".equals(normalized)) {
            session.state = State.IDLE;
            return REPLY_CANCEL + "\n\n" + withLinks(MENU);
        }
        if ("AGENT".equals(normalized)) return REPLY_AGENT;
        if ("HINDI".equals(normalized)) return REPLY_HINDI;

        // Continue active conversational booking flow first
        if (session.state != State.IDLE && session.state != State.BOOKING_COMPLETE) {
            return handleStateFlow(session, msg, normalized);
        }

        // Fresh menu routing
        if ("1".equals(msg) || PATTERN_1.matcher(msg).find()) {
            session.state = State.AWAIT_CITY;
            return ASK_CITY_MSG;
        }
        if ("2".equals(msg) || PATTERN_2.matcher(msg).find()) return REPLY_2 + "\n\n" + mobileAppInstallBlock();
        if ("3".equals(msg) || PATTERN_3.matcher(msg).find()) return REPLY_3;
        if ("4".equals(msg) || PATTERN_4.matcher(msg).find()) return REPLY_4;
        if ("5".equals(msg) || PATTERN_5.matcher(msg).find()) return REPLY_5;

        if (isGreetingOrFirstMessage(msg)) return GREETING + "\n\n" + withLinks(MENU);
        return FALLBACK;
    }

    private String handleStateFlow(SessionData session, String msg, String normalized) {
        if (session.state == State.AWAIT_CITY) {
            session.city = msg;
            session.state = State.AWAIT_DATES;
            return ASK_DATES_MSG;
        }
        if (session.state == State.AWAIT_DATES) {
            session.dates = msg;
            session.state = State.AWAIT_GUESTS;
            return ASK_GUESTS_MSG;
        }
        if (session.state == State.AWAIT_GUESTS) {
            session.guests = msg;
            String dynamicHotels = buildHotelOptionsFromDb(session);
            if (dynamicHotels == null) {
                session.state = State.AWAIT_CITY;
                return "I couldn't find hotels in *" + safe(session.city) + "* right now. Please share another city/state.";
            }
            session.state = State.AWAIT_HOTEL_PICK;
            return dynamicHotels;
        }
        if (session.state == State.AWAIT_HOTEL_PICK) {
            Integer pick = parseChoice(msg, 1, 3);
            if (pick == null) return "Please reply with *1*, *2*, or *3* to select a hotel.";
            if (!session.hotelOptions.containsKey(pick)) return "That option is not available. Please choose one of the listed hotels.";
            session.hotelChoice = pick;
            session.state = State.AWAIT_ROOM_TYPE;
            HotelOption h = session.hotelOptions.get(pick);
            return ASK_ROOM_TYPE_MSG.formatted(h.name(), h.standard(), h.deluxe(), h.suite());
        }
        if (session.state == State.AWAIT_ROOM_TYPE) {
            Integer pick = parseChoice(msg, 1, 3);
            if (pick == null) return "Please reply with *1*, *2*, or *3* for room type.";
            session.roomTypeChoice = pick;
            session.state = State.AWAIT_CONFIRM;
            return buildSummary(session);
        }
        if (session.state == State.AWAIT_CONFIRM) {
            if ("YES".equals(normalized)) {
                session.state = State.BOOKING_COMPLETE;
                String bookingId = "HTL" + (System.currentTimeMillis() % 100000);
                return """
                    🎉 *Booking Request Confirmed!*

                    Your temporary Booking ID is *#%s*
                    Our team will finalize availability shortly.

                    %s

                    Need help? Reply *AGENT*.
                    """.formatted(bookingId, mobileAppCompletionHint());
            }
            if ("NO".equals(normalized)) {
                session.state = State.IDLE;
                return """
                    No problem! What would you like to change?
                    1️⃣ City / Hotel
                    2️⃣ Dates
                    3️⃣ Number of guests
                    4️⃣ Room type

                    Reply *1-4* (or MENU to restart).
                    """;
            }
            return "Please reply *YES* to confirm or *NO* to change details.";
        }
        return FALLBACK;
    }

    private String buildHotelOptionsFromDb(SessionData session) {
        List<Hotel> hotels = hotelRepository.findByCityContainingIgnoreCase(safe(session.city))
                .stream()
                .sorted(Comparator.comparing((Hotel h) -> h.getRating() != null ? h.getRating() : 0.0).reversed())
                .limit(3)
                .toList();
        if (hotels.isEmpty()) return null;

        session.hotelOptions.clear();
        StringBuilder sb = new StringBuilder();
        sb.append("🏨 Here are the top hotels in *").append(safe(session.city)).append("* for your dates:\n\n");

        int index = 1;
        for (Hotel hotel : hotels) {
            HotelOption option = toHotelOption(hotel);
            session.hotelOptions.put(index, option);

            sb.append(index).append("️⃣ ").append(option.name()).append("\n")
                    .append("   ⭐ ").append(String.format("%.1f", option.rating()))
                    .append(" | ₹").append(option.standard()).append("/night")
                    .append(" | ").append(option.amenities()).append("\n\n");
            index++;
        }
        sb.append("Reply *1*, *2*, or *3* to select a hotel.");
        return sb.toString();
    }

    private HotelOption toHotelOption(Hotel hotel) {
        List<Room> rooms = roomRepository.findByHotelAndAvailableTrue(hotel);
        if (rooms.isEmpty()) {
            rooms = roomRepository.findByHotel(hotel);
        }

        List<Integer> prices = rooms.stream()
                .map(Room::getPrice)
                .filter(p -> p != null && p > 0)
                .map(Double::intValue)
                .sorted()
                .distinct()
                .limit(3)
                .toList();

        int standard = prices.size() > 0 ? prices.get(0) : fallbackBasePrice(hotel);
        int deluxe = prices.size() > 1 ? prices.get(1) : Math.max(standard + 700, standard);
        int suite = prices.size() > 2 ? prices.get(2) : Math.max(deluxe + 1200, deluxe);
        double rating = hotel.getRating() != null ? hotel.getRating() : 4.0;
        String amenities = (hotel.getAmenities() != null && !hotel.getAmenities().isBlank())
                ? hotel.getAmenities()
                : "WiFi, Parking, Breakfast";
        return new HotelOption(hotel.getName(), rating, amenities, standard, deluxe, suite);
    }

    private int fallbackBasePrice(Hotel hotel) {
        if (hotel.getBasePricePerNight() != null && hotel.getBasePricePerNight() > 0) {
            return hotel.getBasePricePerNight().intValue();
        }
        return 2000;
    }

    private static Integer parseChoice(String msg, int min, int max) {
        try {
            int val = Integer.parseInt(msg.trim());
            return (val >= min && val <= max) ? val : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildSummary(SessionData session) {
        HotelOption h = session.hotelOptions.get(session.hotelChoice);
        if (h == null) return "I couldn't load your selected hotel. Please reply MENU and try again.";
        String roomType = ROOM_TYPES.getOrDefault(session.roomTypeChoice, "Standard");
        int perNight = switch (session.roomTypeChoice != null ? session.roomTypeChoice : 1) {
            case 2 -> h.deluxe();
            case 3 -> h.suite();
            default -> h.standard();
        };
        int estimatedTotal = perNight * 2; // placeholder for phase-1 flow
        return """
            ✅ *Booking Summary*
            ━━━━━━━━━━━━━━━━━━━━
            🏨 %s, %s
            📅 %s
            👥 %s
            🛏 %s Room
            💰 Est. Total: ₹%s

            ━━━━━━━━━━━━━━━━━━━━
            Reply *YES* to confirm your booking
            Reply *NO* to change any details
            """.formatted(
                h.name(),
                safe(session.city),
                safe(session.dates),
                safe(session.guests),
                roomType,
                estimatedTotal
        );
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String withLinks(String menu) {
        return menu + "\n\n" + mobileAppInstallBlock() + "\n\n🔗 Book now: " + baseUrl + "\n🔗 My bookings: " + baseUrl + "/my-bookings.html";
    }

    /** Short CTA with optional Play / App Store URLs from configuration. */
    private String mobileAppInstallBlock() {
        StringBuilder sb = new StringBuilder();
        sb.append("📲 *Smart Hotel Management app:* Install once and book faster — avoid opening the website again and again.\n");
        boolean hasStore = false;
        if (playStoreUrl != null && !playStoreUrl.isBlank()) {
            sb.append("• Android: ").append(playStoreUrl.trim()).append('\n');
            hasStore = true;
        }
        if (appStoreUrl != null && !appStoreUrl.isBlank()) {
            sb.append("• iOS: ").append(appStoreUrl.trim()).append('\n');
            hasStore = true;
        }
        if (!hasStore) {
            sb.append("_(Store links are configured on the server when the app is live.)_\n");
        }
        return sb.toString().trim();
    }

    private String mobileAppCompletionHint() {
        return "📲 Next time, use the *Smart Hotel Management* mobile app for quicker access with the same login.\n\n"
                + mobileAppInstallBlock();
    }

    private static boolean isGreetingOrFirstMessage(String msg) {
        String lower = msg.toLowerCase();
        return lower.matches("^(hi|hello|hey|help|start|hii|good morning|good afternoon|good evening)$")
                || lower.contains("help with hotel")
                || lower.contains("need help");
    }
}
