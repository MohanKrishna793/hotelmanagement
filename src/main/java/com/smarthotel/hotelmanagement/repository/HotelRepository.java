package com.smarthotel.hotelmanagement.repository;

import com.smarthotel.hotelmanagement.entity.Destination;
import com.smarthotel.hotelmanagement.entity.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HotelRepository extends JpaRepository<Hotel, Long> {

    List<Hotel> findByDestination(Destination destination);

    List<Hotel> findByCityIgnoreCase(String city);

    List<Hotel> findByCityContainingIgnoreCase(String city);

    Optional<Hotel> findFirstByNameIgnoreCaseAndCityIgnoreCaseAndCountryIgnoreCase(String name, String city, String country);
}

