package com.qualifacts.patient_portal.controller;

import com.qualifacts.patient_portal.model.Appointment;
import com.qualifacts.patient_portal.service.AppointmentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
public class AppointmentController {

    public static final List<String> AVAILABLE_PROVIDERS = List.of(
            "Dr. Alice Smith (General Physician)",
            "Dr. Bob Johnson (Cardiologist)",
            "Dr. Carol Williams (Pediatrician)"
    );

    public static final List<String> APPOINTMENT_TYPES = List.of(
            "General Checkup",
            "Consultation",
            "Follow-up",
            "Specialist Visit"
    );

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/appointments?role=patient";
    }

    @GetMapping("/appointments")
    public String listAppointments(@RequestParam(name = "role", defaultValue = "patient") String role, Model model) {
        // Sanitize role to either "patient" or "provider"
        String activeRole = "provider".equalsIgnoreCase(role) ? "provider" : "patient";

        List<Appointment> appointments = appointmentService.getAllAppointments();

        model.addAttribute("role", activeRole);
        model.addAttribute("appointments", appointments);
        model.addAttribute("providers", AVAILABLE_PROVIDERS);
        model.addAttribute("appointmentTypes", APPOINTMENT_TYPES);

        return "appointments";
    }

    @PostMapping("/appointments/book")
    public String bookAppointment(
            @RequestParam("dateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTime,
            @RequestParam("providerName") String providerName,
            @RequestParam("appointmentType") String appointmentType,
            @RequestParam(value = "reason", required = false) String reason,
            RedirectAttributes redirectAttributes) {
        try {
            Appointment booked = appointmentService.bookAppointment(dateTime, providerName, appointmentType, reason);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Appointment #" + booked.getAppointmentId() + " successfully requested (Status: PENDING).");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/appointments?role=patient";
    }

    @PostMapping("/appointments/{id}/cancel")
    public String cancelAppointment(
            @PathVariable("id") Long id,
            @RequestParam(name = "role", defaultValue = "patient") String role,
            RedirectAttributes redirectAttributes) {
        try {
            appointmentService.cancelAppointment(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Appointment #" + id + " has been cancelled.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/appointments?role=" + role;
    }

    @PostMapping("/appointments/{id}/confirm")
    public String confirmAppointment(
            @PathVariable("id") Long id,
            @RequestParam(name = "role", defaultValue = "provider") String role,
            RedirectAttributes redirectAttributes) {
        try {
            appointmentService.confirmAppointment(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Appointment #" + id + " has been confirmed.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/appointments?role=" + role;
    }

    @PostMapping("/appointments/{id}/reschedule")
    public String rescheduleAppointment(
            @PathVariable("id") Long id,
            @RequestParam("newDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime newDateTime,
            @RequestParam(name = "role", defaultValue = "provider") String role,
            RedirectAttributes redirectAttributes) {
        try {
            Appointment updated = appointmentService.rescheduleAppointment(id, newDateTime);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Appointment #" + id + " rescheduled to " + updated.getDateTime() + " (Status remains " + updated.getStatus() + ").");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/appointments?role=" + role;
    }
}
