package com.smarthotel.hotelmanagement.service;

import com.smarthotel.hotelmanagement.dto.HotelSearchResultDto;
import com.smarthotel.hotelmanagement.dto.RoomSummaryDto;
import com.smarthotel.hotelmanagement.dto.RoomTypeAvailabilityDto;
import com.smarthotel.hotelmanagement.entity.Destination;
import com.smarthotel.hotelmanagement.entity.Hotel;
import com.smarthotel.hotelmanagement.entity.Room;
import com.smarthotel.hotelmanagement.repository.BookingRepository;
import com.smarthotel.hotelmanagement.repository.DestinationRepository;
import com.smarthotel.hotelmanagement.repository.HotelRepository;
import com.smarthotel.hotelmanagement.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HotelService {

    private final HotelRepository hotelRepository;
    private final DestinationRepository destinationRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    public HotelService(HotelRepository hotelRepository,
                        DestinationRepository destinationRepository,
                        RoomRepository roomRepository,
                        BookingRepository bookingRepository) {
        this.hotelRepository = hotelRepository;
        this.destinationRepository = destinationRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    public Hotel createHotel(Hotel hotel, Long destinationId) {
        if (destinationId != null) {
            Destination destination = destinationRepository.findById(destinationId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Destination not found"));
            hotel.setDestination(destination);
        }
        return hotelRepository.save(hotel);
    }

    public List<Hotel> getAllHotels() {
        return hotelRepository.findAll();
    }

    public List<Hotel> findHotelsByDestination(Long destinationId) {
        Destination destination = destinationRepository.findById(destinationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Destination not found"));
        return hotelRepository.findByDestination(destination);
    }

    public List<Hotel> findHotelsByCity(String city) {
        return hotelRepository.findByCityIgnoreCase(city);
    }

    public Hotel getHotelById(Long id) {
        return hotelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found"));
    }

    public List<Room> getAvailableRoomsForHotel(Hotel hotel) {
        return roomRepository.findByHotelAndAvailableTrue(hotel);
    }

    /** Rooms available for the given date range (not booked in that range). */
    public List<Room> getAvailableRoomsForHotelForDates(Hotel hotel, LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn))
            return List.of();
        List<Room> all = roomRepository.findByHotel(hotel);
        List<Long> bookedIds = bookingRepository.findBookedRoomIdsForHotelInRange(hotel.getId(), checkIn, checkOut);
        Set<Long> bookedSet = new HashSet<>(bookedIds);
        return all.stream().filter(r -> !bookedSet.contains(r.getId())).collect(Collectors.toList());
    }

    /** Room types with availability count for the given dates. */
    public List<RoomTypeAvailabilityDto> getRoomTypeAvailability(Long hotelId, LocalDate checkIn, LocalDate checkOut) {
        Hotel hotel = getHotelById(hotelId);
        List<Room> available = getAvailableRoomsForHotelForDates(hotel, checkIn, checkOut);
        Map<String, List<Room>> byType = available.stream().collect(Collectors.groupingBy(r -> r.getType() != null ? r.getType() : ""));
        return byType.entrySet().stream()
                .filter(e -> !e.getKey().isEmpty())
                .map(e -> {
                    List<Room> rooms = e.getValue();
                    Double price = rooms.stream().map(Room::getPrice).filter(Objects::nonNull).findFirst().orElse(0.0);
                    List<Long> ids = rooms.stream().map(Room::getId).collect(Collectors.toList());
                    return new RoomTypeAvailabilityDto(e.getKey(), price, rooms.size(), rooms.size(), ids);
                })
                .collect(Collectors.toList());
    }

    public List<HotelSearchResultDto> searchHotels(
            Double lat,
            Double lng,
            Double radiusKm,
            String city,
            String state,
            String hotelClass,
            String hotelType,
            Double minPrice,
            Double maxPrice,
            LocalDate checkIn,
            LocalDate checkOut
    ) {
        List<Hotel> hotels;
        if (state != null && !state.isBlank()) {
            String stateTrim = state.trim();
            hotels = destinationRepository.findByNameAndCountry(stateTrim, "India")
                    .map(hotelRepository::findByDestination)
                    .orElseGet(() -> destinationRepository.findAll().stream()
                            .filter(d -> stateTrim.equalsIgnoreCase(d.getName()))
                            .flatMap(d -> hotelRepository.findByDestination(d).stream())
                            .collect(Collectors.toList()));
            if (hotels.isEmpty()) {
                // Fallback for legacy rows where destination linkage may be missing.
                hotels = hotelRepository.findAll().stream()
                        .filter(h -> h.getAddress() != null && h.getAddress().toLowerCase().contains(stateTrim.toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (city != null && !city.isBlank()) {
                String cityTrim = city.trim();
                hotels = hotels.stream()
                        .filter(h -> h.getCity() != null && h.getCity().equalsIgnoreCase(cityTrim))
                        .collect(Collectors.toList());
            }
        } else if (lat != null && lng != null && radiusKm != null) {
            hotels = hotelRepository.findAll();
            hotels = hotels.stream()
                    .filter(h -> h.getLatitude() != null && h.getLongitude() != null)
                    .filter(h -> {
                        double d = haversine(lat, lng, h.getLatitude(), h.getLongitude());
                        return d <= radiusKm;
                    })
                    .collect(Collectors.toList());
        } else if (city != null && !city.isBlank()) {
            hotels = hotelRepository.findByCityIgnoreCase(city.trim());
            if (hotels.isEmpty()) {
                hotels = hotelRepository.findByCityContainingIgnoreCase(city.trim());
            }
        } else {
            hotels = hotelRepository.findAll();
        }

        final boolean useDateFilter = checkIn != null && checkOut != null && checkOut.isAfter(checkIn);
        final String roomTypeFilter = (hotelClass != null && !hotelClass.isBlank()) ? hotelClass.trim() : null;
        if (roomTypeFilter != null) {
            hotels = hotels.stream()
                    .filter(h -> {
                        List<Room> available = useDateFilter
                                ? getAvailableRoomsForHotelForDates(h, checkIn, checkOut)
                                : roomRepository.findByHotelAndAvailableTrue(h);
                        return available.stream()
                                .anyMatch(r -> r.getType() != null && r.getType().equalsIgnoreCase(roomTypeFilter));
                    })
                    .collect(Collectors.toList());
        }
        final String hotelTypeFilter = (hotelType != null && !hotelType.isBlank()) ? hotelType.trim() : null;
        if (hotelTypeFilter != null) {
            hotels = hotels.stream()
                    .filter(h -> h.getDescription() != null && h.getDescription().toLowerCase().contains(hotelTypeFilter.toLowerCase()))
                    .collect(Collectors.toList());
        }

        return hotels.stream()
                .map(h -> {
                    List<Room> rooms = useDateFilter
                            ? getAvailableRoomsForHotelForDates(h, checkIn, checkOut)
                            : roomRepository.findByHotelAndAvailableTrue(h);
                    if (roomTypeFilter != null) {
                        rooms = rooms.stream()
                                .filter(r -> r.getType() != null && r.getType().equalsIgnoreCase(roomTypeFilter))
                                .collect(Collectors.toList());
                    }
                    Double startingPrice = rooms.isEmpty() ? null : rooms.stream()
                            .map(Room::getPrice)
                            .filter(Objects::nonNull)
                            .min(Double::compareTo)
                            .orElse(h.getBasePricePerNight() != null ? h.getBasePricePerNight() : 0.0);

                    Double distance = null;
                    if (lat != null && lng != null && h.getLatitude() != null && h.getLongitude() != null) {
                        distance = haversine(lat, lng, h.getLatitude(), h.getLongitude());
                    }

                    HotelSearchResultDto dto = new HotelSearchResultDto(
                            h.getId(),
                            h.getName(),
                            h.getCity(),
                            h.getCountry(),
                            null,
                            distance,
                            h.getRating(),
                            startingPrice
                    );
                    dto.setImageUrl(h.getImageUrl());
                    dto.setAmenities(h.getAmenities());
                    return dto;
                })
                .filter(dto -> {
                    if (dto.getStartingPrice() == null || dto.getStartingPrice() <= 0) return false; // no available rooms
                    if (minPrice != null && dto.getStartingPrice() < minPrice) return false;
                    if (maxPrice != null && dto.getStartingPrice() > maxPrice) return false;
                    return true;
                })
                .sorted(Comparator.comparing(h -> h.getDistanceKm() == null ? Double.MAX_VALUE : h.getDistanceKm()))
                .collect(Collectors.toList());
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}

