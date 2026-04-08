package com.example.pms.controller;

import com.example.pms.model.Student;
import com.example.pms.service.OpenAiChatService;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/ai-chat")
public class StudentAiChatController {

    private final OpenAiChatService openAiChatService;

    public StudentAiChatController(OpenAiChatService openAiChatService) {
        this.openAiChatService = openAiChatService;
    }

    @PostMapping("/message")
    public ResponseEntity<Map<String, Object>> sendMessage(@RequestBody(required = false) AiChatRequest request,
            HttpSession session) {
        Student student = (Student) session.getAttribute("userProfile");
        if (student == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Please log in again."));
        }

        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Request body is required."));
        }

        List<OpenAiChatService.ChatTurn> history = request.history == null
                ? List.of()
                : request.history.stream()
                        .filter(item -> item != null)
                        .map(item -> new OpenAiChatService.ChatTurn(item.role, item.content))
                        .toList();

        try {
            String reply = openAiChatService.createReply(student, request.message, history);
            return ResponseEntity.ok(Map.of(
                    "reply", reply,
                    "model", openAiChatService.getModel()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (IllegalStateException ex) {
            HttpStatus status = openAiChatService.isEnabled() ? HttpStatus.BAD_GATEWAY : HttpStatus.SERVICE_UNAVAILABLE;
            return ResponseEntity.status(status).body(Map.of("message", ex.getMessage()));
        }
    }

    public static class AiChatRequest {
        private String message;
        private List<AiChatTurn> history;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public List<AiChatTurn> getHistory() {
            return history;
        }

        public void setHistory(List<AiChatTurn> history) {
            this.history = history;
        }
    }

    public static class AiChatTurn {
        private String role;
        private String content;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
