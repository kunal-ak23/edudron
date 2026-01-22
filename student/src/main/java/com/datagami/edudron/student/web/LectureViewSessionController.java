package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.EndSessionRequest;
import com.datagami.edudron.student.dto.LectureViewSessionDTO;
import com.datagami.edudron.student.dto.StartSessionRequest;
import com.datagami.edudron.student.service.LectureViewSessionService;
import com.datagami.edudron.student.util.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Lecture View Sessions", description = "Lecture viewing session tracking endpoints")
public class LectureViewSessionController {

    private static final Logger log = LoggerFactory.getLogger(LectureViewSessionController.class);

    @Autowired
    private LectureViewSessionService sessionService;

    @PostMapping("/lectures/{lectureId}/sessions/start")
    @Operation(summary = "Start lecture viewing session", description = "Start tracking a new viewing session for a lecture")
    public ResponseEntity<LectureViewSessionDTO> startSession(
            @PathVariable String lectureId,
            @Valid @RequestBody StartSessionRequest request) {
        String studentId = UserUtil.getCurrentUserId();
        log.info("[Session Controller] Received start session request: lectureId={}, studentId={}, courseId={}, progressAtStart={}", 
            lectureId, studentId, request.getCourseId(), request.getProgressAtStart());
        
        try {
            request.setLectureId(lectureId);
            LectureViewSessionDTO session = sessionService.startSession(studentId, request);
            log.info("[Session Controller] Successfully started session: sessionId={}, lectureId={}, studentId={}", 
                session.getId(), lectureId, studentId);
            return ResponseEntity.ok(session);
        } catch (Exception e) {
            log.error("[Session Controller] Failed to start session: lectureId={}, studentId={}, error={}", 
                lectureId, studentId, e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/lectures/{lectureId}/sessions/{sessionId}/end")
    @Operation(summary = "End lecture viewing session", description = "End a viewing session and record final progress")
    public ResponseEntity<LectureViewSessionDTO> endSession(
            @PathVariable String lectureId,
            @PathVariable String sessionId,
            @Valid @RequestBody EndSessionRequest request) {
        log.info("[Session Controller] Received end session request: sessionId={}, lectureId={}, progressAtEnd={}, isCompleted={}", 
            sessionId, lectureId, request.getProgressAtEnd(), request.getIsCompleted());
        
        try {
            LectureViewSessionDTO session = sessionService.endSession(sessionId, request);
            log.info("[Session Controller] Successfully ended session: sessionId={}, lectureId={}, duration={}s", 
                sessionId, lectureId, session.getDurationSeconds());
            return ResponseEntity.ok(session);
        } catch (Exception e) {
            log.error("[Session Controller] Failed to end session: sessionId={}, lectureId={}, error={}", 
                sessionId, lectureId, e.getMessage(), e);
            throw e;
        }
    }
}
