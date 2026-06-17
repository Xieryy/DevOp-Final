package net.orderzone.idcard.service;

import net.orderzone.idcard.model.Profile;
import net.orderzone.idcard.repository.ProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProfileService {

    @Autowired
    private ProfileRepository profileRepository;

    /**
     * CREATE a new Profile with auto-generated UUID and Registration Number
     */
    public Profile createProfile(Profile profile) {
        // 1. Generate stable public UUID
        profile.setUuid(UUID.randomUUID().toString());

        // 2. Generate Custom Registration Number sequence: YEAR-DEPT-###
        String dept = (profile.getDepartment() != null && !profile.getDepartment().isBlank()) 
                ? profile.getDepartment().toUpperCase() 
                : "GEN";
        int currentYear = LocalDate.now().getYear();
        
        // Count existing records to make a simple incrementing sequence number
        long count = profileRepository.count() + 1;
        String customRegNumber = String.format("%d-%s-%03d", currentYear, dept, count);
        
        profile.setRegistrationNumber(customRegNumber);

        return profileRepository.save(profile);
    }

    /**
     * READ all Profiles
     */
    public List<Profile> getAllProfiles() {
        return profileRepository.findAll();
    }

    /**
     * READ a single Profile by ID
     */
    public Optional<Profile> getProfileById(Long id) {
        return profileRepository.findById(id);
    }

    /**
     * READ a single Profile by public UUID (Useful for QR code verification later)
     */
    public Optional<Profile> getProfileByUuid(String uuid) {
        return profileRepository.findByUuid(uuid);
    }

    /**
     * UPDATE an existing Profile
     */
    public Profile updateProfile(Long id, Profile updatedDetails) {
        return profileRepository.findById(id).map(existingProfile -> {
            existingProfile.setFullName(updatedDetails.getFullName());
            existingProfile.setDepartment(updatedDetails.getDepartment());
            existingProfile.setTitle(updatedDetails.getTitle());
            existingProfile.setEmail(updatedDetails.getEmail());
            existingProfile.setPhone(updatedDetails.getPhone());
            existingProfile.setDateOfBirth(updatedDetails.getDateOfBirth());
            existingProfile.setExpiryDate(updatedDetails.getExpiryDate());
            existingProfile.setType(updatedDetails.getType());
            existingProfile.setBarcodeType(updatedDetails.getBarcodeType());
            existingProfile.setTemplate(updatedDetails.getTemplate());
            // Keep original UUID, Registration Number, and CreatedAt intact
            return profileRepository.save(existingProfile);
        }).orElseThrow(() -> new RuntimeException("Profile not found with id: " + id));
    }

    /**
     * DELETE a Profile by ID
     */
    public boolean deleteProfile(Long id) {
        return profileRepository.findById(id).map(profile -> {
            profileRepository.delete(profile);
            return true;
        }).orElse(false);
    }
}