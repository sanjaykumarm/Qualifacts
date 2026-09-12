package com.qualifacts.patient_portal.service;

import com.qualifacts.patient_portal.model.Appointment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Service
public class StubNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(StubNotificationService.class);

    @Override
    public void notifyAppointmentConfirmed(Appointment appointment) {
        if (appointment == null) {
            log.warn("Cannot send notification: appointment is null");
            return;
        }

        try {
            // Simulating email/SMS notification dispatch via log statement
            log.info("[NOTIFICATION STUB] Would send confirmation notification to patient");
        } catch (Exception ex) {
            // Guarantee fault tolerance: log the error and never propagate to caller
            log.error("Failed to process notification for appointment #{}: {}", appointment.getAppointmentId(), ex.getMessage(), ex);
        }
    }
}
