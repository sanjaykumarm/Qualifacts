package com.qualifacts.patient_portal.controller;

import com.qualifacts.patient_portal.model.Appointment;
import com.qualifacts.patient_portal.model.AppointmentHistory;
import com.qualifacts.patient_portal.service.AppointmentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @GetMapping("/appointments/{id}/history")
    @ResponseBody
    public List<Map<String, Object>> getAppointmentHistory(@PathVariable("id") Long id) {
        List<AppointmentHistory> historyList = appointmentService.getAppointmentHistory(id);
        DateTimeFormatter dtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        DateTimeFormatter tsFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return historyList.stream().map(h -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("historyId", h.getHistoryId());
            map.put("appointmentId", h.getAppointmentId());
            map.put("action", h.getAction().name());
            map.put("actorRole", h.getActorRole());
            map.put("previousDateTime", h.getPreviousDateTime() != null ? h.getPreviousDateTime().format(dtFormatter) : null);
            map.put("newDateTime", h.getNewDateTime() != null ? h.getNewDateTime().format(dtFormatter) : null);
            map.put("previousStatus", h.getPreviousStatus() != null ? h.getPreviousStatus().name() : null);
            map.put("newStatus", h.getNewStatus() != null ? h.getNewStatus().name() : null);
            map.put("details", h.getDetails());
            map.put("createdAt", h.getCreatedAt() != null ? h.getCreatedAt().format(tsFormatter) : null);
            return map;
        }).toList();
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
            @RequestParam("version") Integer version,
            @RequestParam(name = "role", defaultValue = "patient") String role,
            RedirectAttributes redirectAttributes) {
        try {
            appointmentService.cancelAppointment(id, version);
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
            @RequestParam("version") Integer version,
            @RequestParam(name = "role", defaultValue = "provider") String role,
            RedirectAttributes redirectAttributes) {
        try {
            appointmentService.confirmAppointment(id, version);
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
            @RequestParam("version") Integer version,
            @RequestParam(name = "role", defaultValue = "provider") String role,
            RedirectAttributes redirectAttributes) {
        try {
            Appointment updated = appointmentService.rescheduleAppointment(id, newDateTime, version);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Appointment #" + id + " rescheduled to " + updated.getDateTime() + " (Status remains " + updated.getStatus() + ").");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/appointments?role=" + role;
    }
}
