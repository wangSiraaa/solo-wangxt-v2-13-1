package com.tmhub;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmhub.domain.DecisionAction;
import com.tmhub.domain.TmVersion;
import com.tmhub.service.Hashes;
import com.tmhub.service.PublishService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end REST smoke test: batch intake, review 409 semantics, task and version queries. */
@AutoConfigureMockMvc
class ApiSmokeTest extends BaseAcceptanceTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PublishService publishService;

    @Test
    void restFlow_initiateShardDecideTaskVersion() throws Exception {
        TmVersion baseline = publishService.createBaseline("v0", "tester");
        String tmx = tmx("en", "de", new String[][]{{"Hello {0}", "Hallo {0}"}});
        String fingerprint = Hashes.sha256(tmx);

        String initiateBody = """
                {"sourceVersionId":%d,"sourceLang":"en","targetLang":"de","productLine":"mail",
                 "vendor":"vendor-a","tmxFingerprint":"%s","expectedShards":1}
                """.formatted(baseline.getId(), fingerprint);

        MvcResult first = mvc.perform(post("/api/batches")
                        .contentType(MediaType.APPLICATION_JSON).content(initiateBody))
                .andExpect(status().isOk()).andReturn();
        long batchId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asLong();

        // Retry with the same delivery metadata returns the same batch (no duplicate).
        MvcResult retry = mvc.perform(post("/api/batches")
                        .contentType(MediaType.APPLICATION_JSON).content(initiateBody))
                .andExpect(status().isOk()).andReturn();
        assert objectMapper.readTree(retry.getResponse().getContentAsString()).get("id").asLong() == batchId;

        mvc.perform(post("/api/batches/{id}/shards/0", batchId)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(tmx.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));

        MvcResult candidates = mvc.perform(get("/api/batches/{id}/candidates", batchId))
                .andExpect(status().isOk()).andReturn();
        JsonNode list = objectMapper.readTree(candidates.getResponse().getContentAsString());
        long candidateId = list.get(0).get("id").asLong();

        String decision = """
                {"reviewer":"alice","action":"ACCEPT","rationale":"ok"}
                """;
        mvc.perform(post("/api/candidates/{id}/decisions", candidateId)
                        .contentType(MediaType.APPLICATION_JSON).content(decision))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // A second decision on the same candidate is a version conflict, not a double review.
        mvc.perform(post("/api/candidates/{id}/decisions", candidateId)
                        .contentType(MediaType.APPLICATION_JSON).content(decision))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("version_conflict"));

        MvcResult taskResult = mvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchId\":" + batchId + ",\"name\":\"t\"}"))
                .andExpect(status().isOk()).andReturn();
        long taskId = objectMapper.readTree(taskResult.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/tasks/{id}/start", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mvc.perform(post("/api/versions/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"rel\",\"batchIds\":[" + batchId + "],\"actor\":\"pm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("INCREMENTAL"));

        mvc.perform(get("/api/versions")).andExpect(status().isOk());
        mvc.perform(get("/api/tasks/{id}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committedCount").value(1));
    }
}
