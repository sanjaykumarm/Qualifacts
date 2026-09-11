package com.qualifacts.patient_portal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appointment_id")
    private Long appointmentId;

    @Version
    @Column(name = "version")
    private Integer version;

    @Column(name = "date_time", nullable = false)
    private LocalDateTime dateTime;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Column(name = "appointment_type", nullable = false)
    private String appointmentType;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AppointmentStatus status = AppointmentStatus.PENDING;

    public Appointment() {
    }

    public Appointment(LocalDateTime dateTime, String providerName, String appointmentType, String reason) {
        this.dateTime = dateTime;
        this.providerName = providerName;
        this.appointmentType = appointmentType;
        this.reason = reason;
        this.status = AppointmentStatus.PENDING;
    }

    public Long getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(Long appointmentId) {
        this.appointmentId = appointmentId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getAppointmentType() {
        return appointmentType;
    }

    public void setAppointmentType(String appointmentType) {
        this.appointmentType = appointmentType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public boolean canPatientCancel() {
        return this.status == AppointmentStatus.CONFIRMED;
    }

    public boolean canProviderConfirm() {
        return this.status == AppointmentStatus.PENDING;
    }

    public boolean canProviderReschedule() {
        return this.status == AppointmentStatus.PENDING || this.status == AppointmentStatus.CONFIRMED;
    }
}
