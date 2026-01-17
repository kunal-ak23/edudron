package com.datagami.edudron.content.service.psychometric;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PsychometricScoringService
 */
@ExtendWith(MockitoExtension.class)
class PsychometricScoringServiceTest {
    
    private PsychometricScoringService scoringService;
    
    @BeforeEach
    void setUp() {
        scoringService = new PsychometricScoringService();
    }
    
    @Test
    void testScoreCoreAnswers_AllSameAnswers() {
        // Test case: All same answers (all "Strongly Agree")
        SessionState state = new SessionState();
        List<Answer> answers = new ArrayList<>();
        
        // Add 18 "Strongly Agree" answers
        for (int i = 1; i <= 18; i++) {
            Question q = CoreQuestionBank.getQuestionByOrder(i);
            if (q != null) {
                answers.add(new Answer(q.getId(), "Strongly Agree"));
            }
        }
        
        state.setCoreAnswers(answers);
        scoringService.scoreCoreAnswers(state, CoreQuestionBank.getCoreQuestions());
        
        // Verify scores are computed
        assertNotNull(state.getRiasecScores());
        assertNotNull(state.getIndicatorScores());
        assertNotNull(state.getStreamScores());
        
        // All scores should be positive (Strongly Agree = +2)
        assertTrue(state.getStreamScores().values().stream().anyMatch(s -> s > 0));
    }
    
    @Test
    void testScoreCoreAnswers_ConflictingAnswers() {
        // Test case: Conflicting answers (some strongly agree, some strongly disagree)
        SessionState state = new SessionState();
        List<Answer> answers = new ArrayList<>();
        
        // Mix of answers
        String[] answerValues = {"Strongly Agree", "Strongly Disagree", "Agree", "Disagree", "Not Sure"};
        int answerIndex = 0;
        
        for (int i = 1; i <= 18; i++) {
            Question q = CoreQuestionBank.getQuestionByOrder(i);
            if (q != null) {
                answers.add(new Answer(q.getId(), answerValues[answerIndex % answerValues.length]));
                answerIndex++;
            }
        }
        
        state.setCoreAnswers(answers);
        scoringService.scoreCoreAnswers(state, CoreQuestionBank.getCoreQuestions());
        
        // Verify scores are computed (should have mixed results)
        assertNotNull(state.getRiasecScores());
        assertNotNull(state.getStreamScores());
    }
    
    @Test
    void testDetectPrimeCandidates_ClearScience() {
        // Test case: Clear Science preference
        SessionState state = new SessionState();
        state.getStreamScores().put("Science", 15.0);
        state.getStreamScores().put("Commerce", 2.0);
        state.getStreamScores().put("Arts", 1.0);
        
        state.getRiasecScores().put("I", 10.0);
        state.getRiasecScores().put("R", 8.0);
        state.getRiasecScores().put("A", 2.0);
        state.getRiasecScores().put("S", 1.0);
        state.getRiasecScores().put("E", 1.0);
        state.getRiasecScores().put("C", 1.0);
        
        scoringService.detectPrimeCandidates(state);
        
        assertNotNull(state.getPrimeCandidates());
        assertTrue(state.getPrimeCandidates().contains("STREAM:Science"));
        assertTrue(state.getPrimeCandidates().contains("RIASEC:I"));
    }
    
    @Test
    void testDetectPrimeCandidates_ClearCommerce() {
        // Test case: Clear Commerce preference
        SessionState state = new SessionState();
        state.getStreamScores().put("Science", 2.0);
        state.getStreamScores().put("Commerce", 16.0);
        state.getStreamScores().put("Arts", 1.0);
        
        state.getRiasecScores().put("E", 12.0);
        state.getRiasecScores().put("C", 10.0);
        state.getRiasecScores().put("I", 2.0);
        state.getRiasecScores().put("A", 1.0);
        state.getRiasecScores().put("S", 1.0);
        state.getRiasecScores().put("R", 1.0);
        
        scoringService.detectPrimeCandidates(state);
        
        assertNotNull(state.getPrimeCandidates());
        assertTrue(state.getPrimeCandidates().contains("STREAM:Commerce"));
        assertTrue(state.getPrimeCandidates().contains("RIASEC:E"));
    }
    
    @Test
    void testDetectPrimeCandidates_ClearArts() {
        // Test case: Clear Arts preference
        SessionState state = new SessionState();
        state.getStreamScores().put("Science", 1.0);
        state.getStreamScores().put("Commerce", 2.0);
        state.getStreamScores().put("Arts", 14.0);
        
        state.getRiasecScores().put("A", 11.0);
        state.getRiasecScores().put("S", 9.0);
        state.getRiasecScores().put("I", 2.0);
        state.getRiasecScores().put("R", 1.0);
        state.getRiasecScores().put("E", 1.0);
        state.getRiasecScores().put("C", 1.0);
        
        scoringService.detectPrimeCandidates(state);
        
        assertNotNull(state.getPrimeCandidates());
        assertTrue(state.getPrimeCandidates().contains("STREAM:Arts"));
        assertTrue(state.getPrimeCandidates().contains("RIASEC:A"));
    }
    
    @Test
    void testDetermineStreams_CloseScoreSecondary() {
        // Test case: Close scores - should have secondary stream
        SessionState state = new SessionState();
        state.getStreamScores().put("Science", 10.0);
        state.getStreamScores().put("Commerce", 8.0); // Within 5 points
        state.getStreamScores().put("Arts", 2.0);
        
        Map<String, String> streams = scoringService.determineStreams(state);
        
        assertEquals("Science", streams.get("primary"));
        assertEquals("Commerce", streams.get("secondary")); // Should have secondary
    }
    
    @Test
    void testDetermineStreams_NoSecondary() {
        // Test case: Large gap - no secondary stream
        SessionState state = new SessionState();
        state.getStreamScores().put("Science", 15.0);
        state.getStreamScores().put("Commerce", 5.0); // More than 5 points gap
        state.getStreamScores().put("Arts", 2.0);
        
        Map<String, String> streams = scoringService.determineStreams(state);
        
        assertEquals("Science", streams.get("primary"));
        assertNull(streams.get("secondary")); // No secondary
    }
    
    @Test
    void testSelectAdaptiveModules() {
        SessionState state = new SessionState();
        state.getStreamScores().put("Science", 12.0);
        state.getStreamScores().put("Commerce", 8.0);
        state.getStreamScores().put("Arts", 3.0);
        
        List<String> modules = scoringService.selectAdaptiveModules(state);
        
        assertNotNull(modules);
        assertFalse(modules.isEmpty());
        assertEquals("SCIENCE", modules.get(0));
    }
    
    @Test
    void testGetTopRiasecThemes() {
        SessionState state = new SessionState();
        state.getRiasecScores().put("I", 12.0);
        state.getRiasecScores().put("R", 10.0);
        state.getRiasecScores().put("A", 5.0);
        state.getRiasecScores().put("S", 3.0);
        state.getRiasecScores().put("E", 2.0);
        state.getRiasecScores().put("C", 1.0);
        
        List<RiasecTheme> themes = scoringService.getTopRiasecThemes(state);
        
        assertNotNull(themes);
        assertEquals(2, themes.size());
        assertEquals("I", themes.get(0).getCode());
        assertEquals("R", themes.get(1).getCode());
    }
}
