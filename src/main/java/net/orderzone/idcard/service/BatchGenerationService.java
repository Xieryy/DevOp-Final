package net.orderzone.idcard.service;

import net.orderzone.idcard.model.Profile;
import net.orderzone.idcard.repository.ProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class BatchGenerationService {

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private PdfExportService pdfExportService;

    /**
     * Generates a single ZIP archive containing individual ID card PDFs for a list of profile IDs.
     */
    public byte[] generateBatchZip(List<Long> profileIds) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        for (Long id : profileIds) {
            try {
                // Fetch profile metadata to name the file cleanly inside the ZIP
                Profile profile = profileRepository.findById(id).orElse(null);
                if (profile == null) {
                    continue; // Skip if the profile ID doesn't exist
                }

                // 1. Generate the individual PDF binary array
                byte[] pdfBytes = pdfExportService.generateIdCardPdf(id);

                // 2. Format a clean filename (e.g., "2026-ITC-001_Im_Chheangngim.pdf")
                String cleanName = profile.getFullName().replaceAll("\\s+", "_");
                String filename = profile.getRegistrationNumber() + "_" + cleanName + ".pdf";

                // 3. Create a entry slot inside the ZIP container file
                ZipEntry entry = new ZipEntry(filename);
                zos.putNextEntry(entry);
                zos.write(pdfBytes);
                zos.closeEntry();

            } catch (Exception e) {
                // Log exception or handle individually so one bad profile image/data doesn't crash the whole batch
                System.err.println("Failed to generate batch item for Profile ID " + id + ": " + e.getMessage());
            }
        }

        zos.finish();
        zos.close();
        return baos.toByteArray();
    }
}