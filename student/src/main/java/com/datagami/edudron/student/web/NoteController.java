package com.datagami.edudron.student.web;

import com.datagami.edudron.student.dto.CreateNoteRequest;
import com.datagami.edudron.student.dto.NoteDTO;
import com.datagami.edudron.student.service.NoteService;
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
@Tag(name = "Notes", description = "Student notes and highlights endpoints")
public class NoteController {

    @Autowired
    private NoteService noteService;

    @PostMapping("/lectures/{lectureId}/notes")
    @Operation(summary = "Create note", description = "Create a new note with highlight for a lecture")
    public ResponseEntity<NoteDTO> createNote(
            @PathVariable String lectureId,
            @Valid @RequestBody CreateNoteRequest request) {
        String studentId = UserUtil.getCurrentUserId();
        request.setLectureId(lectureId);
        NoteDTO note = noteService.createNote(studentId, request);
        return ResponseEntity.ok(note);
    }

    @PutMapping("/notes/{noteId}")
    @Operation(summary = "Update note", description = "Update an existing note")
    public ResponseEntity<NoteDTO> updateNote(
            @PathVariable String noteId,
            @Valid @RequestBody CreateNoteRequest request) {
        String studentId = UserUtil.getCurrentUserId();
        NoteDTO note = noteService.updateNote(studentId, noteId, request);
        return ResponseEntity.ok(note);
    }

    @GetMapping("/lectures/{lectureId}/notes")
    @Operation(summary = "Get notes by lecture", description = "Get all notes for a specific lecture")
    public ResponseEntity<List<NoteDTO>> getNotesByLecture(@PathVariable String lectureId) {
        String studentId = UserUtil.getCurrentUserId();
        List<NoteDTO> notes = noteService.getNotesByLecture(studentId, lectureId);
        return ResponseEntity.ok(notes);
    }

    @GetMapping("/courses/{courseId}/notes")
    @Operation(summary = "Get notes by course", description = "Get all notes for a course")
    public ResponseEntity<List<NoteDTO>> getNotesByCourse(@PathVariable String courseId) {
        String studentId = UserUtil.getCurrentUserId();
        List<NoteDTO> notes = noteService.getNotesByCourse(studentId, courseId);
        return ResponseEntity.ok(notes);
    }

    @DeleteMapping("/notes/{noteId}")
    @Operation(summary = "Delete note", description = "Delete a note")
    public ResponseEntity<Void> deleteNote(@PathVariable String noteId) {
        String studentId = UserUtil.getCurrentUserId();
        noteService.deleteNote(studentId, noteId);
        return ResponseEntity.noContent().build();
    }
}

