package com.qualifacts.patient_portal.service;

import com.qualifacts.patient_portal.model.Appointment;
import com.qualifacts.patient_portal.model.AppointmentHistory;
import com.qualifacts.patient_portal.model.AppointmentHistoryAction;
import com.qualifacts.patient_portal.model.AppointmentStatus;
import com.qualifacts.patient_portal.model.Provider;
import com.qualifacts.patient_portal.repository.AppointmentHistoryRepository;
import com.qualifacts.patient_portal.repository.AppointmentRepository;
import com.qualifacts.patient_portal.repository.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Transactional
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final NotificationService notificationService;
    private final ProviderRepository providerRepository;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              AppointmentHistoryRepository appointmentHistoryRepository,
                              NotificationService notificationService,
                              ProviderRepository providerRepository) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.notificationService = notificationService;
        this.providerRepository = providerRepository;
    }

    /**
     * Acquires a database-level pessimistic write lock (SELECT ... FOR UPDATE) on the given provider.
     * Guarantees that only one transaction at a time can modify confirmed appointments for this provider,
     * completely eliminating write-skew / overlapping double-bookings even under simultaneous requests.
     */
    private void lockProviderForScheduleModification(String providerName) {
        if (providerName == null || providerName.trim().isEmpty()) {
            return;
        }
        String trimmedName = providerName.trim();
        // If the provider record doesn't exist yet, create it so we have a row to lock
        providerRepository.findById(trimmedName).orElseGet(() -> providerRepository.saveAndFlush(new Provider(trimmedName)));
        // Acquire PESSIMISTIC_WRITE lock
        providerRepository.findByNameForUpdate(trimmedName);
    }

    @Transactional(readOnly = true)
    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAllByOrderByAppointmentIdAsc();
    }

    @Transactional(readOnly = true)
    public List<AppointmentHistory> getAppointmentHistory(Long appointmentId) {
        return appointmentHistoryRepository.findByAppointmentIdOrderByHistoryIdDesc(appointmentId);
    }

    @Transactional(readOnly = true)
    public Appointment getAppointmentById(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found with ID: " + id));
    }

    public Appointment bookAppointment(LocalDateTime dateTime, String providerName, String appointmentType, String reason) {
        if (dateTime == null) {
            throw new IllegalArgumentException("Appointment date and time is required.");
        }
        if (providerName == null || providerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider name is required.");
        }
        if (appointmentType == null || appointmentType.trim().isEmpty()) {
            throw new IllegalArgumentException("Appointment type is required.");
        }

        Appointment appointment = new Appointment(dateTime, providerName.trim(), appointmentType.trim(), reason != null ? reason.trim() : "");
        appointment.setStatus(AppointmentStatus.PENDING);
        // To guarantee immediate database synchronization and version increment.
        Appointment saved = appointmentRepository.saveAndFlush(appointment);

        appointmentHistoryRepository.save(new AppointmentHistory(
                saved.getAppointmentId(),
                AppointmentHistoryAction.REQUESTED,
                "PATIENT",
                null,
                saved.getDateTime(),
                null,
                AppointmentStatus.PENDING,
                "Appointment requested by Patient"
        ));

        return saved;
    }

    private void validateVersion(Appointment appointment, Integer clientVersion) {
        if (clientVersion == null) {
            throw new IllegalArgumentException("Version is required for concurrency control.");
        }
        if (!clientVersion.equals(appointment.getVersion())) {
            throw new IllegalStateException(
                    "This appointment was modified by another user while you were viewing it. " +
                    "Your action was not applied, and the latest schedule is shown below."
            );
        }
    }

    public Appointment cancelAppointment(Long id, Integer clientVersion) {
        Appointment appointment = getAppointmentById(id);
        validateVersion(appointment, clientVersion);

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed appointments can be cancelled. Current status is " + appointment.getStatus());
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        // To guarantee immediate database synchronization and version increment.
        Appointment saved = appointmentRepository.saveAndFlush(appointment);

        appointmentHistoryRepository.save(new AppointmentHistory(
                saved.getAppointmentId(),
                AppointmentHistoryAction.CANCELLED,
                "PATIENT",
                saved.getDateTime(),
                saved.getDateTime(),
                AppointmentStatus.CONFIRMED,
                AppointmentStatus.CANCELLED,
                "Appointment cancelled by Patient"
        ));

        return saved;
    }

    public Appointment cancelAppointment(Long id) {
        return cancelAppointment(id, getAppointmentById(id).getVersion());
    }

    public Appointment confirmAppointment(Long id, Integer clientVersion) {
        Appointment appointment = getAppointmentById(id);
        validateVersion(appointment, clientVersion);

        if (appointment.getStatus() == AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Appointment is already confirmed.");
        }
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled appointments cannot be confirmed.");
        }
        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new IllegalStateException("Only pending appointments can be confirmed.");
        }

        // 1. Acquire pessimistic write lock on the provider to serialize schedule mutations for this provider
        lockProviderForScheduleModification(appointment.getProviderName());

        // 2. Check if the provider already has a confirmed appointment at this exact slot
        boolean hasConflict = appointmentRepository.existsByProviderNameAndDateTimeAndStatusAndAppointmentIdNot(
                appointment.getProviderName(),
                appointment.getDateTime(),
                AppointmentStatus.CONFIRMED,
                appointment.getAppointmentId()
        );
        if (hasConflict) {
            String formattedTime = appointment.getDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            throw new IllegalStateException(
                    "Cannot confirm appointment: " + appointment.getProviderName() +
                    " already has a confirmed appointment at " + formattedTime +
                    ". Please reschedule to another time."
            );
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        // To guarantee immediate database synchronization and version increment.
        Appointment saved = appointmentRepository.saveAndFlush(appointment);

        appointmentHistoryRepository.save(new AppointmentHistory(
                saved.getAppointmentId(),
                AppointmentHistoryAction.CONFIRMED,
                "PROVIDER",
                saved.getDateTime(),
                saved.getDateTime(),
                AppointmentStatus.PENDING,
                AppointmentStatus.CONFIRMED,
                "Appointment confirmed by Provider"
        ));

        // Dispatch notification with fault isolation: notification errors must never fail confirmation
        try {
            notificationService.notifyAppointmentConfirmed(saved);
        } catch (Exception ex) {
            log.error("Notification dispatch failed for confirmed appointment #{}: {}", saved.getAppointmentId(), ex.getMessage(), ex);
        }

        return saved;
    }

    public Appointment confirmAppointment(Long id) {
        return confirmAppointment(id, getAppointmentById(id).getVersion());
    }

    public Appointment rescheduleAppointment(Long id, LocalDateTime newDateTime, Integer clientVersion) {
        if (newDateTime == null) {
            throw new IllegalArgumentException("New appointment date and time is required for rescheduling.");
        }

        Appointment appointment = getAppointmentById(id);
        validateVersion(appointment, clientVersion);

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled appointments cannot be rescheduled.");
        }

        // 1. Acquire pessimistic write lock on the provider
        lockProviderForScheduleModification(appointment.getProviderName());

        // 2. If appointment is already CONFIRMED, ensure provider has no other CONFIRMED appointment at newDateTime
        if (appointment.getStatus() == AppointmentStatus.CONFIRMED) {
            boolean hasConflict = appointmentRepository.existsByProviderNameAndDateTimeAndStatusAndAppointmentIdNot(
                    appointment.getProviderName(),
                    newDateTime,
                    AppointmentStatus.CONFIRMED,
                    appointment.getAppointmentId()
            );
            if (hasConflict) {
                String formattedTime = newDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                throw new IllegalStateException(
                        "Cannot reschedule appointment: " + appointment.getProviderName() +
                        " already has a confirmed appointment at " + formattedTime +
                        ". Please select a different time."
                );
            }
        }

        LocalDateTime oldDateTime = appointment.getDateTime();
        appointment.setDateTime(newDateTime);
        // To guarantee immediate database synchronization and version increment.
        Appointment saved = appointmentRepository.saveAndFlush(appointment);

        String detailMsg = "Rescheduled from " + oldDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                + " to " + newDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                + " by Provider";

        appointmentHistoryRepository.save(new AppointmentHistory(
                saved.getAppointmentId(),
                AppointmentHistoryAction.RESCHEDULED,
                "PROVIDER",
                oldDateTime,
                newDateTime,
                saved.getStatus(),
                saved.getStatus(),
                detailMsg
        ));

        return saved;
    }

    public Appointment rescheduleAppointment(Long id, LocalDateTime newDateTime) {
        return rescheduleAppointment(id, newDateTime, getAppointmentById(id).getVersion());
    }
}
