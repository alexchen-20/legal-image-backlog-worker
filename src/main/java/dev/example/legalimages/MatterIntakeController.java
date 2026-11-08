package dev.example.legalimages;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class MatterIntakeController {
    private final MatterWorkflow workflow;

    MatterIntakeController(MatterWorkflow workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/matters/intake")
    ResponseEntity<QueuedMatter> intake(@RequestBody MatterImageJob job) {
        return ResponseEntity.accepted().body(workflow.accept(job));
    }

    @PostMapping("/workers/legal-image")
    ProcessedDocument process(@RequestBody MatterImageJob job) {
        return workflow.process(job);
    }

    @ExceptionHandler(InfraiException.class)
    ResponseEntity<Map<String, Object>> rejected(InfraiException error) {
        int clientStatus = error.status() >= 400 && error.status() < 500 ? error.status() : 502;
        return ResponseEntity.status(clientStatus).body(Map.of(
                "code", error.code(),
                "detail", error.detail()));
    }
}
