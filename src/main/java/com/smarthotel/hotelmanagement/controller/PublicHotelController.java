package com.smarthotel.hotelmanagement.controller;

import com.smarthotel.hotelmanagement.dto.HotelSearchResultDto;
import com.smarthotel.hotelmanagement.dto.RoomSummaryDto;
import com.smarthotel.hotelmanagement.dto.RoomTypeAvailabilityDto;
import com.smarthotel.hotelmanagement.entity.Hotel;
import com.smarthotel.hotelmanagement.entity.Room;
import com.smarthotel.hotelmanagement.entity.TouristPlace;
import com.smarthotel.hotelmanagement.service.HotelService;
import com.smarthotel.hotelmanagement.service.TouristRecommendationService;
import com.smarthotel.hotelmanagement.service.StatesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public")
public class PublicHotelController {

    private final HotelService hotelService;
    private final TouristRecommendationService touristRecommendationService;
    private final StatesService statesService;

    public PublicHotelController(HotelService hotelService,
                                 TouristRecommendationService touristRecommendationService,
                                 StatesService statesService) {
        this.hotelService = hotelService;
        this.touristRecommendationService = touristRecommendationService;
        this.statesService = statesService;
    }

    /** Indian states and UTs for dropdown. From DB (destinations) or static list. */
    @GetMapping("/states")
    public List<String> getStates() {
        return statesService.getStateNames();
    }

    /** Cities in the given state (for State → City → Hotels flow). */
    @GetMapping("/cities")
    public List<String> getCities(@RequestParam String state) {
        return statesService.getCitiesByState(state);
    }

    @GetMapping("/hotels")
    public List<HotelSearchResultDto> searchHotels(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String hotelClass,
            @RequestParam(required = false) String hotelType,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) LocalDate checkIn,
            @RequestParam(required = false) LocalDate checkOut
    ) {
        return hotelService.searchHotels(lat, lng, radiusKm, city, state, hotelClass, hotelType, minPrice, maxPrice, checkIn, checkOut);
    }

    @GetMapping("/hotels/{hotelId}/rooms")
    public List<RoomSummaryDto> getAvailableRoomsForHotel(
            @PathVariable Long hotelId,
            @RequestParam(required = false) LocalDate checkIn,
            @RequestParam(required = false) LocalDate checkOut
    ) {
        Hotel hotel = hotelService.getHotelById(hotelId);
        List<Room> rooms = (checkIn != null && checkOut != null && checkOut.isAfter(checkIn))
                ? hotelService.getAvailableRoomsForHotelForDates(hotel, checkIn, checkOut)
                : hotelService.getAvailableRoomsForHotel(hotel);
        return rooms.stream()
                .map(r -> new RoomSummaryDto(r.getId(), r.getRoomNumber(), r.getType(), r.getPrice(), Boolean.TRUE, r.getCapacity()))
                .collect(Collectors.toList());
    }

    @GetMapping("/hotels/{hotelId}/room-types")
    public List<RoomTypeAvailabilityDto> getRoomTypeAvailability(
            @PathVariable Long hotelId,
            @RequestParam LocalDate checkIn,
            @RequestParam LocalDate checkOut
    ) {
        return hotelService.getRoomTypeAvailability(hotelId, checkIn, checkOut);
    }

    @GetMapping("/hotels/{hotelId}/recommendations")
    public List<TouristPlace> getNearbyTouristPlaces(@PathVariable Long hotelId) {
        return touristRecommendationService.getRecommendationsForHotel(hotelId);
    }
}

