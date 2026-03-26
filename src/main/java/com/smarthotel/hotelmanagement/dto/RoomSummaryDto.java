package com.smarthotel.hotelmanagement.dto;

public class RoomSummaryDto {

    private Long id;
    private String roomNumber;
    private String type;
    private Double price;
    private Boolean available;
    private Integer capacity;

    public RoomSummaryDto() {
    }

    public RoomSummaryDto(Long id, String type, Double price, Boolean available) {
        this.id = id;
        this.type = type;
        this.price = price;
        this.available = available;
    }

    public RoomSummaryDto(Long id, String roomNumber, String type, Double price, Boolean available) {
        this(id, roomNumber, type, price, available, null);
    }

    public RoomSummaryDto(Long id, String roomNumber, String type, Double price, Boolean available, Integer capacity) {
        this.id = id;
        this.roomNumber = roomNumber;
        this.type = type;
        this.price = price;
        this.available = available;
        this.capacity = capacity;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Boolean getAvailable() {
        return available;
    }

    public void setAvailable(Boolean available) {
        this.available = available;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }
}

