package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.CreateFeedbackRequest;
import com.datagami.edudron.student.dto.FeedbackDTO;
import com.datagami.edudron.student.service.FeedbackService;
import com.datagami.edudron.student.util.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Feedback", description = "Lecture feedback endpoints (like/dislike)")
public class FeedbackController {

    @Autowired
    private FeedbackService feedbackService;

    @PostMapping("/lectures/{lectureId}/feedback")
    @Operation(summary = "Create or update feedback", description = "Submit like or dislike feedback for a lecture")
    public ResponseEntity<FeedbackDTO> createOrUpdateFeedback(
            @PathVariable String lectureId,
            @Valid @RequestBody CreateFeedbackRequest request) {
        String studentId = UserUtil.getCurrentUserId();
        request.setLectureId(lectureId);
        FeedbackDTO feedback = feedbackService.createOrUpdateFeedback(studentId, request);
        return ResponseEntity.ok(feedback);
    }

    @GetMapping("/lectures/{lectureId}/feedback")
    @Operation(summary = "Get feedback", description = "Get feedback for a specific lecture")
    public ResponseEntity<FeedbackDTO> getFeedback(@PathVariable String lectureId) {
        String studentId = UserUtil.getCurrentUserId();
        FeedbackDTO feedback = feedbackService.getFeedback(studentId, lectureId);
        if (feedback == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(feedback);
    }

    @GetMapping("/lectures/{lectureId}/feedback/all")
    @Operation(summary = "Get all feedback for lecture", description = "Get all feedback entries for a lecture")
    public ResponseEntity<List<FeedbackDTO>> getAllFeedbackForLecture(@PathVariable String lectureId) {
        List<FeedbackDTO> feedbacks = feedbackService.getFeedbackByLecture(lectureId);
        return ResponseEntity.ok(feedbacks);
    }

    @GetMapping("/courses/{courseId}/feedback")
    @Operation(summary = "Get feedback by course", description = "Get all feedback for a course")
    public ResponseEntity<List<FeedbackDTO>> getFeedbackByCourse(@PathVariable String courseId) {
        String studentId = UserUtil.getCurrentUserId();
        List<FeedbackDTO> feedbacks = feedbackService.getFeedbackByCourse(studentId, courseId);
        return ResponseEntity.ok(feedbacks);
    }

    @DeleteMapping("/lectures/{lectureId}/feedback")
    @Operation(summary = "Delete feedback", description = "Remove feedback for a lecture")
    public ResponseEntity<Void> deleteFeedback(@PathVariable String lectureId) {
        String studentId = UserUtil.getCurrentUserId();
        feedbackService.deleteFeedback(studentId, lectureId);
        return ResponseEntity.noContent().build();
    }
}

