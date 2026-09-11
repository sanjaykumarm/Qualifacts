package com.qualifacts.patient_portal.controller;

import com.qualifacts.patient_portal.model.Appointment;
import com.qualifacts.patient_portal.service.AppointmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class AppointmentControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Autowired
    private AppointmentService appointmentService;

    @Test
    void testRootRedirectsToPatientView() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/appointments?role=patient"));
    }

    @Test
    void testGetAppointmentsAsPatient() throws Exception {
        mockMvc.perform(get("/appointments").param("role", "patient"))
                .andExpect(status().isOk())
                .andExpect(view().name("appointments"))
                .andExpect(model().attribute("role", "patient"))
                .andExpect(content().string(containsString("Request New Appointment")))
                .andExpect(content().string(containsString("My Appointments")));
    }

    @Test
    void testGetAppointmentsAsProvider() throws Exception {
        mockMvc.perform(get("/appointments").param("role", "provider"))
                .andExpect(status().isOk())
                .andExpect(view().name("appointments"))
                .andExpect(model().attribute("role", "provider"))
                .andExpect(content().string(containsString("Provider Appointment Management")));
    }

    @Test
    void testBookAppointmentPost() throws Exception {
        mockMvc.perform(post("/appointments/book")
                        .param("dateTime", "2026-09-20T10:30")
                        .param("providerName", "Dr. Alice Smith (General Physician)")
                        .param("appointmentType", "General Checkup")
                        .param("reason", "Routine annual physical"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/appointments?role=patient"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void testConfirmAppointmentPost() throws Exception {
        Appointment appt = appointmentService.bookAppointment(
                LocalDateTime.now().plusDays(2),
                "Dr. Bob Johnson (Cardiologist)",
                "Consultation",
                "Routine"
        );

        mockMvc.perform(post("/appointments/" + appt.getAppointmentId() + "/confirm")
                        .param("version", String.valueOf(appt.getVersion()))
                        .param("role", "provider"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/appointments?role=provider"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void testRescheduleAppointmentPost() throws Exception {
        Appointment appt = appointmentService.bookAppointment(
                LocalDateTime.now().plusDays(2),
                "Dr. Carol Williams (Pediatrician)",
                "Follow-up",
                "Routine"
        );

        mockMvc.perform(post("/appointments/" + appt.getAppointmentId() + "/reschedule")
                        .param("newDateTime", "2026-09-25T14:00")
                        .param("version", String.valueOf(appt.getVersion()))
                        .param("role", "provider"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/appointments?role=provider"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void testCancelAppointmentPost() throws Exception {
        Appointment appt = appointmentService.bookAppointment(
                LocalDateTime.now().plusDays(2),
                "Dr. Alice Smith (General Physician)",
                "Checkup",
                "Routine"
        );
        // First confirm it
        Appointment confirmed = appointmentService.confirmAppointment(appt.getAppointmentId());

        mockMvc.perform(post("/appointments/" + appt.getAppointmentId() + "/cancel")
                        .param("version", String.valueOf(confirmed.getVersion()))
                        .param("role", "patient"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/appointments?role=patient"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void testCancelAppointmentWithStaleVersionPost() throws Exception {
        Appointment appt = appointmentService.bookAppointment(
                LocalDateTime.now().plusDays(2),
                "Dr. Alice Smith (General Physician)",
                "Checkup",
                "Routine"
        );
        Appointment confirmed = appointmentService.confirmAppointment(appt.getAppointmentId());
        Integer staleVersion = confirmed.getVersion();

        // Reschedule increments version
        appointmentService.rescheduleAppointment(appt.getAppointmentId(), LocalDateTime.now().plusDays(3));

        // Submit cancel with stale version
        mockMvc.perform(post("/appointments/" + appt.getAppointmentId() + "/cancel")
                        .param("version", String.valueOf(staleVersion))
                        .param("role", "patient"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/appointments?role=patient"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void testRescheduleWithStaleVersionPost() throws Exception {
        Appointment appt = appointmentService.bookAppointment(
                LocalDateTime.now().plusDays(2),
                "Dr. Bob Johnson (Cardiologist)",
                "Checkup",
                "Routine"
        );
        Appointment confirmed = appointmentService.confirmAppointment(appt.getAppointmentId());
        Integer staleVersion = confirmed.getVersion();

        // Patient cancels concurrently
        appointmentService.cancelAppointment(appt.getAppointmentId(), staleVersion);

        // Provider tries to reschedule with stale version
        mockMvc.perform(post("/appointments/" + appt.getAppointmentId() + "/reschedule")
                        .param("newDateTime", "2026-09-30T10:00")
                        .param("version", String.valueOf(staleVersion))
                        .param("role", "provider"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/appointments?role=provider"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void testConfirmWithStaleVersionPost() throws Exception {
        Appointment appt = appointmentService.bookAppointment(
                LocalDateTime.now().plusDays(2),
                "Dr. Carol Williams (Pediatrician)",
                "Checkup",
                "Routine"
        );
        Integer staleVersion = appt.getVersion();

        // Reschedule changes version
        appointmentService.rescheduleAppointment(appt.getAppointmentId(), LocalDateTime.now().plusDays(4));

        // Provider tries to confirm with stale version
        mockMvc.perform(post("/appointments/" + appt.getAppointmentId() + "/confirm")
                        .param("version", String.valueOf(staleVersion))
                        .param("role", "provider"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/appointments?role=provider"))
                .andExpect(flash().attributeExists("errorMessage"));
    }
}
