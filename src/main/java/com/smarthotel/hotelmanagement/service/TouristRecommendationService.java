package com.smarthotel.hotelmanagement.service;

import com.smarthotel.hotelmanagement.entity.Hotel;
import com.smarthotel.hotelmanagement.entity.HotelTouristPlace;
import com.smarthotel.hotelmanagement.entity.TouristPlace;
import com.smarthotel.hotelmanagement.repository.HotelRepository;
import com.smarthotel.hotelmanagement.repository.HotelTouristPlaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TouristRecommendationService {

    private final HotelRepository hotelRepository;
    private final HotelTouristPlaceRepository hotelTouristPlaceRepository;

    public TouristRecommendationService(HotelRepository hotelRepository,
                                        HotelTouristPlaceRepository hotelTouristPlaceRepository) {
        this.hotelRepository = hotelRepository;
        this.hotelTouristPlaceRepository = hotelTouristPlaceRepository;
    }

    public List<TouristPlace> getRecommendationsForHotel(Long hotelId) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found"));

        List<HotelTouristPlace> mappings =
                hotelTouristPlaceRepository.findByHotelOrderByDistanceKmAsc(hotel);

        return mappings.stream()
                .map(HotelTouristPlace::getTouristPlace)
                .collect(Collectors.toList());
    }
}

