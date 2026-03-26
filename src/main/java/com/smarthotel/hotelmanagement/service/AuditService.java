package com.smarthotel.hotelmanagement.service;

import com.smarthotel.hotelmanagement.entity.AuditLog;
import com.smarthotel.hotelmanagement.repository.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(String userEmail, String action, String entityType, Long entityId, String details) {
        try {
            auditLogRepository.save(new AuditLog(userEmail, action, entityType, entityId, details));
        } catch (Exception e) {
            // do not fail the main operation
        }
    }

    public List<AuditLog> getRecent(int limit) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.min(limit, 200)));
    }
}
