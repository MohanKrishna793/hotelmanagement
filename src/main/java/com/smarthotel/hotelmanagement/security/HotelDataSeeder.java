package com.smarthotel.hotelmanagement.security;

import com.smarthotel.hotelmanagement.entity.*;
import com.smarthotel.hotelmanagement.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds sample hotels, destinations, rooms and nearby tourist places when the DB has no hotels.
 * Run scripts/delete-hotel-data.sql (optional), then restart the app to load this data.
 */
@Component
@Order(2)
public class HotelDataSeeder implements CommandLineRunner {

    private final DestinationRepository destinationRepository;
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final TouristPlaceRepository touristPlaceRepository;
    private final HotelTouristPlaceRepository hotelTouristPlaceRepository;

    public HotelDataSeeder(DestinationRepository destinationRepository,
                           HotelRepository hotelRepository,
                           RoomRepository roomRepository,
                           TouristPlaceRepository touristPlaceRepository,
                           HotelTouristPlaceRepository hotelTouristPlaceRepository) {
        this.destinationRepository = destinationRepository;
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.touristPlaceRepository = touristPlaceRepository;
        this.hotelTouristPlaceRepository = hotelTouristPlaceRepository;
    }

    @Override
    public void run(String... args) {
        if (hotelRepository.count() > 0) {
            return;
        }

        String img = "https://images.pexels.com/photos/258154/pexels-photo-258154.jpeg?auto=compress&cs=tinysrgb&w=800";

        // Mumbai
        Destination mumbaiDest = destinationRepository.save(
                new Destination("Mumbai", "India", "Mumbai", "Financial capital"));
        Hotel seaFace = saveHotel("SmartHotel Sea Face", "Marine Drive", "Mumbai", "India",
                "Beachfront stay with sea views.", img,
                "Free WiFi, Pool, Beach Access, Restaurant, Bar, AC, Parking, 24/7 Front Desk",
                7500.0, 4.8, 18.9440, 72.8225, mumbaiDest);
        addRooms(seaFace, "MUM-", 5200, 8000, 9500);
        TouristPlace gateway = savePlace("Gateway of India", "historical", 18.9220, 72.8347);
        TouristPlace marineDrive = savePlace("Marine Drive", "nature", 18.9440, 72.8225);
        TouristPlace juhu = savePlace("Juhu Beach", "nature", 19.0880, 72.8260);
        link(seaFace, gateway, 5.0);
        link(seaFace, marineDrive, 1.0);
        link(seaFace, juhu, 8.0);

        // Hyderabad
        Destination hydDest = destinationRepository.save(
                new Destination("Hyderabad", "India", "Hyderabad", "City of pearls"));
        Hotel lakeView = saveHotel("SmartHotel Lake View", "Banjara Hills", "Hyderabad", "India",
                "Luxury stay with premium amenities.", img,
                "Free WiFi, Pool, Gym, Spa, Restaurant, Room Service, AC, Parking, 24/7 Front Desk",
                5200.0, 4.5, 17.4239, 78.4738, hydDest);
        addRooms(lakeView, "HYD-", 5200, 8000, 9500);
        TouristPlace charminar = savePlace("Charminar", "historical", 17.3616, 78.4747);
        TouristPlace golconda = savePlace("Golconda Fort", "historical", 17.3833, 78.4011);
        link(lakeView, charminar, 8.5);
        link(lakeView, golconda, 12.0);

        // Delhi
        Destination delhiDest = destinationRepository.save(
                new Destination("Delhi", "India", "New Delhi", "Capital"));
        Hotel central = saveHotel("SmartHotel Central", "Connaught Place", "New Delhi", "India",
                "Central location for business and leisure.", img,
                "Free WiFi, Gym, Restaurant, AC, Parking, Conference Rooms, Laundry",
                4500.0, 4.2, 28.6139, 77.2090, delhiDest);
        addRooms(central, "DL-", 4500, 7000, 8500);
        TouristPlace indiaGate = savePlace("India Gate", "historical", 28.6129, 77.2295);
        TouristPlace redFort = savePlace("Red Fort", "historical", 28.6562, 77.2410);
        link(central, indiaGate, 4.0);
        link(central, redFort, 5.0);
    }

    private Hotel saveHotel(String name, String address, String city, String country,
                            String desc, String imageUrl, String amenities,
                            double basePrice, double rating, double lat, double lng, Destination dest) {
        Hotel h = new Hotel();
        h.setName(name);
        h.setAddress(address);
        h.setCity(city);
        h.setCountry(country);
        h.setDescription(desc);
        h.setImageUrl(imageUrl);
        h.setAmenities(amenities);
        h.setBasePricePerNight(basePrice);
        h.setRating(rating);
        h.setLatitude(lat);
        h.setLongitude(lng);
        h.setDestination(dest);
        return hotelRepository.save(h);
    }

    private void addRooms(Hotel hotel, String prefix, double deluxePrice, double suitePrice, double premiumPrice) {
        roomRepository.save(createRoom(hotel, prefix + "101", "DELUXE", deluxePrice));
        roomRepository.save(createRoom(hotel, prefix + "102", "DELUXE", deluxePrice));
        roomRepository.save(createRoom(hotel, prefix + "201", "SUITE", suitePrice));
        roomRepository.save(createRoom(hotel, prefix + "202", "SUITE", suitePrice));
        roomRepository.save(createRoom(hotel, prefix + "301", "PREMIUM", premiumPrice));
    }

    private Room createRoom(Hotel hotel, String number, String type, double price) {
        Room r = new Room(number, type, price, true);
        r.setHotel(hotel);
        return r;
    }

    private TouristPlace savePlace(String name, String type, double lat, double lng) {
        return touristPlaceRepository.save(
                new TouristPlace(name, type, "Popular visit", lat, lng, 9.0));
    }

    private void link(Hotel hotel, TouristPlace place, double km) {
        hotelTouristPlaceRepository.save(new HotelTouristPlace(hotel, place, km));
    }
}
