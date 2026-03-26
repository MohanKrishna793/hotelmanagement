package com.smarthotel.hotelmanagement.repository;

import com.smarthotel.hotelmanagement.entity.DiscountCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface DiscountCodeRepository extends JpaRepository<DiscountCode, Long> {
    Optional<DiscountCode> findByCodeIgnoreCaseAndActiveTrue(String code);

    default Optional<DiscountCode> findValidCode(String code, LocalDate date) {
        return findByCodeIgnoreCaseAndActiveTrue(code).filter(dc -> {
            if (dc.getValidFrom() != null && date.isBefore(dc.getValidFrom())) return false;
            if (dc.getValidUntil() != null && date.isAfter(dc.getValidUntil())) return false;
            return true;
        });
    }
}
