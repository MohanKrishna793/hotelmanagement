package com.smarthotel.hotelmanagement.security;

import com.smarthotel.hotelmanagement.entity.*;
import com.smarthotel.hotelmanagement.repository.*;
import com.smarthotel.hotelmanagement.service.HotelImageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Seeds hotels, rooms, destinations and nearby tourist places from a CSV file.
 * Supports enriched CSV columns:
 * State/UT, Hotel Name, City, Hotel Category, Hotel Type, Star Rating, Amenities, Nearby Tourist Places, ...
 * Also supports legacy CSV columns: State, Hotel Name, Type, Amenities, Nearby Tourist Places.
 * Place preferred file as src/main/resources/hotelsdata_enriched.csv.
 * Runs when the database has no hotels, or when hotel.seed.reset=true (clears existing data then seeds).
 * CSV file preference: src/main/resources/hotelsdata_enriched.csv, then legacy file names.
 */
@Component
@Order(1)
public class CsvHotelDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CsvHotelDataSeeder.class);
    private static final String COUNTRY = "India";
    private static final String DEFAULT_IMAGE = "https://images.pexels.com/photos/258154/pexels-photo-258154.jpeg?auto=compress&cs=tinysrgb&w=800";

    private static final long PEXELS_DELAY_MS = 500;
    private static final int MAX_ROW_RETRY_ATTEMPTS = 4;
    private static final long INITIAL_ROW_RETRY_BACKOFF_MS = 1000;

    private final DestinationRepository destinationRepository;
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final TouristPlaceRepository touristPlaceRepository;
    private final HotelTouristPlaceRepository hotelTouristPlaceRepository;
    private final BookingRepository bookingRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private HotelImageService hotelImageService;

    @Value("${hotel.seed.reset:false}")
    private boolean resetAndSeed;

    // Fetching images via Pexels can be slow and may cause remote DB connections to drop mid-import.
    // Set to true only if you have a working Pexels API key and enough time/network stability.
    @Value("${hotel.seed.fetch-images:false}")
    private boolean fetchImages;

    public CsvHotelDataSeeder(DestinationRepository destinationRepository,
                              HotelRepository hotelRepository,
                              RoomRepository roomRepository,
                              TouristPlaceRepository touristPlaceRepository,
                              HotelTouristPlaceRepository hotelTouristPlaceRepository,
                              BookingRepository bookingRepository,
                              JdbcTemplate jdbcTemplate) {
        this.destinationRepository = destinationRepository;
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.touristPlaceRepository = touristPlaceRepository;
        this.hotelTouristPlaceRepository = hotelTouristPlaceRepository;
        this.bookingRepository = bookingRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        long existingHotels = hotelRepository.count();
        log.info("CsvHotelDataSeeder starting. hotel.seed.reset={}, existingHotelsCount={}", resetAndSeed, existingHotels);
        if (resetAndSeed) {
            log.info("hotel.seed.reset=true: clearing existing hotel data before seeding.");
            try {
                deleteAllHotelDataWithRetries();
            } catch (DataAccessException e) {
                log.error(
                        "hotel.seed.reset failed after retries (database unreachable or connection reset). "
                                + "Set hotel.seed.reset=false and fix DB connectivity. Skipping CSV seed this startup.",
                        e);
                return;
            }
        } else if (hotelRepository.count() > 0) {
            return;
        }
        ClassPathResource resource = findCsvResource();
        if (resource == null) {
            log.warn(
                    "No hotels CSV in classpath; skipping CSV seed. Looked for: hotelsdata_enriched.csv, hotelsdata.csv, Hotels data.csv, Hotels data -Sheet6.csv");
            return;
        }
        log.info("Found hotels CSV resource: {}", resource.getPath());
        try {
            loadFromCsv(resource);
        } catch (Exception e) {
            log.error("Failed to seed from CSV", e);
        }
    }

    private ClassPathResource findCsvResource() {
        String[] names = { "hotelsdata_enriched.csv", "hotelsdata.csv", "Hotels data.csv", "Hotels data -Sheet6.csv" };
        for (String name : names) {
            ClassPathResource r = new ClassPathResource(name);
            if (r.exists()) return r;
        }
        return null;
    }

    /**
     * Remote DBs (e.g. Supabase pooler) sometimes reset the connection during long delete sequences.
     * Retry a few times before failing startup.
     */
    private void deleteAllHotelDataWithRetries() {
        final int maxAttempts = 3;
        long backoffMs = 800;
        DataAccessException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                deleteAllHotelData();
                log.info("deleteAllHotelData succeeded on attempt {}", attempt);
                return;
            } catch (DataAccessException e) {
                last = e;
                log.warn("deleteAllHotelData attempt {}/{} failed: {}", attempt, maxAttempts, e.getMessage());
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                    backoffMs *= 2;
                }
            }
        }
        if (last != null) {
            throw last;
        }
    }

    private void deleteAllHotelData() {
        // Use TRUNCATE for speed and reliability on remote DBs (avoids long DELETE loops).
        // Keep ordering similar to scripts/delete-hotel-data.sql.
        log.info("deleteAllHotelData(): clearing hotel tables using TRUNCATE (fast path)...");
        jdbcTemplate.execute("TRUNCATE TABLE hotel_tourist_places RESTART IDENTITY CASCADE");
        log.info("TRUNCATE hotel_tourist_places done");
        jdbcTemplate.execute("TRUNCATE TABLE bookings RESTART IDENTITY CASCADE");
        log.info("TRUNCATE bookings done");
        jdbcTemplate.execute("TRUNCATE TABLE rooms RESTART IDENTITY CASCADE");
        log.info("TRUNCATE rooms done");
        jdbcTemplate.execute("TRUNCATE TABLE hotels RESTART IDENTITY CASCADE");
        log.info("TRUNCATE hotels done");
        jdbcTemplate.execute("TRUNCATE TABLE tourist_places RESTART IDENTITY CASCADE");
        log.info("TRUNCATE tourist_places done");
        jdbcTemplate.execute("TRUNCATE TABLE destinations RESTART IDENTITY CASCADE");
        log.info("TRUNCATE destinations done");
        log.info("Hotel-related data cleared via TRUNCATE.");
    }

    private void loadFromCsv(ClassPathResource resource) throws Exception {
        int hotelsCreated = 0;
        String lastState = "";
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null) return;
            log.info("Hotels CSV header: {}", header);
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                List<String> row = parseCsvLine(line);
                if (row.size() < 2) continue;
                String state = row.get(0).trim();
                String hotelName = row.get(1).trim();
                // Optional City column: if 3rd column looks like a city (not Type), use it
                String type = "";
                String amenities = "";
                String nearbyPlaces = "";
                String cityFromCsv = "";
                if (row.size() > 2) {
                    String col2 = row.get(2).trim();
                    if (row.size() >= 8) {
                        // Enriched format:
                        // 0 State, 1 Hotel Name, 2 City, 3 Category, 4 Hotel Type, 5 Star, 6 Amenities, 7 Nearby
                        cityFromCsv = col2;
                        type = row.size() > 4 ? row.get(4).trim() : "";
                        amenities = row.size() > 6 ? row.get(6).trim() : "";
                        nearbyPlaces = row.size() > 7 ? row.get(7).trim() : "";
                    } else if (row.size() >= 6) {
                        // Format: State, Hotel Name, City, Type, Amenities, Nearby
                        cityFromCsv = col2;
                        type = row.size() > 3 ? row.get(3).trim() : "";
                        amenities = row.size() > 4 ? row.get(4).trim() : "";
                        nearbyPlaces = row.size() > 5 ? row.get(5).trim() : "";
                    } else {
                        // Format: State, Hotel Name, Type, Amenities, Nearby (no City column)
                        type = col2;
                        amenities = row.size() > 3 ? row.get(3).trim() : "";
                        nearbyPlaces = row.size() > 4 ? row.get(4).trim() : "";
                    }
                }
                if (state.isEmpty() && !lastState.isEmpty()) state = lastState;
                if (state.isEmpty() || hotelName.isEmpty()) continue;
                if ("State".equalsIgnoreCase(state) && "Hotel Name".equalsIgnoreCase(hotelName)) continue;
                lastState = state;

                boolean created = seedHotelRowWithRetries(state, hotelName, cityFromCsv, type, amenities, nearbyPlaces, hotelsCreated);
                if (created) {
                    hotelsCreated++;
                    if (hotelsCreated > 0 && hotelsCreated % 25 == 0) {
                        log.info("CSV seeding progress: {} hotels created...", hotelsCreated);
                    }
                }
            }
        }
        log.info("CSV seed complete: {} hotels created (cities inferred where possible, images from API or default).", hotelsCreated);
    }

    private boolean seedHotelRowWithRetries(String state, String hotelName, String cityFromCsv, String type,
                                            String amenities, String nearbyPlaces, int hotelIndex) {
        long backoffMs = INITIAL_ROW_RETRY_BACKOFF_MS;
        for (int attempt = 1; attempt <= MAX_ROW_RETRY_ATTEMPTS; attempt++) {
            try {
                String city = inferCity(hotelName, state, cityFromCsv, nearbyPlaces);
                Destination dest = getOrCreateDestination(state);
                Hotel hotel = saveHotel(hotelName, city, state, type, amenities, dest, hotelIndex);
                addRooms(hotel);
                linkTouristPlaces(hotel, nearbyPlaces);
                return true;
            } catch (Exception e) {
                if (attempt >= MAX_ROW_RETRY_ATTEMPTS) {
                    log.error("CSV seeding failed for hotel '{}' (state='{}') after {} attempts. Skipping. Error: {}",
                            hotelName, state, MAX_ROW_RETRY_ATTEMPTS, e.getMessage());
                    return false;
                }
                log.warn("CSV seeding retry {}/{} for hotel '{}' (state='{}'). Reason: {}",
                        attempt, MAX_ROW_RETRY_ATTEMPTS, hotelName, state, e.getMessage());
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                backoffMs *= 2;
            }
        }
        return false;
    }

    /**
     * Infer city from hotel name, optional CSV city column, or nearby places.
     * Ensures city filter works correctly for customers.
     */
    private String inferCity(String hotelName, String state, String cityFromCsv, String nearbyPlaces) {
        if (cityFromCsv != null && !cityFromCsv.isBlank()
                && !cityFromCsv.equalsIgnoreCase("Type") && !cityFromCsv.equalsIgnoreCase("Ultra-Luxury")
                && !cityFromCsv.equalsIgnoreCase("Luxury") && !cityFromCsv.equalsIgnoreCase("Resort")
                && !cityFromCsv.equalsIgnoreCase("Moderate")) {
            return cityFromCsv.trim();
        }
        String name = hotelName.toLowerCase();
        String nearby = (nearbyPlaces != null ? nearbyPlaces : "").toLowerCase();
        // Andhra / Telangana cities (common in hotel names or nearby)
        if (name.contains("guntur") || nearby.contains("amaravathi") && state.equalsIgnoreCase("Andhra")) return "Guntur";
        if (name.contains("nellore")) return "Nellore";
        if (name.contains("vizag") || name.contains("visakhapatnam")) return "Vizag";
        if (name.contains("tirupati")) return "Tirupati";
        if (name.contains("vijayawada") || name.contains("kanaka durga") || nearby.contains("kanaka durga")) return "Vijayawada";
        if (name.contains("kakinada")) return "Kakinada";
        if (name.contains("rajahmundry") || nearby.contains("papi hills")) return "Rajahmundry";
        // Kerala
        if (name.contains("kovalam") || name.contains("trivandrum")) return "Trivandrum";
        if (name.contains("kochi") || name.contains("kochi")) return "Kochi";
        if (name.contains("munnar")) return "Munnar";
        if (name.contains("wayanad")) return "Wayanad";
        if (name.contains("kumarakom")) return "Kumarakom";
        if (name.contains("alleppey") || name.contains("alappuzha")) return "Alleppey";
        // Rajasthan
        if (name.contains("jaipur")) return "Jaipur";
        if (name.contains("udaipur")) return "Udaipur";
        if (name.contains("jodhpur")) return "Jodhpur";
        if (name.contains("jaisalmer")) return "Jaisalmer";
        if (name.contains("bikaner")) return "Bikaner";
        if (name.contains("pushkar")) return "Pushkar";
        // Karnataka
        if (name.contains("bengaluru") || name.contains("bangalore")) return "Bengaluru";
        if (name.contains("mysore") || name.contains("mysuru")) return "Mysore";
        if (name.contains("coorg") || name.contains("madikeri")) return "Coorg";
        if (name.contains("hampi")) return "Hampi";
        if (name.contains("chikmagalur")) return "Chikmagalur";
        // Maharashtra
        if (name.contains("mumbai")) return "Mumbai";
        if (name.contains("pune")) return "Pune";
        if (name.contains("lonavala")) return "Lonavala";
        if (name.contains("mahabaleshwar")) return "Mahabaleshwar";
        if (name.contains("nashik")) return "Nashik";
        if (name.contains("aurangabad")) return "Aurangabad";
        if (name.contains("nagpur")) return "Nagpur";
        // Default: use state as city when no inference possible
        return state;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur = new StringBuilder();
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private Destination getOrCreateDestination(String state) {
        return destinationRepository.findByNameAndCountry(state, COUNTRY)
                .orElseGet(() -> destinationRepository.save(
                        new Destination(state, COUNTRY, state, state + " destinations")));
    }

    private Hotel saveHotel(String name, String city, String state, String type, String amenities, Destination dest, int hotelIndex) {
        Optional<Hotel> existing = hotelRepository.findFirstByNameIgnoreCaseAndCityIgnoreCaseAndCountryIgnoreCase(name, city, COUNTRY);
        if (existing.isPresent()) {
            return existing.get();
        }
        String imageUrl = DEFAULT_IMAGE;
        if (fetchImages && hotelImageService != null && !hotelImageService.isRateLimitHit()) {
            // Prefer hotel-name search first to get images closer to the actual property.
            String primaryQuery = name + " " + city + " India hotel exterior";
            // Fallback to city/state query if exact-name search has low coverage in Pexels.
            String fallbackQuery = "hotel building " + city + " " + state + " India";
            Optional<String> fetched = hotelImageService.fetchImageUrlWithFallback(primaryQuery, fallbackQuery, hotelIndex);
            if (fetched.isPresent()) {
                imageUrl = fetched.get();
            }
            if (PEXELS_DELAY_MS > 0) {
                try {
                    Thread.sleep(PEXELS_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        Hotel h = new Hotel();
        h.setName(name);
        h.setAddress(city + ", " + state + ", " + COUNTRY);
        h.setCity(city);
        h.setCountry(COUNTRY);
        h.setDescription(type);
        h.setImageUrl(imageUrl);
        h.setAmenities(amenities.isEmpty() ? "WiFi, AC" : amenities);
        h.setBasePricePerNight(5000.0);
        h.setRating(4.5);
        h.setDestination(dest);
        return hotelRepository.save(h);
    }

    private void addRooms(Hotel hotel) {
        String prefix = "H" + hotel.getId() + "-";
        double[] prices = {4000, 4000, 6000, 6000, 8000, 8000, 10000, 10000, 3000, 3500};
        String[] types = {"DELUXE", "DELUXE", "SUITE", "SUITE", "LUXURY", "LUXURY", "PREMIUM", "PREMIUM", "BUDGET", "BUDGET"};
        for (int i = 0; i < 10; i++) {
            String roomNumber = prefix + (i + 1);
            if (roomRepository.findByRoomNumber(roomNumber).isPresent()) {
                continue;
            }
            Room r = new Room(roomNumber, types[i], prices[i], true);
            r.setHotel(hotel);
            roomRepository.save(r);
        }
    }

    private void linkTouristPlaces(Hotel hotel, String nearbyPlaces) {
        if (nearbyPlaces == null || nearbyPlaces.trim().isEmpty()) return;
        String[] parts = nearbyPlaces.split(",");
        double distance = 2.0;
        for (String part : parts) {
            String name = part.trim();
            if (name.isEmpty()) continue;
            TouristPlace place = touristPlaceRepository.findFirstByName(name)
                    .orElseGet(() -> touristPlaceRepository.save(
                            new TouristPlace(name, null, "Nearby attraction", null, null, 8.0)));
            hotelTouristPlaceRepository.save(new HotelTouristPlace(hotel, place, distance));
            distance += 0.5;
        }
    }
}
