package net.orderzone.idcard.controller;

import net.orderzone.idcard.model.Template;
import net.orderzone.idcard.repository.TemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    @Autowired
    private TemplateRepository templateRepository;

    // 1. POST - Create a new brand template theme
    @PostMapping
    public ResponseEntity<Template> createTemplate(@RequestBody Template template) {
        try {
            // Basic validation to check for duplicate codes
            if (templateRepository.findByCode(template.getCode()).isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
            Template savedTemplate = templateRepository.save(template);
            return new ResponseEntity<>(savedTemplate, HttpStatus.CREATED);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 2. GET - Retrieve all available templates
    @GetMapping
    public ResponseEntity<List<Template>> getAllTemplates() {
        List<Template> templates = templateRepository.findAll();
        return ResponseEntity.ok(templates);
    }

    // 3. GET - Retrieve a specific template by its ID
    @GetMapping("/{id}")
    public ResponseEntity<Template> getTemplateById(@PathVariable Long id) {
        return templateRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 4. PUT - Update an existing template configuration
    @PutMapping("/{id}")
    public ResponseEntity<Template> updateTemplate(@PathVariable Long id, @RequestBody Template templateDetails) {
        return templateRepository.findById(id)
                .map(existingTemplate -> {
                    existingTemplate.setName(templateDetails.getName());
                    existingTemplate.setOrganizationName(templateDetails.getOrganizationName());
                    existingTemplate.setTagline(templateDetails.getTagline());
                    existingTemplate.setLayout(templateDetails.getLayout());
                    existingTemplate.setPrimaryColor(templateDetails.getPrimaryColor());
                    existingTemplate.setSecondaryColor(templateDetails.getSecondaryColor());
                    existingTemplate.setTextColor(templateDetails.getTextColor());
                    
                    Template updatedTemplate = templateRepository.save(existingTemplate);
                    return ResponseEntity.ok(updatedTemplate);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 5. DELETE - Remove a template from the system
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        if (!templateRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        try {
            templateRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}