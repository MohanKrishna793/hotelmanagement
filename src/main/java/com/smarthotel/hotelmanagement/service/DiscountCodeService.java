package com.smarthotel.hotelmanagement.service;

import com.smarthotel.hotelmanagement.entity.DiscountCode;
import com.smarthotel.hotelmanagement.repository.DiscountCodeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class DiscountCodeService {

    private final DiscountCodeRepository discountCodeRepository;

    public DiscountCodeService(DiscountCodeRepository discountCodeRepository) {
        this.discountCodeRepository = discountCodeRepository;
    }

    public Optional<DiscountCode> findValid(String code, LocalDate checkInDate, int nights) {
        return discountCodeRepository.findValidCode(code, checkInDate != null ? checkInDate : LocalDate.now())
                .filter(dc -> dc.getMinNights() == null || nights >= dc.getMinNights());
    }

    /** Apply discount to total. Returns discounted total. */
    public double applyDiscount(double total, DiscountCode dc) {
        if (dc == null) return total;
        if (dc.getPercentOff() != null && dc.getPercentOff() > 0) {
            return total * (1 - dc.getPercentOff() / 100.0);
        }
        if (dc.getAmountOff() != null && dc.getAmountOff() > 0) {
            return Math.max(0, total - dc.getAmountOff());
        }
        return total;
    }
}
