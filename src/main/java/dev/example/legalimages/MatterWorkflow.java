package dev.example.legalimages;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

@Service
final class MatterWorkflow {
    private final InfraiClient infrai;
    private final Clock clock;

    MatterWorkflow(InfraiClient infrai, Clock clock) {
        this.infrai = infrai;
        this.clock = clock;
    }

    QueuedMatter accept(MatterImageJob job) {
        IntakeDecision decision = decide(job.deadline(), clock.instant());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("matterId", job.matterId());
        payload.put("documentId", job.documentId());
        payload.put("image", job.image());
        payload.put("signedDelivery", job.signedDelivery());
        payload.put("deadline", job.deadline().toString());
        payload.put("priority", decision.priority());
        payload.put("followUp", decision.followUp());
        JsonNode queued = infrai.publish(payload, "matter-" + job.matterId() + "-" + job.documentId());
        return new QueuedMatter(job.matterId(), decision.priority(), decision.followUp(), queued);
    }

    ProcessedDocument process(MatterImageJob job) {
        IntakeDecision decision = decide(job.deadline(), clock.instant());
        JsonNode image = infrai.resize(job, decision);
        return new ProcessedDocument(job.matterId(), job.documentId(), job.signedDelivery(),
                decision.priority(), decision.followUp(), image);
    }

    static IntakeDecision decide(Instant deadline, Instant now) {
        Duration remaining = Duration.between(now, deadline);
        if (remaining.compareTo(Duration.ofHours(48)) <= 0) {
            return new IntakeDecision("urgent", "notify-counsel-now", 1800, 2400);
        }
        return new IntakeDecision("standard", "daily-deadline-review", 1200, 1600);
    }
}

record MatterImageJob(String matterId, String documentId, Map<String, String> image,
                      String signedDelivery, Instant deadline) {}
record IntakeDecision(String priority, String followUp, int width, int height) {}
record QueuedMatter(String matterId, String priority, String followUp, JsonNode queue) {}
record ProcessedDocument(String matterId, String documentId, String signedDelivery,
                         String priority, String followUp, JsonNode image) {}
