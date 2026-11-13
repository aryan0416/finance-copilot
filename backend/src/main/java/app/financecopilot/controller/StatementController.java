package app.financecopilot.controller;

import app.financecopilot.dto.StatementDtos.ImportSummary;
import app.financecopilot.dto.StatementDtos.StatementResponse;
import app.financecopilot.security.AuthenticatedUser;
import app.financecopilot.service.StatementImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/statements")
@Tag(name = "Statements")
public class StatementController {

    private final StatementImportService importService;

    public StatementController(StatementImportService importService) {
        this.importService = importService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a CSV or text PDF statement; returns an import summary")
    public ImportSummary upload(@AuthenticationPrincipal AuthenticatedUser user,
                                @RequestPart("file") MultipartFile file) throws IOException {
        return importService.importStatement(user.id(), file.getOriginalFilename(), file.getBytes());
    }

    @GetMapping
    @Operation(summary = "Upload history")
    public List<StatementResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return importService.list(user.id());
    }
}
