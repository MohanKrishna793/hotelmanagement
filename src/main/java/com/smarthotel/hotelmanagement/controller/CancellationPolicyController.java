package com.smarthotel.hotelmanagement.controller;

import com.smarthotel.hotelmanagement.service.CancellationPolicyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class CancellationPolicyController {

    private final CancellationPolicyService cancellationPolicyService;

    public CancellationPolicyController(CancellationPolicyService cancellationPolicyService) {
        this.cancellationPolicyService = cancellationPolicyService;
    }

    @GetMapping("/cancellation-policy")
    public Map<String, String> getPolicy() {
        return Map.of(
                "description", cancellationPolicyService.getPolicyDescription(),
                "freeCancelHoursBefore", String.valueOf(cancellationPolicyService.getFreeCancelHoursBefore()),
                "oneNightFeeIfLate", String.valueOf(cancellationPolicyService.isOneNightFeeIfLate())
        );
    }
}
