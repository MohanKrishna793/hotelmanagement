package com.smarthotel.hotelmanagement.controller;

import com.smarthotel.hotelmanagement.entity.Destination;
import com.smarthotel.hotelmanagement.repository.DestinationRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin/destinations")
public class AdminDestinationController {

    private final DestinationRepository destinationRepository;

    public AdminDestinationController(DestinationRepository destinationRepository) {
        this.destinationRepository = destinationRepository;
    }

    @GetMapping
    public List<Destination> getAll() {
        return destinationRepository.findAll();
    }

    @PostMapping
    public Destination create(@Valid @RequestBody CreateDestinationRequest request) {
        Destination d = new Destination(
                request.getName(),
                request.getCountry(),
                request.getCity(),
                request.getDescription() != null ? request.getDescription() : ""
        );
        return destinationRepository.save(d);
    }

    public static class CreateDestinationRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String country;
        @NotBlank
        private String city;
        private String description;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
