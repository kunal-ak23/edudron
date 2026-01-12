package com.datagami.edudron.student.dto;

import java.util.List;

public class BulkStudentImportResult {
    private Long totalRows;
    private Long processedRows;
    private Long successfulRows;
    private Long failedRows;
    private Long skippedRows;
    private List<StudentImportRowResult> rowResults;

    public BulkStudentImportResult() {}

    // Getters and Setters
    public Long getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Long totalRows) {
        this.totalRows = totalRows;
    }

    public Long getProcessedRows() {
        return processedRows;
    }

    public void setProcessedRows(Long processedRows) {
        this.processedRows = processedRows;
    }

    public Long getSuccessfulRows() {
        return successfulRows;
    }

    public void setSuccessfulRows(Long successfulRows) {
        this.successfulRows = successfulRows;
    }

    public Long getFailedRows() {
        return failedRows;
    }

    public void setFailedRows(Long failedRows) {
        this.failedRows = failedRows;
    }

    public Long getSkippedRows() {
        return skippedRows;
    }

    public void setSkippedRows(Long skippedRows) {
        this.skippedRows = skippedRows;
    }

    public List<StudentImportRowResult> getRowResults() {
        return rowResults;
    }

    public void setRowResults(List<StudentImportRowResult> rowResults) {
        this.rowResults = rowResults;
    }
}

