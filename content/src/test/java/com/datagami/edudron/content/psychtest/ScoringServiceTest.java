package com.datagami.edudron.content.psychtest;

import com.datagami.edudron.content.psychtest.domain.PsychTestAnswer;
import com.datagami.edudron.content.psychtest.domain.PsychTestOption;
import com.datagami.edudron.content.psychtest.domain.PsychTestQuestion;
import com.datagami.edudron.content.psychtest.domain.PsychTestSession;
import com.datagami.edudron.content.psychtest.repo.PsychTestOptionRepository;
import com.datagami.edudron.content.psychtest.service.ScoringService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class ScoringServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void computeSnapshot_shouldNormalizeLikertTo0To100() {
        PsychTestOptionRepository optionRepo = Mockito.mock(PsychTestOptionRepository.class);
        ScoringService scoringService = new ScoringService(optionRepo);

        PsychTestSession session = new PsychTestSession();
        session.setId("S1");
        session.setBankVersion("v1");

        PsychTestQuestion q = new PsychTestQuestion();
        q.setId("Q_R");
        q.setType(PsychTestQuestion.Type.LIKERT);
        q.setDomainTags(List.of("R"));
        q.setBankVersion("v1");
        q.setReverseScored(false);
        q.setWeight(1.0);

        PsychTestOption opt = new PsychTestOption();
        opt.setId("O1");
        opt.setValue(2);

        PsychTestAnswer a = new PsychTestAnswer();
        a.setQuestion(q);
        ObjectNode answerJson = objectMapper.createObjectNode();
        answerJson.put("selectedOptionId", "O1");
        a.setAnswerJson(answerJson);

        Mockito.when(optionRepo.findAllById(Mockito.anyIterable())).thenReturn(List.of(opt));

        ScoringService.ScoringSnapshot snap = scoringService.computeSnapshot(session, List.of(a));
        assertNotNull(snap);
        assertTrue(snap.domains().containsKey("R"));
        assertEquals(100.0, snap.domains().get("R").score0To100(), 0.01);
        assertNotNull(snap.overallConfidenceLevel());
        assertFalse(snap.topDomains().isEmpty());
    }
}

