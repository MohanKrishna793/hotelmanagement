package com.smarthotel.hotelmanagement.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "discount_codes")
public class DiscountCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /** Percentage off (e.g. 10 = 10% off). */
    @Column(name = "percent_off")
    private Integer percentOff;

    /** Fixed amount off in INR. */
    @Column(name = "amount_off")
    private Double amountOff;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "min_nights")
    private Integer minNights; // e.g. 2 for "10% off for 2+ nights"

    @Column(nullable = false)
    private Boolean active = true;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Integer getPercentOff() { return percentOff; }
    public void setPercentOff(Integer percentOff) { this.percentOff = percentOff; }
    public Double getAmountOff() { return amountOff; }
    public void setAmountOff(Double amountOff) { this.amountOff = amountOff; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public Integer getMinNights() { return minNights; }
    public void setMinNights(Integer minNights) { this.minNights = minNights; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
