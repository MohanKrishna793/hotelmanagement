package com.smarthotel.hotelmanagement.repository;

import com.smarthotel.hotelmanagement.entity.Destination;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DestinationRepository extends JpaRepository<Destination, Long> {

    Optional<Destination> findByNameAndCountry(String name, String country);
}

