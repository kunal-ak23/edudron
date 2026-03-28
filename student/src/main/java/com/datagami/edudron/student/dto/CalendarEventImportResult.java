package com.datagami.edudron.student.dto;

import java.util.List;

public record CalendarEventImportResult(
    int created,
    int errors,
    List<ImportError> errorDetails
) {
    public record ImportError(int row, String message) {}
}
