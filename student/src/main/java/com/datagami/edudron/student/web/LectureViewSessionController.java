package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.EndSessionRequest;
import com.datagami.edudron.student.dto.LectureViewSessionDTO;
import com.datagami.edudron.student.dto.StartSessionRequest;
import com.datagami.edudron.student.service.LectureViewSessionService;
import com.datagami.edudron.student.util.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Lecture View Sessions", description = "Lecture viewing session tracking endpoints")
public class LectureViewSessionController {

    @Autowired
    private LectureViewSessionService sessionService;

    @PostMapping("/lectures/{lectureId}/sessions/start")
    @Operation(summary = "Start lecture viewing session", description = "Start tracking a new viewing session for a lecture")
    public ResponseEntity<LectureViewSessionDTO> startSession(
            @PathVariable String lectureId,
            @Valid @RequestBody StartSessionRequest request) {
        String studentId = UserUtil.getCurrentUserId();
        request.setLectureId(lectureId);
        LectureViewSessionDTO session = sessionService.startSession(studentId, request);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/lectures/{lectureId}/sessions/{sessionId}/end")
    @Operation(summary = "End lecture viewing session", description = "End a viewing session and record final progress")
    public ResponseEntity<LectureViewSessionDTO> endSession(
            @PathVariable String lectureId,
            @PathVariable String sessionId,
            @Valid @RequestBody EndSessionRequest request) {
        LectureViewSessionDTO session = sessionService.endSession(sessionId, request);
        return ResponseEntity.ok(session);
    }
}
