package com.datagami.edudron.content.psychtest;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.content.domain.Course;
import com.datagami.edudron.content.psychtest.ai.PsychTestAiService;
import com.datagami.edudron.content.psychtest.domain.PsychTestAnswer;
import com.datagami.edudron.content.psychtest.domain.PsychTestOption;
import com.datagami.edudron.content.psychtest.domain.PsychTestQuestion;
import com.datagami.edudron.content.psychtest.domain.PsychTestResult;
import com.datagami.edudron.content.psychtest.domain.PsychTestSession;
import com.datagami.edudron.content.psychtest.repo.PsychTestAnswerRepository;
import com.datagami.edudron.content.psychtest.repo.PsychTestOptionRepository;
import com.datagami.edudron.content.psychtest.repo.PsychTestQuestionRepository;
import com.datagami.edudron.content.psychtest.repo.PsychTestResultRepository;
import com.datagami.edudron.content.psychtest.repo.PsychTestSessionRepository;
import com.datagami.edudron.content.psychtest.service.AdaptiveQuestionSelector;
import com.datagami.edudron.content.psychtest.service.MappingService;
import com.datagami.edudron.content.psychtest.service.PsychTestVersions;
import com.datagami.edudron.content.psychtest.service.ResultExplanationService;
import com.datagami.edudron.content.psychtest.service.RecommendationService;
import com.datagami.edudron.content.psychtest.service.ReportService;
import com.datagami.edudron.content.psychtest.service.ScoringService;
import com.datagami.edudron.content.psychtest.service.SessionService;
import com.datagami.edudron.content.repo.CourseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class SessionFlowIntegrationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID clientId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        TenantContext.setClientId(clientId.toString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void sessionFlow_start_answer_next_complete() {
        // In-memory mutable storage for answers
        List<PsychTestAnswer> storedAnswers = new ArrayList<>();

        PsychTestSessionRepository sessionRepo = Mockito.mock(PsychTestSessionRepository.class);
        PsychTestQuestionRepository questionRepo = Mockito.mock(PsychTestQuestionRepository.class);
        PsychTestOptionRepository optionRepo = Mockito.mock(PsychTestOptionRepository.class);
        PsychTestAnswerRepository answerRepo = Mockito.mock(PsychTestAnswerRepository.class);
        PsychTestResultRepository resultRepo = Mockito.mock(PsychTestResultRepository.class);
        CourseRepository courseRepo = Mockito.mock(CourseRepository.class);

        // Minimal question bank: one LIKERT in A domain
        PsychTestQuestion qA = new PsychTestQuestion();
        qA.setId("Q_A_1");
        qA.setType(PsychTestQuestion.Type.LIKERT);
        qA.setPrompt("I enjoy creative activities.");
        qA.setDomainTags(List.of("A"));
        qA.setReverseScored(false);
        qA.setWeight(1.0);
        qA.setIsActive(true);
        qA.setBankVersion(PsychTestVersions.BANK_VERSION);

        PsychTestOption o1 = new PsychTestOption();
        o1.setId("O1");
        o1.setLabel("Strongly Agree");
        o1.setValue(2);

        Mockito.when(questionRepo.findActiveByBankVersion(PsychTestVersions.BANK_VERSION)).thenReturn(List.of(qA));
        Mockito.when(questionRepo.findById("Q_A_1")).thenReturn(Optional.of(qA));
        Mockito.when(optionRepo.findByQuestionId("Q_A_1")).thenReturn(List.of(o1));
        Mockito.when(optionRepo.findAllById(Mockito.any())).thenAnswer(inv -> List.of(o1));

        Mockito.when(answerRepo.findBySessionIdOrdered(Mockito.anyString())).thenAnswer(inv -> storedAnswers);
        Mockito.when(answerRepo.save(Mockito.any())).thenAnswer(inv -> {
            PsychTestAnswer a = inv.getArgument(0);
            storedAnswers.add(a);
            return a;
        });

        PsychTestSession session = new PsychTestSession();
        session.setId("S1");
        session.setClientId(clientId);
        session.setUserId("USER_ULID_000000000000000000"); // 26-ish placeholder
        session.setStatus(PsychTestSession.Status.IN_PROGRESS);
        session.setTestVersion(PsychTestVersions.TEST_VERSION);
        session.setBankVersion(PsychTestVersions.BANK_VERSION);
        session.setScoringVersion(PsychTestVersions.SCORING_VERSION);
        session.setPromptVersion(PsychTestVersions.PROMPT_VERSION);
        session.setMaxQuestions(30);

        Mockito.when(sessionRepo.findFirstByClientIdAndUserIdAndStatusOrderByCreatedAtDesc(clientId, session.getUserId(), PsychTestSession.Status.IN_PROGRESS))
            .thenReturn(Optional.empty());
        Mockito.when(sessionRepo.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(sessionRepo.findByIdAndClientId("S1", clientId)).thenReturn(Optional.of(session));

        // Recommendation: return one course
        Course c = new Course();
        c.setId("COURSE1");
        c.setTitle("Creative Design Basics");
        c.setDescription("Learn design basics.");
        Mockito.when(courseRepo.findCuratedByStreamAndRiasec(Mockito.eq(clientId), Mockito.any(), Mockito.any(), Mockito.anyInt()))
            .thenReturn(List.of(c));
        Mockito.when(courseRepo.findCuratedBySkill(Mockito.eq(clientId), Mockito.any(), Mockito.anyInt()))
            .thenReturn(List.of(c));

        RecommendationService recommendationService = new RecommendationService(courseRepo);
        ScoringService scoringService = new ScoringService(optionRepo);
        AdaptiveQuestionSelector selector = new AdaptiveQuestionSelector();
        MappingService mappingService = new MappingService();
        ReportService reportService = new ReportService(objectMapper);
        ResultExplanationService resultExplanationService = new ResultExplanationService(answerRepo, optionRepo, scoringService, mappingService);

        PsychTestAiService ai = Mockito.mock(PsychTestAiService.class);
        Mockito.when(ai.isConfigured()).thenReturn(false);

        SessionService svc = new SessionService(
            sessionRepo,
            questionRepo,
            optionRepo,
            answerRepo,
            resultRepo,
            scoringService,
            selector,
            mappingService,
            recommendationService,
            reportService,
            ai,
            objectMapper,
            resultExplanationService
        );

        PsychTestSession started = svc.startOrResume(session.getUserId(), 10, "en", 30);
        assertNotNull(started);

        // Next question
        SessionService.NextQuestion next = svc.getNextQuestion("S1", session.getUserId());
        assertNotNull(next);
        assertNotNull(next.questionId());

        // Answer
        svc.submitAnswer("S1", session.getUserId(), "Q_A_1", "O1", null, null);
        assertEquals(1, storedAnswers.size());

        // Complete
        Mockito.when(resultRepo.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
        PsychTestResult result = svc.complete("S1", session.getUserId());
        assertNotNull(result);
        assertNotNull(result.getOverallConfidence());
        assertNotNull(result.getDomainScoresJson());
    }
}

