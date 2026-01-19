package com.datagami.edudron.content.psychtest.web;

import com.datagami.edudron.common.TenantContextRestTemplateInterceptor;
import com.datagami.edudron.content.psychtest.domain.PsychTestResult;
import com.datagami.edudron.content.psychtest.domain.PsychTestSession;
import com.datagami.edudron.content.psychtest.service.ResultExplanationService;
import com.datagami.edudron.content.psychtest.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/psych-test")
@Tag(name = "Psych Test (v2)", description = "Adaptive RIASEC psychometric test (DB-driven) endpoints")
public class PsychTestController {
    private final SessionService sessionService;
    private final ResultExplanationService resultExplanationService;

    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;

    private volatile RestTemplate restTemplate;
    private final Object restTemplateLock = new Object();

    public PsychTestController(SessionService sessionService, ResultExplanationService resultExplanationService) {
        this.sessionService = sessionService;
        this.resultExplanationService = resultExplanationService;
    }

    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            synchronized (restTemplateLock) {
                if (restTemplate == null) {
                    restTemplate = new RestTemplate();
                    List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
                    interceptors.add(new TenantContextRestTemplateInterceptor());
                    interceptors.add((request, body, execution) -> {
                        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                        if (attributes != null) {
                            HttpServletRequest currentRequest = attributes.getRequest();
                            String authHeader = currentRequest.getHeader("Authorization");
                            if (authHeader != null && !authHeader.isBlank()) {
                                if (!request.getHeaders().containsKey("Authorization")) {
                                    request.getHeaders().add("Authorization", authHeader);
                                }
                            }
                        }
                        return execution.execute(request, body);
                    });
                    restTemplate.setInterceptors(interceptors);
                }
            }
        }
        return restTemplate;
    }

    public record StartSessionRequest(
        Integer grade,
        String locale,
        @Min(10) @Max(60) Integer maxQuestions
    ) {}

    public record StartSessionResponse(
        String sessionId,
        SessionService.NextQuestion firstQuestion
    ) {}

    @PostMapping("/sessions")
    @Operation(summary = "Start/resume a psych test session")
    public ResponseEntity<StartSessionResponse> start(@RequestBody(required = false) @Valid StartSessionRequest request) {
        UserProfile user = requireUserProfile();
        String userId = user.id();
        Integer grade = request != null ? request.grade() : null;
        String locale = request != null ? request.locale() : null;
        Integer maxQuestions = request != null ? request.maxQuestions() : null;

        PsychTestSession session = sessionService.startOrResume(userId, grade, locale, maxQuestions);
        SessionService.NextQuestion first = sessionService.getNextQuestion(session.getId(), userId, user.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(new StartSessionResponse(session.getId(), first));
    }

    @GetMapping("/sessions/{id}")
    @Operation(summary = "Get session status")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable("id") String id) {
        String userId = requireUserId();
        PsychTestSession s = sessionService.getSession(id, userId);
        return ResponseEntity.ok(Map.of(
            "id", s.getId(),
            "status", s.getStatus().name(),
            "startedAt", s.getStartedAt(),
            "completedAt", s.getCompletedAt(),
            "testVersion", s.getTestVersion(),
            "bankVersion", s.getBankVersion(),
            "scoringVersion", s.getScoringVersion(),
            "promptVersion", s.getPromptVersion(),
            "currentQuestionIndex", s.getCurrentQuestionIndex(),
            "maxQuestions", s.getMaxQuestions()
        ));
    }

    @GetMapping("/sessions/{id}/next-question")
    @Operation(summary = "Fetch next question")
    public ResponseEntity<SessionService.NextQuestion> nextQuestion(@PathVariable("id") String id) {
        UserProfile user = requireUserProfile();
        return ResponseEntity.ok(sessionService.getNextQuestion(id, user.id(), user.name()));
    }

    public record SubmitAnswerRequest(
        @NotBlank String questionId,
        String selectedOptionId,
        @Size(max = 500) String text,
        @Min(0) @Max(600000) Integer timeSpentMs
    ) {}

    @PostMapping("/sessions/{id}/answers")
    @Operation(summary = "Submit an answer")
    public ResponseEntity<Void> submitAnswer(@PathVariable("id") String id, @RequestBody @Valid SubmitAnswerRequest request) {
        String userId = requireUserId();
        sessionService.submitAnswer(id, userId, request.questionId(), request.selectedOptionId(), request.text(), request.timeSpentMs());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/sessions/{id}/complete")
    @Operation(summary = "Complete test and persist results")
    public ResponseEntity<Map<String, Object>> complete(@PathVariable("id") String id) {
        String userId = requireUserId();
        PsychTestResult r = sessionService.complete(id, userId);
        return ResponseEntity.ok(Map.of(
            "sessionId", id,
            "resultId", r.getId()
        ));
    }

    @PostMapping("/sessions/{id}/regenerate-result")
    @Operation(summary = "Re-generate stored narrative/explanations for an existing result (no re-scoring)")
    public ResponseEntity<Map<String, Object>> regenerate(@PathVariable("id") String id) {
        String userId = requireUserId();
        PsychTestResult r = sessionService.regenerateResultArtifacts(id, userId);
        return ResponseEntity.ok(Map.of(
            "sessionId", id,
            "resultId", r.getId()
        ));
    }

    @GetMapping("/sessions/{id}/result")
    @Operation(summary = "Get test result")
    public ResponseEntity<Map<String, Object>> result(@PathVariable("id") String id) {
        String userId = requireUserId();
        PsychTestSession s = sessionService.getSession(id, userId);
        PsychTestResult r = sessionService.getResult(id, userId);

        List<String> topDomains = (r.getTopDomainsJson() != null && r.getTopDomainsJson().isArray())
            ? new ArrayList<>() : List.of();
        if (r.getTopDomainsJson() != null && r.getTopDomainsJson().isArray()) {
            r.getTopDomainsJson().forEach(n -> topDomains.add(n.asText()));
        }

        Object answerBreakdown = null;
        Object domainNarratives = null;
        Object suggestions = null;
        if (r.getExplanationsJson() != null && r.getExplanationsJson().isObject()) {
            if (r.getExplanationsJson().hasNonNull("answerBreakdown")) {
                answerBreakdown = r.getExplanationsJson().get("answerBreakdown");
            }
            if (r.getExplanationsJson().hasNonNull("domainNarratives")) {
                domainNarratives = r.getExplanationsJson().get("domainNarratives");
            }
            if (r.getExplanationsJson().hasNonNull("suggestions")) {
                suggestions = r.getExplanationsJson().get("suggestions");
            }
        }
        if (answerBreakdown == null || suggestions == null || domainNarratives == null) {
            ResultExplanationService.ResultExplanation explanation = resultExplanationService.explain(
                s,
                r.getOverallConfidence(),
                topDomains
            );
            if (answerBreakdown == null) answerBreakdown = explanation.answers();
            if (suggestions == null) suggestions = explanation.suggestions();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", r.getId());
        out.put("sessionId", id);
        out.put("overallConfidence", r.getOverallConfidence());
        out.put("domainScores", r.getDomainScoresJson());
        out.put("topDomains", r.getTopDomainsJson());
        out.put("streamSuggestion", r.getStreamSuggestion());
        out.put("careerFields", r.getCareerFieldsJson());
        out.put("recommendedCourses", r.getRecommendedCoursesJson());
        out.put("reportText", r.getReportText());
        out.put("answerBreakdown", answerBreakdown);
        out.put("domainNarratives", domainNarratives);
        out.put("suggestions", suggestions);
        out.put("createdAt", r.getCreatedAt());
        return ResponseEntity.ok(out);
    }

    @GetMapping("/results")
    @Operation(summary = "List recent completed psych test results for current user")
    public ResponseEntity<List<Map<String, Object>>> listResults(
        @RequestParam(name = "limit", required = false, defaultValue = "20") @Min(1) @Max(100) Integer limit
    ) {
        String userId = requireUserId();
        List<PsychTestResult> results = sessionService.listRecentResults(userId, limit != null ? limit : 20);

        List<Map<String, Object>> out = results.stream().map(r -> {
            PsychTestSession s = r.getSession();
            List<String> topDomains = new ArrayList<>();
            if (r.getTopDomainsJson() != null && r.getTopDomainsJson().isArray()) {
                r.getTopDomainsJson().forEach(n -> topDomains.add(n.asText()));
            }
            return Map.<String, Object>of(
                "sessionId", s != null ? s.getId() : null,
                "completedAt", s != null ? s.getCompletedAt() : null,
                "overallConfidence", r.getOverallConfidence(),
                "topDomains", topDomains,
                "streamSuggestion", r.getStreamSuggestion(),
                "createdAt", r.getCreatedAt()
            );
        }).toList();

        return ResponseEntity.ok(out);
    }

    private String requireUserId() {
        return requireUserProfile().id();
    }

    private record UserProfile(String id, String name) {}

    private UserProfile requireUserProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalStateException("Unauthorized");
        }

        // Resolve ULID user id via identity service (/idp/users/me), matching existing behavior.
        try {
            String meUrl = gatewayUrl + "/idp/users/me";
            ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                meUrl,
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object id = response.getBody().get("id");
                if (id != null && !id.toString().isBlank()) {
                    Object name = response.getBody().get("name");
                    return new UserProfile(id.toString(), name != null ? name.toString() : null);
                }
            }
        } catch (Exception ignored) {
        }

        throw new IllegalStateException("Unauthorized");
    }
}

