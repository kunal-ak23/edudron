package com.datagami.edudron.student.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.student.domain.Note;
import com.datagami.edudron.student.dto.CreateNoteRequest;
import com.datagami.edudron.student.dto.NoteDTO;
import com.datagami.edudron.student.repo.NoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class NoteService {
    
    @Autowired
    private NoteRepository noteRepository;
    
    public NoteDTO createNote(String studentId, CreateNoteRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Note note = new Note();
        note.setId(UlidGenerator.nextUlid());
        note.setClientId(clientId);
        note.setStudentId(studentId);
        note.setLectureId(request.getLectureId());
        note.setCourseId(request.getCourseId());
        note.setHighlightedText(request.getHighlightedText());
        note.setHighlightColor(request.getHighlightColor());
        note.setNoteText(request.getNoteText());
        note.setContext(request.getContext());
        
        Note saved = noteRepository.save(note);
        return toDTO(saved);
    }
    
    public NoteDTO updateNote(String studentId, String noteId, CreateNoteRequest request) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Note note = noteRepository.findById(noteId)
            .orElseThrow(() -> new IllegalArgumentException("Note not found"));
        
        if (!note.getClientId().equals(clientId) || !note.getStudentId().equals(studentId)) {
            throw new IllegalArgumentException("Unauthorized access to note");
        }
        
        note.setHighlightedText(request.getHighlightedText());
        note.setHighlightColor(request.getHighlightColor());
        note.setNoteText(request.getNoteText());
        note.setContext(request.getContext());
        
        Note saved = noteRepository.save(note);
        return toDTO(saved);
    }
    
    public List<NoteDTO> getNotesByLecture(String studentId, String lectureId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Note> notes = noteRepository.findByClientIdAndStudentIdAndLectureId(
            clientId, studentId, lectureId);
        return notes.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public List<NoteDTO> getNotesByCourse(String studentId, String courseId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        List<Note> notes = noteRepository.findByClientIdAndStudentIdAndCourseId(
            clientId, studentId, courseId);
        return notes.stream().map(this::toDTO).collect(Collectors.toList());
    }
    
    public void deleteNote(String studentId, String noteId) {
        String clientIdStr = TenantContext.getClientId();
        if (clientIdStr == null) {
            throw new IllegalStateException("Tenant context is not set");
        }
        UUID clientId = UUID.fromString(clientIdStr);
        
        Note note = noteRepository.findById(noteId)
            .orElseThrow(() -> new IllegalArgumentException("Note not found"));
        
        if (!note.getClientId().equals(clientId) || !note.getStudentId().equals(studentId)) {
            throw new IllegalArgumentException("Unauthorized access to note");
        }
        
        noteRepository.delete(note);
    }
    
    private NoteDTO toDTO(Note note) {
        NoteDTO dto = new NoteDTO();
        dto.setId(note.getId());
        dto.setClientId(note.getClientId());
        dto.setStudentId(note.getStudentId());
        dto.setLectureId(note.getLectureId());
        dto.setCourseId(note.getCourseId());
        dto.setHighlightedText(note.getHighlightedText());
        dto.setHighlightColor(note.getHighlightColor());
        dto.setNoteText(note.getNoteText());
        dto.setContext(note.getContext());
        dto.setCreatedAt(note.getCreatedAt());
        dto.setUpdatedAt(note.getUpdatedAt());
        return dto;
    }
}

