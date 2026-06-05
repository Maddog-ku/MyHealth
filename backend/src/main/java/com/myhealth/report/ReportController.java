package com.myhealth.report;

import com.myhealth.ai.AiEndpointRateLimiter;
import com.myhealth.auth.CurrentUser;
import com.myhealth.report.ReportDtos.GenerateReportRequest;
import com.myhealth.report.ReportDtos.WeeklyReportResponse;
import com.myhealth.user.AppUser;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final CurrentUser currentUser;
    private final ReportService reportService;
    private final AiEndpointRateLimiter rateLimiter;

    public ReportController(CurrentUser currentUser, ReportService reportService, AiEndpointRateLimiter rateLimiter) {
        this.currentUser = currentUser;
        this.reportService = reportService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/weekly")
    WeeklyReportResponse weekly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return reportService.get(currentUser.require(), weekStart);
    }

    @PostMapping("/weekly/generate")
    WeeklyReportResponse generate(@RequestBody(required = false) GenerateReportRequest request) {
        AppUser user = currentUser.require();
        rateLimiter.checkReport(user);
        LocalDate weekStart = request == null ? null : request.weekStart();
        return reportService.generate(user, weekStart);
    }
}
