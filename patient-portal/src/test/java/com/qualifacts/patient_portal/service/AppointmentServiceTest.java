package com.qualifacts.patient_portal.service;

import com.qualifacts.patient_portal.model.Appointment;
import com.qualifacts.patient_portal.model.AppointmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class AppointmentServiceTest {

    @Autowired
    private AppointmentService appointmentService;

    @Test
    void testBookAppointmentStartsInPendingStatus() {
        LocalDateTime time = LocalDateTime.now().plusDays(1);
        Appointment appt = appointmentService.bookAppointment(
                time,
                "Dr. Alice Smith (General Physician)",
                "General Checkup",
                "Annual health assessment"
        );

        assertNotNull(appt.getAppointmentId());
        assertEquals(AppointmentStatus.PENDING, appt.getStatus());
        assertEquals("Dr. Alice Smith (General Physician)", appt.getProviderName());
        assertEquals("General Checkup", appt.getAppointmentType());
        assertEquals("Annual health assessment", appt.getReason());

        List<Appointment> all = appointmentService.getAllAppointments();
        assertTrue(all.stream().anyMatch(a -> a.getAppointmentId().equals(appt.getAppointmentId())));
    }

    @Test
    void testPatientCannotCancelPendingAppointment() {
        LocalDateTime time = LocalDateTime.now().plusDays(1);
        Appointment appt = appointmentService.bookAppointment(
                time,
                "Dr. Bob Johnson (Cardiologist)",
                "Consultation",
                "Heart palpitation"
        );
        assertEquals(AppointmentStatus.PENDING, appt.getStatus());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                appointmentService.cancelAppointment(appt.getAppointmentId())
        );
        assertTrue(ex.getMessage().contains("Only confirmed appointments can be cancelled"));
    }

    @Test
    void testProviderCanConfirmPendingAppointment() {
        LocalDateTime time = LocalDateTime.now().plusDays(1);
        Appointment appt = appointmentService.bookAppointment(
                time,
                "Dr. Carol Williams (Pediatrician)",
                "Checkup",
                "Child checkup"
        );

        Appointment confirmed = appointmentService.confirmAppointment(appt.getAppointmentId());
        assertEquals(AppointmentStatus.CONFIRMED, confirmed.getStatus());
    }

    @Test
    void testConfirmedAppointmentCannotBeConfirmedAgain() {
        LocalDateTime time = LocalDateTime.now().plusDays(1);
        Appointment appt = appointmentService.bookAppointment(
                time,
                "Dr. Carol Williams (Pediatrician)",
                "Checkup",
                "Child checkup"
        );

        appointmentService.confirmAppointment(appt.getAppointmentId());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                appointmentService.confirmAppointment(appt.getAppointmentId())
        );
        assertTrue(ex.getMessage().contains("already confirmed"));
    }

    @Test
    void testPatientCanCancelConfirmedAppointment() {
        LocalDateTime time = LocalDateTime.now().plusDays(1);
        Appointment appt = appointmentService.bookAppointment(
                time,
                "Dr. Alice Smith (General Physician)",
                "Consultation",
                "Flu symptoms"
        );

        appointmentService.confirmAppointment(appt.getAppointmentId());
        Appointment cancelled = appointmentService.cancelAppointment(appt.getAppointmentId());

        assertEquals(AppointmentStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void testCancelledAppointmentCannotBeConfirmed() {
        LocalDateTime time = LocalDateTime.now().plusDays(1);
        Appointment appt = appointmentService.bookAppointment(
                time,
                "Dr. Alice Smith (General Physician)",
                "Consultation",
                "Flu symptoms"
        );

        appointmentService.confirmAppointment(appt.getAppointmentId());
        appointmentService.cancelAppointment(appt.getAppointmentId());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                appointmentService.confirmAppointment(appt.getAppointmentId())
        );
        assertTrue(ex.getMessage().contains("Cancelled appointments cannot be confirmed"));
    }

    @Test
    void testCancelledAppointmentCannotBeRescheduled() {
        LocalDateTime time = LocalDateTime.now().plusDays(1);
        Appointment appt = appointmentService.bookAppointment(
                time,
                "Dr. Alice Smith (General Physician)",
                "Consultation",
                "Flu symptoms"
        );

        appointmentService.confirmAppointment(appt.getAppointmentId());
        appointmentService.cancelAppointment(appt.getAppointmentId());

        LocalDateTime newTime = LocalDateTime.now().plusDays(3);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                appointmentService.rescheduleAppointment(appt.getAppointmentId(), newTime)
        );
        assertTrue(ex.getMessage().contains("Cancelled appointments cannot be rescheduled"));
    }

    @Test
    void testProviderReschedulePendingAppointmentRemainsPending() {
        LocalDateTime time = LocalDateTime.now().plusDays(1);
        Appointment appt = appointmentService.bookAppointment(
                time,
                "Dr. Bob Johnson (Cardiologist)",
                "Specialist Visit",
                "Chest discomfort"
        );

        LocalDateTime newTime = LocalDateTime.now().plusDays(5);
        Appointment rescheduled = appointmentService.rescheduleAppointment(appt.getAppointmentId(), newTime);

        assertEquals(newTime, rescheduled.getDateTime());
        assertEquals(AppointmentStatus.PENDING, rescheduled.getStatus());
    }

    @Test
    void testProviderRescheduleConfirmedAppointmentRemainsConfirmed() {
        LocalDateTime time = LocalDateTime.now().plusDays(1);
        Appointment appt = appointmentService.bookAppointment(
                time,
                "Dr. Bob Johnson (Cardiologist)",
                "Specialist Visit",
                "Chest discomfort"
        );

        appointmentService.confirmAppointment(appt.getAppointmentId());

        LocalDateTime newTime = LocalDateTime.now().plusDays(7);
        Appointment rescheduled = appointmentService.rescheduleAppointment(appt.getAppointmentId(), newTime);

        assertEquals(newTime, rescheduled.getDateTime());
        assertEquals(AppointmentStatus.CONFIRMED, rescheduled.getStatus());
    }

    @Test
    void testCancelAppointmentWithStaleVersionThrowsException() {
        LocalDateTime time = LocalDateTime.now().plusDays(1);
        Appointment appt = appointmentService.bookAppointment(
                time,
                "Dr. Bob Johnson (Cardiologist)",
                "Consultation",
                "Heart palpitations"
        );

        Appointment confirmed = appointmentService.confirmAppointment(appt.getAppointmentId());
        Integer staleVersion = confirmed.getVersion();

        // Provider reschedules to a new time, which increments version
        LocalDateTime newTime = LocalDateTime.now().plusDays(2);
        Appointment rescheduled = appointmentService.rescheduleAppointment(appt.getAppointmentId(), newTime);

        assertNotEquals(staleVersion, rescheduled.getVersion());

        // Patient attempts to cancel using the stale version
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                appointmentService.cancelAppointment(appt.getAppointmentId(), staleVersion)
        );
        assertTrue(ex.getMessage().contains("modified by another user"));

        // Patient cancels with the up-to-date version
        Appointment cancelled = appointmentService.cancelAppointment(appt.getAppointmentId(), rescheduled.getVersion());
        assertEquals(AppointmentStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void testRescheduleWithStaleVersionThrowsExceptionWhenPatientCancelledConcurrently() {
        LocalDateTime time = LocalDateTime.now().plusDays(1);
        Appointment appt = appointmentService.bookAppointment(
                time,
                "Dr. Alice Smith (General Physician)",
                "Checkup",
                "Routine checkup"
        );
        Appointment confirmed = appointmentService.confirmAppointment(appt.getAppointmentId());
        Integer staleVersion = confirmed.getVersion();

        // Concurrently, patient cancels the confirmed appointment -> version increments to 2
        Appointment cancelled = appointmentService.cancelAppointment(appt.getAppointmentId(), staleVersion);
        assertNotEquals(staleVersion, cancelled.getVersion());

        // Provider tries to reschedule based on the stale version (e.g. version 1)
        LocalDateTime newTime = LocalDateTime.now().plusDays(4);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                appointmentService.rescheduleAppointment(appt.getAppointmentId(), newTime, staleVersion)
        );
        assertTrue(ex.getMessage().contains("modified by another user"));
    }

    @Test
    void testConfirmWithStaleVersionThrowsException() {
        LocalDateTime time = LocalDateTime.now().plusDays(1);
        Appointment appt = appointmentService.bookAppointment(
                time,
                "Dr. Carol Williams (Pediatrician)",
                "Consultation",
                "Child rash"
        );
        Integer initialVersion = appt.getVersion();

        // Concurrently, provider reschedules pending appointment -> version increments
        appointmentService.rescheduleAppointment(appt.getAppointmentId(), LocalDateTime.now().plusDays(2));

        // Provider attempts to confirm using stale version
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                appointmentService.confirmAppointment(appt.getAppointmentId(), initialVersion)
        );
        assertTrue(ex.getMessage().contains("modified by another user"));
    }

    @Test
    void testMissingVersionThrowsIllegalArgumentException() {
        LocalDateTime time = LocalDateTime.now().plusDays(1);
        Appointment appt = appointmentService.bookAppointment(
                time,
                "Dr. Bob Johnson (Cardiologist)",
                "Checkup",
                "Routine"
        );

        assertThrows(IllegalArgumentException.class, () ->
                appointmentService.cancelAppointment(appt.getAppointmentId(), null)
        );
        assertThrows(IllegalArgumentException.class, () ->
                appointmentService.confirmAppointment(appt.getAppointmentId(), null)
        );
        assertThrows(IllegalArgumentException.class, () ->
                appointmentService.rescheduleAppointment(appt.getAppointmentId(), LocalDateTime.now().plusDays(3), null)
        );
    }
}
