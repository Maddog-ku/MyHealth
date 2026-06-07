package com.myhealth.export;

import com.myhealth.auth.CurrentUser;
import com.myhealth.export.ExportDtos.ExportFile;
import java.time.LocalDate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/export")
public class ExportController {
    private final CurrentUser currentUser;
    private final DataExportService exportService;

    public ExportController(CurrentUser currentUser, DataExportService exportService) {
        this.currentUser = currentUser;
        this.exportService = exportService;
    }

    @GetMapping
    ResponseEntity<ExportFile> export() {
        ExportFile file = exportService.export(currentUser.require());
        String filename = "myhealth-export-%s.json".formatted(LocalDate.now());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"%s\"".formatted(filename))
                .contentType(MediaType.APPLICATION_JSON)
                .body(file);
    }
}
