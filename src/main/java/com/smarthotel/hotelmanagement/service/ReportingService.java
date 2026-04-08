package com.smarthotel.hotelmanagement.service;

import com.smarthotel.hotelmanagement.entity.Booking;
import com.smarthotel.hotelmanagement.entity.BookingStatus;
import com.smarthotel.hotelmanagement.repository.BookingRepository;
import com.smarthotel.hotelmanagement.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

        long availableRooms = roomRepository.countByAvailableTrue();
        long totalGuests = 0;
        try {
            totalGuests = allBookings.stream()
                    .filter(b -> b.getGuest() != null)
                    .map(b -> b.getGuest().getId())
                    .distinct().count();
        } catch (Exception ignored) {}

        double occupancyPercent = totalRooms > 0 ? (activeBookings * 100.0 / totalRooms) : 0;

        // Status breakdown for pie chart
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        statusCounts.put("BOOKED",     allBookings.stream().filter(b -> b.getStatus() == BookingStatus.BOOKED).count());
        statusCounts.put("CHECKED_IN", allBookings.stream().filter(b -> b.getStatus() == BookingStatus.CHECKED_IN).count());
        statusCounts.put("COMPLETED",  allBookings.stream().filter(b -> b.getStatus() == BookingStatus.COMPLETED).count());
        statusCounts.put("CANCELLED",  allBookings.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).count());

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalBookings",   totalBookings);
        map.put("totalRooms",      totalRooms);
        map.put("availableRooms",  availableRooms);
        map.put("totalGuests",     totalGuests);
        map.put("revenue",         revenue);
        map.put("activeBookings",  activeBookings);
        map.put("occupancyPercent", Math.round(occupancyPercent * 10) / 10.0);
        map.put("statusCounts",    statusCounts);
        return map;
    }

    public Map<String, Object> getRevenueByMonth(int months) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusMonths(months - 1).withDayOfMonth(1);

        // Build sorted month slots from oldest → newest so chart is in order
        List<String> labels   = new ArrayList<>();
        List<Double> revenues = new ArrayList<>();
        List<Long>   counts   = new ArrayList<>();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yyyy");

        // Pre-fill all months with 0
        LinkedHashMap<String, double[]> slots = new LinkedHashMap<>();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym  = YearMonth.from(today.minusMonths(i));
            String key    = ym.toString();         // "2025-06" — used for grouping
            String label  = ym.atDay(1).format(fmt); // "Jun 2025" — for chart
            slots.put(key, new double[]{0.0, 0.0}); // [revenue, count]
            labels.add(label);
        }

        List<Booking> bookings = bookingRepository.findAllWithRoomAndGuest().stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED && b.getCreatedAt() != null)
                .filter(b -> !b.getCreatedAt().toLocalDate().isBefore(start))
                .collect(Collectors.toList());

        for (Booking b : bookings) {
            YearMonth ym = YearMonth.from(b.getCreatedAt().toLocalDate());
            double[] slot = slots.get(ym.toString());
            if (slot != null) {
                slot[0] += b.getTotalCost() != null ? b.getTotalCost() : 0;
                slot[1] += 1;
            }
        }

        for (double[] slot : slots.values()) {
            revenues.add(slot[0]);
            counts.add((long) slot[1]);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("labels",   labels);
        result.put("revenues", revenues);
        result.put("counts",   counts);
        // keep old field for backward compat
        LinkedHashMap<String, Double> byMonth = new LinkedHashMap<>();
        List<String> keyList = new ArrayList<>(slots.keySet());
        for (int i = 0; i < keyList.size(); i++) {
            byMonth.put(labels.get(i), revenues.get(i));
        }
        result.put("byMonth", byMonth);
        return result;
    }
}
