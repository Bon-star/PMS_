package com.example.pms.service;

import com.example.pms.model.Student;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class OpenAiChatService {

    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_HISTORY_ITEMS = 12;
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final Pattern NON_LATIN_SCRIPT_PATTERN = Pattern.compile(".*[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}\\p{IsCyrillic}\\p{IsArabic}\\p{IsHebrew}\\p{IsThai}\\p{IsGreek}].*");
    private static final Pattern LATIN_EXTENDED_PATTERN = Pattern.compile(".*[\\p{InLatin-1Supplement}\\p{InLatinExtended-A}\\p{InLatinExtended-B}].*");
    private static final Set<String> GREETING_TERMS = terms(
            "hi", "hello", "hey", "good morning", "good afternoon", "good evening",
            "chao", "chào", "xin chao", "xin chào", "helo", "heloo",
            "hola", "bonjour", "ciao", "hallo", "مرحبا");
    private static final Set<String> PMS_SCOPE_TERMS = terms(
            "pms", "website", "web", "system", "student area", "page", "screen", "menu", "button", "sidebar",
            "group", "member", "leader", "invite", "invitation", "request", "join request",
            "project", "task", "sprint", "workflow", "approval", "review", "comment", "notification",
            "profile", "account", "password", "login", "log in", "register", "otp",
            "score", "grade", "semester", "class", "lecturer", "staff", "source code", "document",
            "history", "deadline", "progress", "status", "pending", "next step",
            "trang web", "he thong", "hệ thống", "nhom", "nhóm", "thanh vien", "thành viên",
            "truong nhom", "trưởng nhóm", "moi", "mời", "yeu cau", "yêu cầu",
            "du an", "dự án", "cong viec", "công việc", "nhiem vu", "nhiệm vụ", "sprint",
            "thong bao", "thông báo", "tai khoan", "tài khoản", "mat khau", "mật khẩu",
            "dang nhap", "đăng nhập", "dang ky", "đăng ký", "diem", "điểm", "hoc ky", "học kỳ",
            "giang vien", "giảng viên", "nhan vien", "nhân viên", "tai lieu", "tài liệu",
            "tien do", "tiến độ", "han nop", "hạn nộp", "buoc tiep theo", "bước tiếp theo");
    private static final Set<String> FOLLOW_UP_TERMS = terms(
            "what about", "and then", "what next", "next", "then", "that", "it", "this", "more", "details",
            "con", "còn", "roi sao", "rồi sao", "tiep theo", "tiếp theo", "the con", "thế còn", "vay", "vậy");
    private static final Set<String> PMS_GUIDANCE_TERMS = terms(
            "what should i do next", "what do i do next", "what is next", "anything pending",
            "what am i missing", "summarize my progress", "my progress", "my status",
            "toi nen lam gi tiep theo", "tôi nên làm gì tiếp theo", "toi dang thieu gi", "tôi đang thiếu gì",
            "tom tat tien do", "tóm tắt tiến độ", "trang thai cua toi", "trạng thái của tôi");
    private static final Set<String> VI_LANGUAGE_HINTS = terms(
            "toi", "tôi", "ban", "bạn", "gi", "gì", "nhu the nao", "như thế nào", "tai sao", "tại sao",
            "khong", "không", "duoc", "được", "giup", "giúp", "tren", "trên");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StudentAiKnowledgeBaseService knowledgeBaseService;
    private final StudentAiContextService contextService;
    private final StudentAiDocumentRetrievalService documentRetrievalService;

    private enum SupportedLanguage {
        ENGLISH,
        VIETNAMESE,
        OTHER
    }

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.api.model:" + DEFAULT_MODEL + "}")
    private String model;

    @Value("${openai.api.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    public OpenAiChatService(StudentAiKnowledgeBaseService knowledgeBaseService,
            StudentAiContextService contextService,
            StudentAiDocumentRetrievalService documentRetrievalService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.contextService = contextService;
        this.documentRetrievalService = documentRetrievalService;
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getModel() {
        return model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
    }

    public String createReply(Student student, String rawMessage, List<ChatTurn> rawHistory) {
        if (!isEnabled()) {
            throw new IllegalStateException("AI assistant has not been configured yet.");
        }

        String message = normalizeMessage(rawMessage);
        List<ChatTurn> history = sanitizeHistory(rawHistory);
        String greetingReply = buildGreetingReply(message);
        if (greetingReply != null) {
            return greetingReply;
        }
        if (!isPmsScopedQuestion(message, history)) {
            return buildOutOfScopeReply(message);
        }
        List<Map<String, String>> messages = buildMessages(student, history, message);
        JsonNode responseBody = requestChatCompletion(messages);
        String reply = extractReply(responseBody);
        if (reply.isBlank()) {
            throw new IllegalStateException("OpenAI returned an empty response.");
        }
        return reply.trim();
    }

    private List<Map<String, String>> buildMessages(Student student, List<ChatTurn> history, String message) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", buildSystemPrompt(student)));
        messages.add(Map.of(
                "role", "system",
                "content", buildLanguageInstruction(message)));
        String relevantDocuments = documentRetrievalService.buildRelevantContext(message);
        if (!relevantDocuments.isBlank()) {
            messages.add(Map.of(
                    "role", "system",
                    "content", relevantDocuments));
        }
        for (ChatTurn turn : history) {
            messages.add(Map.of(
                    "role", turn.role(),
                    "content", turn.content()));
        }
        messages.add(Map.of(
                "role", "user",
                "content", message));
        return messages;
    }

    private String buildSystemPrompt(Student student) {
        String studentName = student != null && student.getFullName() != null && !student.getFullName().isBlank()
                ? student.getFullName().trim()
                : "Student";
        return """
                You are PMS AI, a helpful assistant inside the student area of a project management system.
                Prefer concise, practical, step-by-step answers.
                The PMS student UI is currently in English.
                Officially supported response languages are English and Vietnamese only.
                Reply in Vietnamese only when the user's latest message is clearly Vietnamese.
                Reply in English when the user's latest message is English, mixed, neutral, unclear, or in any unsupported language.
                Do not answer in other languages.
                If the user asks about PMS workflows, current group state, project status, task progress, sprint status, notifications, or next actions, rely on the internal knowledge base and live context below first.
                If the user asks how to use a PMS feature or where a PMS action is located in the UI, only mention flows, buttons, or pages that are explicitly described in the knowledge base, retrieved documents, or live context.
                If a PMS feature path is not documented, say that you do not currently see that feature in this PMS implementation and then offer the closest verified alternative. Do not invent generic paths like settings, profile, or account pages unless they are explicitly supported by the provided materials.
                You only support questions that are directly related to PMS, this student website, its workflows, and the current student's PMS data.
                If the user asks about unrelated topics such as math, news, entertainment, general coding, translation, or broad knowledge outside PMS, politely refuse and redirect them back to PMS-related help.
                Never claim to read hidden data that is not included in the live context.
                Never reveal or infer private information about other groups, classes, students, lecturers, or staff beyond the provided context.
                If data is missing or uncertain, say that clearly and point the user to the relevant PMS page.
                Keep answers useful and grounded in the PMS student workflow.
                Today's date is %s.
                The current user is %s.

                INTERNAL KNOWLEDGE BASE
                %s

                %s
                """.formatted(
                LocalDate.now(),
                studentName,
                knowledgeBaseService.getStudentKnowledgeBase(),
                contextService.buildStudentContext(student));
    }

    private JsonNode requestChatCompletion(List<Map<String, String>> messages) {
        RestClient client = RestClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "model", getModel(),
                    "messages", messages,
                    "temperature", 0.3,
                    "max_tokens", 300,
                    "n", 1));
            String body = client.post()
                    .uri(resolveChatCompletionsEndpoint())
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(body);
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(resolveApiError(ex), ex);
        } catch (Exception ex) {
            String detail = ex.getMessage();
            if (detail == null || detail.isBlank()) {
                throw new IllegalStateException("Unable to connect to AI service right now.", ex);
            }
            throw new IllegalStateException("Unable to connect to AI service right now: " + detail, ex);
        }
    }

    private String extractReply(JsonNode responseBody) {
        JsonNode choices = responseBody.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return "";
        }
        JsonNode content = choices.get(0).path("message").path("content");
        return content.isTextual() ? content.asText("") : "";
    }

    private String resolveApiError(RestClientResponseException ex) {
        String fallback;
        if (ex.getStatusCode().value() == 401) {
            fallback = "OpenAI API key is invalid or missing.";
        } else if (ex.getStatusCode().value() == 429) {
            fallback = "OpenAI is rate limiting requests right now. Please try again shortly.";
        } else {
            fallback = "OpenAI request failed. Please try again.";
        }

        try {
            JsonNode root = objectMapper.readTree(ex.getResponseBodyAsString());
            String apiMessage = root.path("error").path("message").asText("");
            return apiMessage == null || apiMessage.isBlank() ? fallback : apiMessage.trim();
        } catch (Exception parseError) {
            return fallback;
        }
    }

    private String resolveChatCompletionsEndpoint() {
        String configuredBaseUrl = baseUrl == null ? "" : baseUrl.trim();
        if (configuredBaseUrl.isBlank()) {
            configuredBaseUrl = "https://api.openai.com/v1";
        }
        configuredBaseUrl = configuredBaseUrl.replaceAll("/+$", "");
        if (configuredBaseUrl.endsWith("/chat/completions")) {
            return configuredBaseUrl;
        }
        return configuredBaseUrl + "/chat/completions";
    }

    private List<ChatTurn> sanitizeHistory(List<ChatTurn> rawHistory) {
        if (rawHistory == null || rawHistory.isEmpty()) {
            return List.of();
        }

        List<ChatTurn> sanitized = new ArrayList<>();
        for (ChatTurn turn : rawHistory) {
            if (turn == null) {
                continue;
            }
            String role = normalizeRole(turn.role());
            String content = turn.content() == null ? "" : turn.content().trim();
            if (role == null || content.isBlank()) {
                continue;
            }
            if (content.length() > MAX_MESSAGE_LENGTH) {
                content = content.substring(0, MAX_MESSAGE_LENGTH);
            }
            sanitized.add(new ChatTurn(role, content));
        }

        if (sanitized.size() <= MAX_HISTORY_ITEMS) {
            return sanitized;
        }
        return new ArrayList<>(sanitized.subList(sanitized.size() - MAX_HISTORY_ITEMS, sanitized.size()));
    }

    private String normalizeRole(String rawRole) {
        if (rawRole == null) {
            return null;
        }
        String role = rawRole.trim().toLowerCase();
        if ("user".equals(role) || "assistant".equals(role)) {
            return role;
        }
        return null;
    }

    private String normalizeMessage(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isBlank()) {
            throw new IllegalArgumentException("Message cannot be empty.");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Message cannot be longer than 2000 characters.");
        }
        return message;
    }

    private String buildLanguageInstruction(String message) {
        return switch (detectSupportedLanguage(message)) {
            case VIETNAMESE ->
                "LANGUAGE RULE: The latest user message is Vietnamese. Reply in Vietnamese.";
            case OTHER ->
                "LANGUAGE RULE: The latest user message is not in an officially supported language. Reply in English only and briefly note that PMS AI officially supports English and Vietnamese.";
            case ENGLISH ->
                "LANGUAGE RULE: Reply in English.";
        };
    }

    private String buildGreetingReply(String message) {
        if (!isGreetingOnlyMessage(message)) {
            return null;
        }
        return switch (detectSupportedLanguage(message)) {
            case VIETNAMESE ->
                "Chào bạn! Mình là PMS AI. Mình có thể hỗ trợ về nhóm, project, sprint, task, điểm, tài liệu và cách sử dụng PMS.";
            case OTHER ->
                "Hello! I'm PMS AI. I officially support English and Vietnamese. Please ask your PMS-related question in English or Vietnamese.";
            case ENGLISH ->
                "Hello! I'm PMS AI. I can help with your group, project, sprint, task, score, documents, and how to use PMS.";
        };
    }

    private boolean isPmsScopedQuestion(String message, List<ChatTurn> history) {
        String lower = normalizeScopeText(message);
        if (containsAny(lower, PMS_SCOPE_TERMS) || containsAny(lower, PMS_GUIDANCE_TERMS)) {
            return true;
        }
        if (looksLikeFollowUp(lower) && recentHistoryLooksPms(history)) {
            return true;
        }
        return false;
    }

    private boolean recentHistoryLooksPms(List<ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return false;
        }
        int checked = 0;
        for (int i = history.size() - 1; i >= 0 && checked < 6; i--, checked++) {
            ChatTurn turn = history.get(i);
            if (turn == null || turn.content() == null) {
                continue;
            }
            String lower = normalizeScopeText(turn.content());
            if (containsAny(lower, PMS_SCOPE_TERMS) || containsAny(lower, PMS_GUIDANCE_TERMS)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeFollowUp(String lower) {
        if (lower.isBlank()) {
            return false;
        }
        if (containsAny(lower, FOLLOW_UP_TERMS)) {
            return true;
        }
        return lower.length() <= 80 && lower.split("\\s+").length <= 8;
    }

    private boolean containsAny(String lower, Set<String> terms) {
        for (String term : terms) {
            if (lower.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeScopeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isGreetingOnlyMessage(String message) {
        String normalized = normalizeGreetingText(message);
        if (normalized.isEmpty()) {
            return false;
        }
        return GREETING_TERMS.contains(normalized);
    }

    private String normalizeGreetingText(String message) {
        if (message == null) {
            return "";
        }
        return Normalizer.normalize(message, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private SupportedLanguage detectSupportedLanguage(String message) {
        if (isVietnameseMessage(message)) {
            return SupportedLanguage.VIETNAMESE;
        }
        if (containsNonLatinScript(message) || containsUnsupportedLatinAccents(message)) {
            return SupportedLanguage.OTHER;
        }
        return SupportedLanguage.ENGLISH;
    }

    private boolean containsNonLatinScript(String message) {
        return message != null && NON_LATIN_SCRIPT_PATTERN.matcher(message).matches();
    }

    private boolean containsUnsupportedLatinAccents(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return LATIN_EXTENDED_PATTERN.matcher(message).matches() && !isVietnameseMessage(message);
    }

    private static Set<String> terms(String... values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value == null) {
                    continue;
                }
                String item = value.trim();
                if (!item.isEmpty()) {
                    normalized.add(item);
                }
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    private String buildOutOfScopeReply(String message) {
        SupportedLanguage language = detectSupportedLanguage(message);
        if (language != null) {
            return switch (language) {
                case VIETNAMESE ->
                    "Mình chỉ hỗ trợ các câu hỏi liên quan đến PMS và website này, như tài khoản, profile, nhóm, project, sprint, task, điểm, tài liệu, thông báo, hoặc bước tiếp theo trong hệ thống.";
                case OTHER ->
                    "PMS AI officially supports English and Vietnamese only. Please ask your PMS-related question in English or Vietnamese.";
                case ENGLISH ->
                    "I can only help with questions related to PMS and this website, such as account access, profile, group, project, sprint, task, score, documents, notifications, or your next steps inside the system.";
            };
        }
        return isVietnameseMessage(message)
                ? "Mình chỉ hỗ trợ các câu hỏi liên quan đến PMS và website này, như tài khoản, profile, nhóm, project, sprint, task, điểm, tài liệu, thông báo, hoặc bước tiếp theo trong hệ thống."
                : "I can only help with questions related to PMS and this website, such as account access, profile, group, project, sprint, task, score, documents, notifications, or your next steps inside the system.";
    }

    private boolean isVietnameseMessage(String message) {
        String lower = normalizeScopeText(message);
        if (lower.matches(".*[ăâđêôơưáàảãạắằẳẵặấầẩẫậéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ].*")) {
            return true;
        }
        return containsAny(lower, VI_LANGUAGE_HINTS);
    }

    public record ChatTurn(String role, String content) {
    }
}
