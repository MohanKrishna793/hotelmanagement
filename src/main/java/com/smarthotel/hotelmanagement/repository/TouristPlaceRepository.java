package com.smarthotel.hotelmanagement.repository;

import com.smarthotel.hotelmanagement.entity.TouristPlace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TouristPlaceRepository extends JpaRepository<TouristPlace, Long> {

    Optional<TouristPlace> findFirstByName(String name);
}

