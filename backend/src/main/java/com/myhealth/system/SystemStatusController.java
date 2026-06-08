package com.myhealth.system;

import com.myhealth.auth.CurrentUser;
import com.myhealth.system.SystemDtos.SystemStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemStatusController {
    private final CurrentUser currentUser;
    private final SystemStatusService systemStatusService;

    public SystemStatusController(CurrentUser currentUser, SystemStatusService systemStatusService) {
        this.currentUser = currentUser;
        this.systemStatusService = systemStatusService;
    }

    @GetMapping("/status")
    SystemStatusResponse status() {
        currentUser.require();
        return systemStatusService.status();
    }
}
