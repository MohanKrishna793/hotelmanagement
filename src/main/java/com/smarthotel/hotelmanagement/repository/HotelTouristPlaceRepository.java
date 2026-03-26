package com.smarthotel.hotelmanagement.repository;

import com.smarthotel.hotelmanagement.entity.Hotel;
import com.smarthotel.hotelmanagement.entity.HotelTouristPlace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HotelTouristPlaceRepository extends JpaRepository<HotelTouristPlace, Long> {

    List<HotelTouristPlace> findByHotelOrderByDistanceKmAsc(Hotel hotel);
}

