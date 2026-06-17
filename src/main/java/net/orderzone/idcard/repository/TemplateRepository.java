package net.orderzone.idcard.repository;

import net.orderzone.idcard.model.Template;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TemplateRepository extends JpaRepository<Template, Long> {
    // Check existence by unique code
    boolean existsByCode(String code);
    
    // Search template by code
    Optional<Template> findByCode(String code);
}