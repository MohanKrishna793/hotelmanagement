package com.smarthotel.hotelmanagement.service;

import com.smarthotel.hotelmanagement.entity.Booking;
import com.smarthotel.hotelmanagement.entity.BookingStatus;
import com.smarthotel.hotelmanagement.repository.BookingRepository;
import com.smarthotel.hotelmanagement.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportingService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;

    public ReportingService(BookingRepository bookingRepository, RoomRepository roomRepository) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
    }

    public Map<String, Object> getDashboardStats() {
        List<Booking> allBookings = bookingRepository.findAllWithRoomAndGuest();
        long totalRooms = roomRepository.count();
        long totalBookings = allBookings.size();
        double revenue = allBookings.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .map(Booking::getTotalCost)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        LocalDate today = LocalDate.now();
        long activeBookings = allBookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.BOOKED || b.getStatus() == BookingStatus.CHECKED_IN)
                .filter(b -> b.getCheckOutDate() != null && !b.getCheckOutDate().isBefore(today))
                .count();
        double occupancyPercent = totalRooms > 0 ? (activeBookings * 100.0 / totalRooms) : 0;

        Map<String, Object> map = new HashMap<>();
        map.put("totalBookings", totalBookings);
        map.put("totalRooms", totalRooms);
        map.put("revenue", revenue);
        map.put("activeBookings", activeBookings);
        map.put("occupancyPercent", Math.round(occupancyPercent * 10) / 10.0);
        return map;
    }

    public Map<String, Object> getRevenueByMonth(int months) {
        LocalDate start = LocalDate.now().minusMonths(months);
        List<Booking> bookings = bookingRepository.findAllWithRoomAndGuest().stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED && b.getCreatedAt() != null)
                .filter(b -> b.getCreatedAt().toLocalDate().isAfter(start.minusDays(1)))
                .collect(Collectors.toList());
        Map<String, Double> byMonth = new HashMap<>();
        for (Booking b : bookings) {
            YearMonth ym = YearMonth.from(b.getCreatedAt().toLocalDate());
            String key = ym.toString();
            byMonth.merge(key, b.getTotalCost() != null ? b.getTotalCost() : 0, Double::sum);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("byMonth", byMonth);
        return result;
    }
}
