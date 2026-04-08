package com.datagami.edudron.content.simulation.dto;

import java.util.List;

public class DebriefDTO {
    private String yourPath;
    private String conceptAtWork;
    private String theGap;
    private String playAgain;
    private List<DecisionBreakdownDTO> decisionBreakdown;
    private String patternAnalysis;

    public static class DecisionBreakdownDTO {
        private String decisionId;
        private String label;
        private Integer quality;
        private String whatHappened;
        private String conceptLesson;

        public String getDecisionId() { return decisionId; }
        public void setDecisionId(String decisionId) { this.decisionId = decisionId; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public Integer getQuality() { return quality; }
        public void setQuality(Integer quality) { this.quality = quality; }
        public String getWhatHappened() { return whatHappened; }
        public void setWhatHappened(String whatHappened) { this.whatHappened = whatHappened; }
        public String getConceptLesson() { return conceptLesson; }
        public void setConceptLesson(String conceptLesson) { this.conceptLesson = conceptLesson; }
    }

    public DebriefDTO() {}

    public DebriefDTO(String yourPath, String conceptAtWork, String theGap, String playAgain) {
        this.yourPath = yourPath;
        this.conceptAtWork = conceptAtWork;
        this.theGap = theGap;
        this.playAgain = playAgain;
    }

    public String getYourPath() { return yourPath; }
    public void setYourPath(String yourPath) { this.yourPath = yourPath; }

    public String getConceptAtWork() { return conceptAtWork; }
    public void setConceptAtWork(String conceptAtWork) { this.conceptAtWork = conceptAtWork; }

    public String getTheGap() { return theGap; }
    public void setTheGap(String theGap) { this.theGap = theGap; }

    public String getPlayAgain() { return playAgain; }
    public void setPlayAgain(String playAgain) { this.playAgain = playAgain; }

    public List<DecisionBreakdownDTO> getDecisionBreakdown() { return decisionBreakdown; }
    public void setDecisionBreakdown(List<DecisionBreakdownDTO> decisionBreakdown) { this.decisionBreakdown = decisionBreakdown; }

    public String getPatternAnalysis() { return patternAnalysis; }
    public void setPatternAnalysis(String patternAnalysis) { this.patternAnalysis = patternAnalysis; }
}
