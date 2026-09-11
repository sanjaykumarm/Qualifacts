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
}
