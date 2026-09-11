package com.qualifacts.patient_portal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "appointment_history")
public class AppointmentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private AppointmentHistoryAction action;

    @Column(name = "actor_role", nullable = false)
    private String actorRole;

    @Column(name = "previous_date_time")
    private LocalDateTime previousDateTime;

    @Column(name = "new_date_time", nullable = false)
    private LocalDateTime newDateTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private AppointmentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private AppointmentStatus newStatus;

    @Column(name = "details", length = 500, nullable = false)
    private String details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public AppointmentHistory() {
    }

    public AppointmentHistory(Long appointmentId,
                              AppointmentHistoryAction action,
                              String actorRole,
                              LocalDateTime previousDateTime,
                              LocalDateTime newDateTime,
                              AppointmentStatus previousStatus,
                              AppointmentStatus newStatus,
                              String details) {
        this.appointmentId = appointmentId;
        this.action = action;
        this.actorRole = actorRole;
        this.previousDateTime = previousDateTime;
        this.newDateTime = newDateTime;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.details = details;
        this.createdAt = LocalDateTime.now();
    }

    public Long getHistoryId() {
        return historyId;
    }

    public void setHistoryId(Long historyId) {
        this.historyId = historyId;
    }

    public Long getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(Long appointmentId) {
        this.appointmentId = appointmentId;
    }

    public AppointmentHistoryAction getAction() {
        return action;
    }

    public void setAction(AppointmentHistoryAction action) {
        this.action = action;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public LocalDateTime getPreviousDateTime() {
        return previousDateTime;
    }

    public void setPreviousDateTime(LocalDateTime previousDateTime) {
        this.previousDateTime = previousDateTime;
    }

    public LocalDateTime getNewDateTime() {
        return newDateTime;
    }

    public void setNewDateTime(LocalDateTime newDateTime) {
        this.newDateTime = newDateTime;
    }

    public AppointmentStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(AppointmentStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public AppointmentStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(AppointmentStatus newStatus) {
        this.newStatus = newStatus;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
