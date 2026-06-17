package net.orderzone.idcard.controller;

import net.orderzone.idcard.dto.IdCardPreviewRequest;
import net.orderzone.idcard.model.BarcodeType;
import net.orderzone.idcard.model.Profile;
import net.orderzone.idcard.service.BarcodeService;
import net.orderzone.idcard.service.ProfileService;
import net.orderzone.idcard.service.QrCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import net.orderzone.idcard.service.PhotoStorageService;
import net.orderzone.idcard.service.TemplateEngineService;
import net.orderzone.idcard.service.PdfExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import net.orderzone.idcard.service.BatchGenerationService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/profiles")
@CrossOrigin(origins = "*") // Allows communication with your frontend live preview later
public class ProfileController {

    @Autowired
    private ProfileService profileService;

    // 1. POST - Create a profile
    @PostMapping
    public ResponseEntity<Profile> createProfile(@RequestBody Profile profile) {
        Profile savedProfile = profileService.createProfile(profile);
        return new ResponseEntity<>(savedProfile, HttpStatus.CREATED);
    }

    // 2. GET - Retrieve all profiles
    @GetMapping
    public ResponseEntity<List<Profile>> getAllProfiles() {
        return ResponseEntity.ok(profileService.getAllProfiles());
    }

    // 3. GET - Retrieve a single profile by database ID
    @GetMapping("/{id}")
    public ResponseEntity<Profile> getProfileById(@PathVariable Long id) {
        return profileService.getProfileById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 4. GET - Retrieve a profile by stable public UUID (Handy for QR Code scanning)
    @GetMapping("/uuid/{uuid}")
    public ResponseEntity<Profile> getProfileByUuid(@PathVariable String uuid) {
        return profileService.getProfileByUuid(uuid)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 5. PUT - Update an existing profile
    @PutMapping("/{id}")
    public ResponseEntity<Profile> updateProfile(@PathVariable Long id, @RequestBody Profile profileDetails) {
        try {
            Profile updatedProfile = profileService.updateProfile(id, profileDetails);
            return ResponseEntity.ok(updatedProfile);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 6. DELETE - Remove a profile from the system
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProfile(@PathVariable Long id) {
        if (profileService.deleteProfile(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @Autowired
    private PhotoStorageService photoStorageService;

    // 7. POST - Upload and attach a photo to an existing profile
    @PostMapping("/{id}/upload-photo")
    public ResponseEntity<?> uploadPhoto(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            // Retrieve the target profile
            return profileService.getProfileById(id).map(profile -> {
                
                // Validate and save the image through the storage service
                String filename = photoStorageService.storePhoto(file);
                
                // Map the metadata back into your database record fields
                profile.setPhotoFileName(filename);
                profile.setPhotoContentType(file.getContentType());
                
                // Save updated profile state
                profileService.updateProfile(id, profile);
                
                return ResponseEntity.ok().body("Photo uploaded successfully! Filename: " + filename);
            }).orElse(ResponseEntity.notFound().build());

        } catch (IllegalArgumentException e) {
            // Return a clean 400 Bad Request error if validation fails (wrong type/empty file)
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error occurred: " + e.getMessage());
        }
    }

    @Autowired
    private TemplateEngineService templateEngineService;
    
    // 8. GET - View the rendered Thymeleaf template HTML design for an ID Card
    @GetMapping(value = "/{id}/html", produces = "text/html;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<String> renderProfileCardHtml(@PathVariable Long id) {
        try {
            String compiledHtml = templateEngineService.generateCardHtml(id);
            return ResponseEntity.ok(compiledHtml);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 9. POST - Live Preview Engine (Generates instant card HTML without saving to DB)
    @PostMapping(value = "/preview", produces = "text/html;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<String> getLiveCardPreview(@RequestBody IdCardPreviewRequest previewRequest) {
        try {
            if (previewRequest.getProfile() == null) {
                return ResponseEntity.badRequest().body("<h1>Error: Profile details cannot be empty</h1>");
            }
            
            // Compile the dynamic template directly from the incoming request data body
            String compiledHtml = templateEngineService.generatePreviewHtml(
                    previewRequest.getProfile(), 
                    previewRequest.getTemplate()
            );
            
            return ResponseEntity.ok(compiledHtml);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("<h1>Preview Generation Failed:</h1><p>" + e.getMessage() + "</p>");
        }
    }

    @Autowired
    private PdfExportService pdfExportService;
    
    // 10. GET - Download the compiled ID Card as a professional PDF
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadIdCardPdf(@PathVariable Long id) {
        try {
            byte[] pdfContents = pdfExportService.generateIdCardPdf(id);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            
            // This instructs the browser to open or cleanly save the document asset file
            headers.setContentDispositionFormData("attachment", "idcard_profile_" + id + ".pdf");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            
            return new ResponseEntity<>(pdfContents, headers, HttpStatus.OK);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Autowired
    private BatchGenerationService batchGenerationService;

    // 11. POST - Batch Generate ID cards for classes or teams down into a single ZIP file download
    @PostMapping(value = "/batch/zip", produces = "application/zip")
    public ResponseEntity<byte[]> downloadBatchIdCards(@RequestBody List<Long> profileIds) {
        try {
            if (profileIds == null || profileIds.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            byte[] zipContents = batchGenerationService.generateBatchZip(profileIds);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/zip"));
            headers.setContentDispositionFormData("attachment", "batch_id_cards.zip");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return new ResponseEntity<>(zipContents, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // -------------------------------------------------------------------------
    // QR Code endpoints
    // -------------------------------------------------------------------------

    @Autowired
    private QrCodeService qrCodeService;

    /**
     * 12. GET /{id}/qr  – Download the QR code for a profile as a PNG image.
     * The QR payload encodes the public verification URL:
     *   {appBaseUrl}/api/profiles/uuid/{uuid}
     */
    @GetMapping(value = "/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> downloadQrCode(
            @PathVariable Long id,
            @RequestParam(defaultValue = "200") int size) {
        try {
            Profile profile = profileService.getProfileById(id)
                    .orElseThrow(() -> new RuntimeException("Profile not found"));

            byte[] qrBytes = qrCodeService.generateVerificationQrBytes(profile, size, size);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentDispositionFormData("inline", "qr_" + id + ".png");
            return new ResponseEntity<>(qrBytes, headers, HttpStatus.OK);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 13. GET /{id}/qr/base64  – Returns a JSON object with the Base64 data-URI
     * and the verification URL for embedding in frontends.
     * Response: { "dataUri": "data:image/png;base64,...", "verificationUrl": "..." }
     */
    @GetMapping("/{id}/qr/base64")
    public ResponseEntity<Map<String, String>> getQrCodeBase64(
            @PathVariable Long id,
            @RequestParam(defaultValue = "200") int size) {
        try {
            Profile profile = profileService.getProfileById(id)
                    .orElseThrow(() -> new RuntimeException("Profile not found"));

            String dataUri = qrCodeService.generateVerificationQrBase64(profile, size, size);
            String url     = qrCodeService.buildVerificationUrl(profile.getUuid());

            return ResponseEntity.ok(Map.of(
                    "dataUri",         dataUri,
                    "verificationUrl", url
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // -------------------------------------------------------------------------
    // Barcode endpoints
    // -------------------------------------------------------------------------

    @Autowired
    private BarcodeService barcodeService;

    /**
     * 14. GET /{id}/barcode  – Download the linear barcode for a profile as a PNG image.
     * The barcode encodes the registration number using the profile's configured symbology
     * (CODE_128 by default, or EAN_13).
     * Optional query params:  ?width=300&height=80&type=EAN_13
     */
    @GetMapping(value = "/{id}/barcode", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> downloadBarcode(
            @PathVariable Long id,
            @RequestParam(defaultValue = "300") int width,
            @RequestParam(defaultValue = "80")  int height,
            @RequestParam(required = false)     String type) {
        try {
            Profile profile = profileService.getProfileById(id)
                    .orElseThrow(() -> new RuntimeException("Profile not found"));

            // Allow the caller to override the barcode type via query param
            BarcodeType barcodeType;
            if (type != null && !type.isBlank()) {
                barcodeType = BarcodeType.valueOf(type.toUpperCase());
            } else {
                barcodeType = profile.getBarcodeType() != null
                        ? profile.getBarcodeType() : BarcodeType.CODE_128;
            }

            byte[] barBytes = barcodeService.generateBarcodeBytes(
                    profile.getRegistrationNumber(), barcodeType, width, height);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentDispositionFormData("inline", "barcode_" + id + ".png");
            return new ResponseEntity<>(barBytes, headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            // Unknown BarcodeType enum value supplied by caller
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 15. GET /{id}/barcode/base64  – Returns a JSON object with the Base64 data-URI
     * and the raw encoded data string for frontend embedding.
     * Response: { "dataUri": "data:image/png;base64,...", "encodedData": "2026-ENG-001", "symbology": "CODE_128" }
     */
    @GetMapping("/{id}/barcode/base64")
    public ResponseEntity<Map<String, String>> getBarcodeBase64(
            @PathVariable Long id,
            @RequestParam(defaultValue = "300") int width,
            @RequestParam(defaultValue = "80")  int height) {
        try {
            Profile profile = profileService.getProfileById(id)
                    .orElseThrow(() -> new RuntimeException("Profile not found"));

            BarcodeType barcodeType = profile.getBarcodeType() != null
                    ? profile.getBarcodeType() : BarcodeType.CODE_128;

            String dataUri = barcodeService.generateBarcodeBase64(
                    profile.getRegistrationNumber(), barcodeType, width, height);

            return ResponseEntity.ok(Map.of(
                    "dataUri",     dataUri,
                    "encodedData", profile.getRegistrationNumber(),
                    "symbology",   barcodeType.name()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}