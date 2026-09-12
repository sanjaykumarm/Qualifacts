package com.qualifacts.patient_portal.repository;

import com.qualifacts.patient_portal.model.Appointment;
import com.qualifacts.patient_portal.model.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findAllByOrderByAppointmentIdAsc();

    boolean existsByProviderNameAndDateTimeAndStatus(
            String providerName, LocalDateTime dateTime, AppointmentStatus status);

    boolean existsByProviderNameAndDateTimeAndStatusAndAppointmentIdNot(
            String providerName, LocalDateTime dateTime, AppointmentStatus status, Long appointmentId);
}
