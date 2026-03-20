package com.datagami.edudron.content.dto;

import jakarta.validation.Valid;

import java.util.List;

public class BulkProjectQuestionRequest {

    @Valid
    private List<CreateProjectQuestionRequest> questions;

    public BulkProjectQuestionRequest() {}

    public List<CreateProjectQuestionRequest> getQuestions() { return questions; }
    public void setQuestions(List<CreateProjectQuestionRequest> questions) { this.questions = questions; }
}
