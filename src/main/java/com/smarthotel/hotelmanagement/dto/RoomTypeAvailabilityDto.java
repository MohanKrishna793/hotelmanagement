
package com.smarthotel.hotelmanagement.dto;

import java.util.List;

/** Room type with availability count for given dates. */
public class RoomTypeAvailabilityDto {
    private String type;
    private Double price;
    private int totalCount;
    private int availableCount;
    private List<Long> availableRoomIds;

    public RoomTypeAvailabilityDto(String type, Double price, int totalCount, int availableCount, List<Long> availableRoomIds) {
        this.type = type;
        this.price = price;
        this.totalCount = totalCount;
        this.availableCount = availableCount;
        this.availableRoomIds = availableRoomIds;
    }

    public String getType() { return type; }
    public Double getPrice() { return price; }
    public int getTotalCount() { return totalCount; }
    public int getAvailableCount() { return availableCount; }
    public List<Long> getAvailableRoomIds() { return availableRoomIds; }
}
