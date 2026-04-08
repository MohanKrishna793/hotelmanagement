package com.smarthotel.hotelmanagement.controller;

import com.smarthotel.hotelmanagement.entity.Hotel;
import com.smarthotel.hotelmanagement.service.HotelImageRefreshService;
import com.smarthotel.hotelmanagement.service.HotelService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/hotels")
public class AdminHotelController {

    private final HotelService hotelService;
    private final HotelImageRefreshService hotelImageRefreshService;

    public AdminHotelController(HotelService hotelService,
                                HotelImageRefreshService hotelImageRefreshService) {
        this.hotelService = hotelService;
        this.hotelImageRefreshService = hotelImageRefreshService;
    }

    @GetMapping
    public List<Hotel> getAll() {
        return hotelService.getAllHotels();
    }

    /**
     * Resets all hotel images to the default placeholder (no external API).
     * Use this to fix hotels that had irrelevant images; runs in background.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        if (!hotelService.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found");
        }
        hotelService.deleteHotel(id);
        return ResponseEntity.ok(Map.of("message", "Hotel removed", "id", id));
    }

    @PostMapping("/refresh-images")
    public ResponseEntity<?> refreshImages() {
        long count = hotelService.getAllHotels().size();
        hotelImageRefreshService.refreshImagesForAllHotelsAsync();
        return ResponseEntity.accepted()
                .body(Map.of(
                        "message", "Resetting images to default for " + count + " hotels. Check server logs when done."
                ));
    }

    @PostMapping
    public Hotel create(@Valid @RequestBody CreateHotelRequest request) {
        Hotel hotel = new Hotel();
        hotel.setName(request.getName());
        hotel.setAddress(request.getAddress() != null ? request.getAddress() : "");
        hotel.setCity(request.getCity());
        hotel.setCountry(request.getCountry());
        hotel.setDescription(request.getDescription());
        hotel.setImageUrl(request.getImageUrl());
        hotel.setAmenities(request.getAmenities());
        hotel.setBasePricePerNight(request.getBasePricePerNight());
        hotel.setRating(request.getRating());
        hotel.setLatitude(request.getLatitude());
        hotel.setLongitude(request.getLongitude());
        return hotelService.createHotel(hotel, request.getDestinationId());
    }

    public static class CreateHotelRequest {
        @NotBlank
        private String name;
        private String address;
        @NotBlank
        private String city;
        @NotBlank
        private String country;
        private String description;
        private String imageUrl;
        private String amenities;
        private Double basePricePerNight;
        private Double rating;
        private Double latitude;
        private Double longitude;
        private Long destinationId;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public String getAmenities() { return amenities; }
        public void setAmenities(String amenities) { this.amenities = amenities; }
        public Double getBasePricePerNight() { return basePricePerNight; }
        public void setBasePricePerNight(Double basePricePerNight) { this.basePricePerNight = basePricePerNight; }
        public Double getRating() { return rating; }
        public void setRating(Double rating) { this.rating = rating; }
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        public Long getDestinationId() { return destinationId; }
        public void setDestinationId(Long destinationId) { this.destinationId = destinationId; }
    }
}
