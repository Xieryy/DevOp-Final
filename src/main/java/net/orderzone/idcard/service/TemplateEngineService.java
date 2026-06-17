package net.orderzone.idcard.service;

import net.orderzone.idcard.model.Profile;
import net.orderzone.idcard.model.Template;
import net.orderzone.idcard.repository.ProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Compiles Thymeleaf ID-card templates into HTML strings.
 * QR code and barcode images are pre-rendered as Base64 data-URIs and
 * injected into the template context so the HTML output is fully self-contained.
 */
@Service
public class TemplateEngineService {

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private TemplateEngine thymeleafEngine;

    @Autowired
    private QrCodeService qrCodeService;

    @Autowired
    private BarcodeService barcodeService;

    /* Compiles a specific profile details into a complete stylized HTML layout string. */
    public String generateCardHtml(Long profileId) {
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("Profile not found with ID: " + profileId));

        Template template = resolveTemplate(profile.getTemplate());
        return processTemplate(profile, template);
    }

    /* Compiles a transient (unsaved) profile and template structure into a complete HTML preview. */
    public String generatePreviewHtml(Profile profile, Template template) {
        Template resolvedTemplate = resolveTemplate(template);
        return processTemplate(profile, resolvedTemplate);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /** Returns a sensible default template when none is assigned to the profile. */
    private Template resolveTemplate(Template template) {
        if (template != null) return template;
        return Template.builder()
                .organizationName("Institute of Technology of Cambodia")
                .tagline("Science and Technology for Development")
                .primaryColor("#1d4ed8")
                .secondaryColor("#e0e7ff")
                .textColor("#111827")
                .layout("VERTICAL")
                .build();
    }

    /**
     * Builds a Thymeleaf context with profile, template, and pre-rendered
     * QR / barcode Base64 data-URIs, then processes the card template.
     */
    private String processTemplate(Profile profile, Template template) {
        Context context = new Context();
        context.setVariable("profile", profile);
        context.setVariable("template", template);

        // --- QR Code (verification URL, 150×150 px) ---
        String qrBase64 = null;
        if (profile.getUuid() != null && !profile.getUuid().isBlank()) {
            try {
                qrBase64 = qrCodeService.generateVerificationQrBase64(profile, 150, 150);
            } catch (Exception ignored) {
                // gracefully skip QR if UUID is absent / generation fails
            }
        }
        context.setVariable("qrCodeBase64", qrBase64);

        // --- Linear Barcode (Code-128 or EAN-13, 280×60 px) ---
        String barcodeBase64 = null;
        String barcodeLabel = null;
        if (profile.getRegistrationNumber() != null && !profile.getRegistrationNumber().isBlank()) {
            try {
                var barcodeType = profile.getBarcodeType() != null
                        ? profile.getBarcodeType()
                        : net.orderzone.idcard.model.BarcodeType.CODE_128;
                barcodeBase64 = barcodeService.generateBarcodeBase64(
                        profile.getRegistrationNumber(), barcodeType, 280, 60);
                barcodeLabel = profile.getRegistrationNumber();
            } catch (Exception ignored) {
                // gracefully skip barcode on encoding failures
            }
        }
        context.setVariable("barcodeBase64", barcodeBase64);
        context.setVariable("barcodeLabel", barcodeLabel);

        return thymeleafEngine.process("idcard-template", context);
    }
}