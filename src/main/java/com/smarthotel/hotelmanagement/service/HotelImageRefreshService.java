package com.smarthotel.hotelmanagement.service;

import com.smarthotel.hotelmanagement.entity.Hotel;
import com.smarthotel.hotelmanagement.repository.HotelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Resets hotel images: when Pexels is available, fetches a relevant "hotel building + city" image per hotel;
 * otherwise or on API failure, uses a single default placeholder. Run via POST /api/admin/hotels/refresh-images.
 */
@Service
public class HotelImageRefreshService {

    private static final Logger log = LoggerFactory.getLogger(HotelImageRefreshService.class);
    /** Default hotel image when no suitable image is found (neutral hotel interior). */
    private static final String DEFAULT_HOTEL_IMAGE = "https://images.pexels.com/photos/258154/pexels-photo-258154.jpeg?auto=compress&cs=tinysrgb&w=800";
    private static final long PEXELS_DELAY_MS = 400;

    private final HotelRepository hotelRepository;

    @Autowired(required = false)
    private HotelImageService hotelImageService;

    public HotelImageRefreshService(HotelRepository hotelRepository) {
        this.hotelRepository = hotelRepository;
    }

    public boolean isImageApiAvailable() {
        return hotelImageService != null;
    }

    /**
     * For each hotel: try to fetch a relevant image (hotel building + city) from Pexels when API is configured;
     * otherwise or on failure, set the default image. Avoids irrelevant results (e.g. wildlife) by using
     * city-based queries so each hotel gets a distinct, hotel-like image where possible.
     */
    @Async
    public void refreshImagesForAllHotelsAsync() {
        List<Hotel> hotels = hotelRepository.findAll();
        int updated = 0;
        for (Hotel hotel : hotels) {
            String imageUrl = DEFAULT_HOTEL_IMAGE;
            if (hotelImageService != null && !hotelImageService.isRateLimitHit()) {
                String city = hotel.getCity() != null ? hotel.getCity().trim() : "";
                String query = city.isEmpty() ? "luxury hotel exterior India" : "hotel building " + city + " India";
                Optional<String> fetched = hotelImageService.fetchImageUrl(query, (int) (hotel.getId() != null ? hotel.getId() % 500 : 0));
                if (fetched.isPresent()) {
                    imageUrl = fetched.get();
                    updated++;
                }
                if (PEXELS_DELAY_MS > 0) {
                    try {
                        Thread.sleep(PEXELS_DELAY_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            hotel.setImageUrl(imageUrl);
            hotelRepository.save(hotel);
        }
        log.info("Hotel images refreshed: {} hotels ({} with Pexels image, rest default).", hotels.size(), updated);
    }
}
