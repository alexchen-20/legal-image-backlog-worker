package dev.example.legalimages;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class MatterWorkflowTest {
    @Test
    void imageReferencesSurviveJobJsonRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        for (String image : new String[] {
                "{\"url\":\"https://example.org/evidence.png\"}",
                "{\"base64\":\"aW1hZ2U=\"}",
                "{\"image_id\":\"pim_12345678901234567890\"}"}) {
            String request = "{\"matterId\":\"MAT-1\",\"documentId\":\"DOC-1\",\"image\":"
                    + image + ",\"signedDelivery\":\"client-portal\",\"deadline\":\"2026-09-28T07:59:59Z\"}";
            MatterImageJob job = mapper.readValue(request, MatterImageJob.class);
            assertThat(mapper.valueToTree(job).get("image")).isEqualTo(mapper.readTree(image));
        }
    }

    @Test
    void deadlineInsideFortyEightHoursGetsUrgentFollowUpAndLargerEvidenceImage() {
        Instant now = Instant.parse("2026-09-26T08:00:00Z");

        IntakeDecision decision = MatterWorkflow.decide(
                Instant.parse("2026-09-28T07:59:59Z"), now);

        assertThat(decision.priority()).isEqualTo("urgent");
        assertThat(decision.followUp()).isEqualTo("notify-counsel-now");
        assertThat(decision.width()).isEqualTo(1800);
        assertThat(decision.height()).isEqualTo(2400);
    }
}
