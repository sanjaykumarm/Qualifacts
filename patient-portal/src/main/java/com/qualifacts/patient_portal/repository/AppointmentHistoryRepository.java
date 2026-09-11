package com.qualifacts.patient_portal.repository;

import com.qualifacts.patient_portal.model.AppointmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppointmentHistoryRepository extends JpaRepository<AppointmentHistory, Long> {
    List<AppointmentHistory> findByAppointmentIdOrderByHistoryIdDesc(Long appointmentId);
    List<AppointmentHistory> findByAppointmentIdOrderByHistoryIdAsc(Long appointmentId);
}
