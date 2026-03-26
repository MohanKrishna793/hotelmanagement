package com.smarthotel.hotelmanagement.repository;

import com.smarthotel.hotelmanagement.entity.Guest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GuestRepository extends JpaRepository<Guest, Long> {

    Optional<Guest> findByEmail(String email);
}

