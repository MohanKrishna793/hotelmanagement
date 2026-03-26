package com.smarthotel.hotelmanagement.dto;

public class HotelSearchResultDto {

    private Long id;
    private String name;
    private String city;
    private String country;
    private String hotelClass;
    private Double distanceKm;
    private Double rating;
    private Double startingPrice;
    private String imageUrl;
    private String amenities;

    public HotelSearchResultDto() {
    }

    public HotelSearchResultDto(Long id, String name, String city, String country,
                                String hotelClass, Double distanceKm,
                                Double rating, Double startingPrice) {
        this.id = id;
        this.name = name;
        this.city = city;
        this.country = country;
        this.hotelClass = hotelClass;
        this.distanceKm = distanceKm;
        this.rating = rating;
        this.startingPrice = startingPrice;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getHotelClass() {
        return hotelClass;
    }

    public void setHotelClass(String hotelClass) {
        this.hotelClass = hotelClass;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public Double getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(Double startingPrice) {
        this.startingPrice = startingPrice;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getAmenities() {
        return amenities;
    }

    public void setAmenities(String amenities) {
        this.amenities = amenities;
    }
}

