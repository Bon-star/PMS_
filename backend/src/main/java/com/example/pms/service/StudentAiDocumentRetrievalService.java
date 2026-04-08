package com.example.pms.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Service
public class StudentAiDocumentRetrievalService {

    private static final int MAX_CHUNK_LENGTH = 900;
    private static final int MAX_RESULTS = 3;
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final String extraDocsDir;

    public StudentAiDocumentRetrievalService(@Value("${openai.ai-docs.student-dir:ai-docs/student}") String extraDocsDir) {
        this.extraDocsDir = extraDocsDir;
    }

    public String buildRelevantContext(String query) {
        List<DocumentChunk> topChunks = retrieveRelevantChunks(query);
        if (topChunks.isEmpty()) {
            return "DOCUMENT RETRIEVAL STATUS\n- No matching internal documentation was found for the latest user question.";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("RELEVANT INTERNAL DOCUMENT EXCERPTS\n");
        int index = 1;
        for (DocumentChunk chunk : topChunks) {
            builder.append(index++)
                    .append(". Source: ")
                    .append(chunk.source())
                    .append('\n')
                    .append(chunk.content())
                    .append("\n\n");
        }
        return builder.toString().trim();
    }

    private List<DocumentChunk> retrieveRelevantChunks(String query) {
        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        List<ScoredChunk> scored = new ArrayList<>();
        for (DocumentChunk chunk : loadChunks()) {
            double score = scoreChunk(chunk, queryTokens, query);
            if (score > 0) {
                scored.add(new ScoredChunk(chunk, score));
            }
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(MAX_RESULTS)
                .map(ScoredChunk::chunk)
                .toList();
    }

    private List<DocumentChunk> loadChunks() {
        List<DocumentChunk> chunks = new ArrayList<>();
        addIfPresent(chunks, Path.of("pj.text"), "pj.text");
        addIfPresent(chunks, Path.of("README.md"), "README.md");
        addDirectoryDocs(chunks, Path.of(extraDocsDir));
        addClasspathDoc(chunks, "ai/student-assistant-guide.md");
        return chunks;
    }

    private void addDirectoryDocs(List<DocumentChunk> chunks, Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(this::isSupportedDocument)
                    .sorted()
                    .forEach(path -> addIfPresent(chunks, path, path.toString().replace('\\', '/')));
        } catch (IOException ex) {
            // Best effort only.
        }
    }

    private boolean isSupportedDocument(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".md")
                || name.endsWith(".txt")
                || name.endsWith(".text")
                || name.endsWith(".html");
    }

    private void addClasspathDoc(List<DocumentChunk> chunks, String classpathLocation) {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            return;
        }
        try {
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            chunkDocument(chunks, classpathLocation, content);
        } catch (IOException ex) {
            // Best effort only.
        }
    }

    private void addIfPresent(List<DocumentChunk> chunks, Path path, String sourceName) {
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            chunkDocument(chunks, sourceName, content);
        } catch (IOException ex) {
            // Best effort only.
        }
    }

    private void chunkDocument(List<DocumentChunk> chunks, String sourceName, String content) {
        if (content == null || content.isBlank()) {
            return;
        }

        String normalized = content.replace("\r\n", "\n").trim();
        String[] paragraphs = normalized.split("\\n\\s*\\n");
        StringBuilder buffer = new StringBuilder();
        for (String paragraph : paragraphs) {
            String cleaned = paragraph == null ? "" : paragraph.trim();
            if (cleaned.isBlank()) {
                continue;
            }
            if (buffer.length() > 0 && buffer.length() + cleaned.length() + 2 > MAX_CHUNK_LENGTH) {
                chunks.add(new DocumentChunk(sourceName, buffer.toString().trim()));
                buffer.setLength(0);
            }
            if (cleaned.length() > MAX_CHUNK_LENGTH) {
                for (int start = 0; start < cleaned.length(); start += MAX_CHUNK_LENGTH) {
                    int end = Math.min(start + MAX_CHUNK_LENGTH, cleaned.length());
                    chunks.add(new DocumentChunk(sourceName, cleaned.substring(start, end).trim()));
                }
                continue;
            }
            if (buffer.length() > 0) {
                buffer.append("\n\n");
            }
            buffer.append(cleaned);
        }
        if (buffer.length() > 0) {
            chunks.add(new DocumentChunk(sourceName, buffer.toString().trim()));
        }
    }

    private double scoreChunk(DocumentChunk chunk, Set<String> queryTokens, String rawQuery) {
        String normalizedContent = normalize(chunk.content());
        if (normalizedContent.isBlank()) {
            return 0;
        }

        double score = 0;
        for (String token : queryTokens) {
            if (token.length() < 2) {
                continue;
            }
            if (normalizedContent.contains(token)) {
                score += token.length() >= 5 ? 2.0 : 1.0;
            }
        }

        String normalizedQuery = normalize(rawQuery);
        if (!normalizedQuery.isBlank() && normalizedContent.contains(normalizedQuery)) {
            score += 4.0;
        }

        if (normalize(chunk.source()).contains(normalizedQuery) && !normalizedQuery.isBlank()) {
            score += 2.0;
        }

        return score;
    }

    private Set<String> tokenize(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return Set.of();
        }

        String[] rawTokens = TOKEN_SPLIT.split(normalized);
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : rawTokens) {
            if (token != null && token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return normalized.replace('đ', 'd').replace('Đ', 'd');
    }

    private record DocumentChunk(String source, String content) {
    }

    private record ScoredChunk(DocumentChunk chunk, double score) {
    }
}
