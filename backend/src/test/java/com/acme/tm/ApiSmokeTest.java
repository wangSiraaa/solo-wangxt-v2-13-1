package com.acme.tm;

import com.acme.tm.repo.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** REST-level smoke test: batches, review, tasks, checkpoints, versions, lineage, export. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiSmokeTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Autowired UploadChunkRepo chunkRepo;
    @Autowired UploadSessionRepo sessionRepo;
    @Autowired TaskEventRepo eventRepo;
    @Autowired MigrationCommitRepo commitRepo;
    @Autowired MigrationTaskRepo taskRepo;
    @Autowired ReviewDecisionRepo decisionRepo;
    @Autowired BatchCandidateRepo candidateRepo;
    @Autowired ConflictGroupRepo conflictRepo;
    @Autowired ImportBatchRepo batchRepo;
    @Autowired TmEntryRepo entryRepo;
    @Autowired TmVersionRepo versionRepo;
    @Autowired TermMappingRuleRepo ruleRepo;
    @Autowired LanguageProfileRepo profileRepo;

    private static final String TMX = """
            <?xml version="1.0" encoding="UTF-8"?><tmx version="1.4"><body>
            <tu><tuv xml:lang="en"><seg>Open {0} file</seg></tuv><tuv xml:lang="de"><seg>Datei {0} öffnen</seg></tuv></tu>
            <tu><tuv xml:lang="en"><seg>Close window</seg></tuv><tuv xml:lang="de"><seg>Fenster schließen</seg></tuv></tu>
            </body></tmx>""";

    @BeforeEach
    void clean() {
        chunkRepo.deleteAll(); sessionRepo.deleteAll();
        eventRepo.deleteAll(); commitRepo.deleteAll(); taskRepo.deleteAll();
        decisionRepo.deleteAll(); candidateRepo.deleteAll(); conflictRepo.deleteAll(); batchRepo.deleteAll();
        entryRepo.deleteAll(); versionRepo.deleteAll();
        ruleRepo.deleteAll(); profileRepo.deleteAll();
    }

    private long postJson(String url, String body) throws Exception {
        MvcResult r = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8").content(body))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).path("id").asLong();
    }

    @Test
    void endToEndOverRest() throws Exception {
        // config: language profile + term rule
        mvc.perform(post("/api/config/language-profiles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"de\",\"caseRule\":\"LOWER\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/config/term-rules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceLang\":\"en\",\"targetLang\":\"de\",\"productLine\":\"alpha\"," +
                                "\"sourceTerm\":\"server\",\"approvedTarget\":\"server\",\"replacedTarget\":\"host\"}"))
                .andExpect(status().isOk());

        // legacy import -> baseline version
        long v1 = postJson("/api/legacy/import",
                "{\"vendor\":\"V0\",\"sourceLang\":\"en\",\"targetLang\":\"de\",\"productLine\":\"alpha\"," +
                "\"label\":\"baseline\",\"tmx\":\"<?xml version=\\\"1.0\\\"?><tmx version=\\\"1.4\\\"><body>" +
                "<tu><tuv xml:lang=\\\"en\\\"><seg>Hello</seg></tuv>" +
                "<tuv xml:lang=\\\"de\\\"><seg>Hallo</seg></tuv></tu></body></tmx>\"}");

        // batch import (twice -> idempotent)
        String importBody = "{\"vendor\":\"V1\",\"sourceLang\":\"en\",\"targetLang\":\"de\"," +
                "\"productLine\":\"alpha\",\"sourceVersionId\":" + v1 + ",\"tmx\":" +
                om.writeValueAsString(TMX) + "}";
        long b1 = postJson("/api/batches", importBody);
        long b1retry = postJson("/api/batches", importBody);
        assertEquals(b1, b1retry);

        // list candidates
        MvcResult cands = mvc.perform(get("/api/batches/{id}/candidates", b1))
                .andExpect(status().isOk()).andReturn();
        JsonNode list = om.readTree(cands.getResponse().getContentAsString());
        assertEquals(2, list.size());
        long candId = list.get(0).path("id").asLong();
        long version = list.get(0).path("version").asLong();

        // review both candidates
        mvc.perform(post("/api/candidates/{id}/decisions", candId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewer\":\"alice\",\"action\":\"ACCEPT\",\"reason\":\"ok\"," +
                                "\"expectedVersion\":" + version + "}"))
                .andExpect(status().isOk());
        // stale retry -> 409
        mvc.perform(post("/api/candidates/{id}/decisions", candId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewer\":\"bob\",\"action\":\"REJECT\",\"reason\":\"late\"," +
                                "\"expectedVersion\":" + version + "}"))
                .andExpect(status().isConflict());
        long cand2 = list.get(1).path("id").asLong();
        mvc.perform(post("/api/candidates/{id}/decisions", cand2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewer\":\"bob\",\"action\":\"ACCEPT\",\"reason\":\"ok\"," +
                                "\"expectedVersion\":" + list.get(1).path("version").asLong() + "}"))
                .andExpect(status().isOk());

        // history recorded
        mvc.perform(get("/api/candidates/{id}/history", candId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reviewer").value("alice"));

        // task lifecycle: create -> run -> checkpoint visible
        MvcResult taskRes = mvc.perform(post("/api/batches/{batchId}/tasks", b1))
                .andExpect(status().isOk()).andReturn();
        long taskId = om.readTree(taskRes.getResponse().getContentAsString()).path("id").asLong();
        mvc.perform(post("/api/tasks/{id}/run", taskId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chunkSize\":1,\"maxChunks\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.committedCount").value(2));
        mvc.perform(get("/api/tasks/{id}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.checkpointCandidateId").value(cand2))
                .andExpect(jsonPath("$.commits").value(2));

        // publish -> new version with parent, manifest, checksum
        MvcResult pub = mvc.perform(post("/api/versions/publish").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"v2\",\"batchIds\":[" + b1 + "]}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode v2 = om.readTree(pub.getResponse().getContentAsString());
        long v2id = v2.path("id").asLong();
        assertEquals(v1, v2.path("parentId").asLong());
        assertTrue(v2.path("batchManifest").asText().contains("\"vendor\":\"V1\""));
        assertFalse(v2.path("checksum").asText().isBlank());

        // lineage: v2 -> v1
        mvc.perform(get("/api/versions/{id}/lineage", v2id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // export TMX for both versions; old download URL preserved
        mvc.perform(get("/api/versions/{id}/tmx", v1))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Content-Checksum"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hallo")));
        MvcResult v2tmx = mvc.perform(get("/api/versions/{id}/tmx", v2id))
                .andExpect(status().isOk()).andReturn();
        String v2body = new String(v2tmx.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(v2body.contains("Datei {0} öffnen"), v2body);

        // rollback -> reverse version with reason, effective pointer moves
        mvc.perform(post("/api/versions/{id}/rollback", v1).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"regression\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ROLLBACK"))
                .andExpect(jsonPath("$.effective").value(true));
        mvc.perform(get("/api/versions/{id}", v2id))
                .andExpect(jsonPath("$.effective").value(false));
        // history never deleted
        mvc.perform(get("/api/versions")).andExpect(jsonPath("$.length()").value(3));

        // legacy term replacement still works
        mvc.perform(post("/api/legacy/replace").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Der host ist down\",\"sourceLang\":\"en\"," +
                                "\"targetLang\":\"de\",\"productLine\":\"alpha\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("Der server ist down"));
    }

    @Test
    void publishBlockedByAnomaliesOverRest() throws Exception {
        mvc.perform(post("/api/config/language-profiles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"de\",\"caseRule\":\"LOWER\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/config/term-rules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceLang\":\"en\",\"targetLang\":\"de\",\"productLine\":\"alpha\"," +
                                "\"sourceTerm\":\"bank\",\"approvedTarget\":\"Bank\",\"polysemous\":true," +
                                "\"alternatives\":\"Bank;Ufer\"}"))
                .andExpect(status().isOk());
        String tmx = "<?xml version=\"1.0\"?><tmx version=\"1.4\"><body>" +
                "<tu><tuv xml:lang=\"en\"><seg>The bank is closed</seg></tuv>" +
                "<tuv xml:lang=\"de\"><seg>Die Bank ist geschlossen</seg></tuv></tu></body></tmx>";
        long b = postJson("/api/batches", "{\"vendor\":\"V1\",\"sourceLang\":\"en\",\"targetLang\":\"de\"," +
                "\"productLine\":\"alpha\",\"tmx\":" + om.writeValueAsString(tmx) + "}");
        mvc.perform(post("/api/versions/publish").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"v2\",\"batchIds\":[" + b + "]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blockers[0]").value(org.hamcrest.Matchers.containsString("POLYSEMOUS_TERM")));
    }
}
