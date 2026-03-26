package com.smarthotel.hotelmanagement.controller;

import com.smarthotel.hotelmanagement.service.AuditService;
import com.smarthotel.hotelmanagement.service.ReportingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class ReportingController {

    private final ReportingService reportingService;
    private final AuditService auditService;

    public ReportingController(ReportingService reportingService, AuditService auditService) {
        this.reportingService = reportingService;
        this.auditService = auditService;
    }

    @GetMapping("/reports/dashboard")
    public Map<String, Object> getDashboard() {
        return reportingService.getDashboardStats();
    }

    @GetMapping("/reports/revenue")
    public Map<String, Object> getRevenue(@RequestParam(defaultValue = "12") int months) {
        return reportingService.getRevenueByMonth(months);
    }

    @GetMapping("/audit-logs")
    public List<?> getAuditLogs(@RequestParam(defaultValue = "100") int limit) {
        return auditService.getRecent(limit);
    }
}
