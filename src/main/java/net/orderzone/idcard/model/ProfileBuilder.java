package net.orderzone.idcard.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Utility builder to construct pre-configured default profiles based on Type.
 * This satisfies the assignment requirement while leveraging Lombok.
 */
public class ProfileBuilder {

    /**
     * Builds a default Student profile with pre-filled baseline data.
     */
    public static Profile createDefaultStudent() {
        return Profile.builder()
                .uuid(UUID.randomUUID().toString()) // Public stable identifier
                .registrationNumber("TEMP-STU-" + System.currentTimeMillis() % 1000)
                .type(ProfileType.STUDENT)
                .fullName("New Student Registration")
                .issueDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusYears(4)) // Default 4-year academic duration
                .barcodeType(BarcodeType.CODE_128)        // Standard default barcode
                .build();
    }

    /**
     * Builds a default Employee profile with pre-filled baseline data.
     */
    public static Profile createDefaultEmployee() {
        return Profile.builder()
                .uuid(UUID.randomUUID().toString())
                .registrationNumber("TEMP-EMP-" + System.currentTimeMillis() % 1000)
                .type(ProfileType.EMPLOYEE)
                .fullName("New Employee Record")
                .issueDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusYears(2)) // Default 2-year contract duration
                .barcodeType(BarcodeType.CODE_128)
                .build();
    }
}