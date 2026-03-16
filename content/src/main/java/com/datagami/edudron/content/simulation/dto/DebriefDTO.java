package com.datagami.edudron.content.simulation.dto;

public class DebriefDTO {
    private String yourPath;
    private String conceptAtWork;
    private String theGap;
    private String playAgain;

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
}
