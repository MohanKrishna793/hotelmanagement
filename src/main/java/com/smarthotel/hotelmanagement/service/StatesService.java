package com.smarthotel.hotelmanagement.service;

import com.smarthotel.hotelmanagement.entity.Destination;
import com.smarthotel.hotelmanagement.entity.Hotel;
import com.smarthotel.hotelmanagement.repository.DestinationRepository;
import com.smarthotel.hotelmanagement.repository.HotelRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Returns Indian states and UTs for the state dropdown; cities by state for State → City → Hotels flow.
 */
@Service
public class StatesService {

    private static final String COUNTRY_INDIA = "India";
    private static final List<String> INDIAN_STATES_AND_UTS = List.of(
            "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh",
            "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jharkhand", "Karnataka",
            "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram",
            "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana",
            "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal",
            "Andaman and Nicobar Islands", "Chandigarh", "Dadra and Nagar Haveli and Daman and Diu",
            "Delhi", "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry"
    );

    private final DestinationRepository destinationRepository;
    private final HotelRepository hotelRepository;

    public StatesService(DestinationRepository destinationRepository, HotelRepository hotelRepository) {
        this.destinationRepository = destinationRepository;
        this.hotelRepository = hotelRepository;
    }

    /** Returns state names for dropdown: from DB (India destinations) if any, else static list. */
    public List<String> getStateNames() {
        List<String> fromDb = destinationRepository.findAll().stream()
                .filter(d -> COUNTRY_INDIA.equalsIgnoreCase(d.getCountry()))
                .map(Destination::getName)
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        return fromDb.isEmpty() ? INDIAN_STATES_AND_UTS : fromDb;
    }

    /** Returns distinct city names that have hotels in the given state (for State → City → Hotels flow). */
    public List<String> getCitiesByState(String state) {
        if (state == null || state.isBlank()) return List.of();
        return destinationRepository.findByNameAndCountry(state.trim(), COUNTRY_INDIA)
                .map(dest -> hotelRepository.findByDestination(dest).stream()
                        .map(Hotel::getCity)
                        .filter(c -> c != null && !c.isBlank())
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList()))
                .orElseGet(() -> hotelRepository.findByCityIgnoreCase(state.trim()).stream()
                        .map(Hotel::getCity)
                        .filter(c -> c != null && !c.isBlank())
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList()));
    }
}
