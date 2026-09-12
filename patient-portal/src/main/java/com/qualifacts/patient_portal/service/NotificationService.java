package com.qualifacts.patient_portal.service;

import com.qualifacts.patient_portal.model.Appointment;

public interface NotificationService {

    /**
     * Notify the patient that their appointment has been confirmed by the provider.
     *
     * @param appointment the confirmed appointment details
     */
    void notifyAppointmentConfirmed(Appointment appointment);
}
