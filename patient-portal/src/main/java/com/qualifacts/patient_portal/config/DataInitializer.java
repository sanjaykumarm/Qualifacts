package com.qualifacts.patient_portal.config;

import com.qualifacts.patient_portal.controller.AppointmentController;
import com.qualifacts.patient_portal.model.Provider;
import com.qualifacts.patient_portal.repository.AppointmentRepository;
import com.qualifacts.patient_portal.repository.ProviderRepository;
import com.qualifacts.patient_portal.service.AppointmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AppointmentRepository appointmentRepository;
    private final ProviderRepository providerRepository;
    private final AppointmentService appointmentService;

    public DataInitializer(AppointmentRepository appointmentRepository,
                           ProviderRepository providerRepository,
                           AppointmentService appointmentService) {
        this.appointmentRepository = appointmentRepository;
        this.providerRepository = providerRepository;
        this.appointmentService = appointmentService;
    }

    @Override
    public void run(String... args) {
        // Ensure known providers exist for pessimistic locking
        for (String providerName : AppointmentController.AVAILABLE_PROVIDERS) {
            if (!providerRepository.existsById(providerName)) {
                providerRepository.save(new Provider(providerName));
            }
        }

        // Only seed mock data if database is completely empty
        if (appointmentRepository.count() > 0) {
            return;
        }

        log.info("Seeding initial mock appointments (all in PENDING status)...");

        // 1. General appointments across different providers and different times (all PENDING)
        appointmentService.bookAppointment(
                LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0),
                "Dr. Alice Smith (General Physician)",
                "General Checkup",
                "Annual health assessment and routine physical"
        );

        appointmentService.bookAppointment(
                LocalDateTime.now().plusDays(1).withHour(11).withMinute(30).withSecond(0).withNano(0),
                "Dr. Bob Johnson (Cardiologist)",
                "Consultation",
                "Periodic cardiovascular consultation"
        );

        appointmentService.bookAppointment(
                LocalDateTime.now().plusDays(2).withHour(9).withMinute(0).withSecond(0).withNano(0),
                "Dr. Carol Williams (Pediatrician)",
                "Follow-up",
                "Pediatric growth and wellness check"
        );

        appointmentService.bookAppointment(
                LocalDateTime.now().plusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0),
                "Dr. Alice Smith (General Physician)",
                "Specialist Visit",
                "Persistent skin rash consultation"
        );

        appointmentService.bookAppointment(
                LocalDateTime.now().plusDays(3).withHour(16).withMinute(0).withSecond(0).withNano(0),
                "Dr. Bob Johnson (Cardiologist)",
                "Follow-up",
                "Blood pressure medication evaluation"
        );

        // 2. Overlapping appointment requests for Problem 4 demonstration:
        // Same provider, exact same date/time, all in PENDING status
        LocalDateTime overlapSlot = LocalDateTime.now().plusDays(4).withHour(15).withMinute(0).withSecond(0).withNano(0);
        String overlapProvider = "Dr. Alice Smith (General Physician)";

        appointmentService.bookAppointment(
                overlapSlot,
                overlapProvider,
                "General Checkup",
                "[Problem 4 Demo] Request A: Routine checkup"
        );

        appointmentService.bookAppointment(
                overlapSlot,
                overlapProvider,
                "Consultation",
                "[Problem 4 Demo] Request B: Frequent headache and fatigue"
        );

        appointmentService.bookAppointment(
                overlapSlot,
                overlapProvider,
                "Follow-up",
                "[Problem 4 Demo] Request C: Lab result discussion"
        );

        log.info("Mock appointments successfully seeded (total: {}).", appointmentRepository.count());
    }
}
