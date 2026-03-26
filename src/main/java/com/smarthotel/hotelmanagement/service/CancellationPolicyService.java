package com.smarthotel.hotelmanagement.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Service
public class CancellationPolicyService {

    @Value("${app.cancellation.free-hours-before:24}")
    private int freeCancelHoursBefore;

    @Value("${app.cancellation.one-night-fee-if-late:true}")
    private boolean oneNightFeeIfLate;

    /** Refund amount (positive = amount to refund). totalPaid - penalty = refund. */
    public double calculateRefund(double totalCost, LocalDate checkInDate, LocalDate checkOutDate) {
        LocalDateTime checkInDateTime = checkInDate.atTime(LocalTime.of(12, 0));
        long hoursUntilCheckIn = ChronoUnit.HOURS.between(LocalDateTime.now(), checkInDateTime);
        if (hoursUntilCheckIn >= freeCancelHoursBefore) {
            return totalCost; // full refund
        }
        if (!oneNightFeeIfLate) {
            return 0;
        }
        long nights = Math.max(1, ChronoUnit.DAYS.between(checkInDate, checkOutDate));
        double oneNightCost = totalCost / nights;
        return Math.max(0, totalCost - oneNightCost);
    }

    public int getFreeCancelHoursBefore() {
        return freeCancelHoursBefore;
    }

    public boolean isOneNightFeeIfLate() {
        return oneNightFeeIfLate;
    }

    public String getPolicyDescription() {
        return "Free cancellation until " + freeCancelHoursBefore + " hours before check-in. After that, one night charge applies.";
    }
}
