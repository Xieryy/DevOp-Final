package net.orderzone.idcard.repository;

import net.orderzone.idcard.model.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, Long> {
    // Check existence by unique registration number or UUID
    boolean existsByRegistrationNumber(String registrationNumber);
    boolean existsByUuid(String uuid);
    
    // Search profile by registration number or UUID
    Optional<Profile> findByRegistrationNumber(String registrationNumber);
    Optional<Profile> findByUuid(String uuid);
}