package com.agronomia.backend.ai;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class BotanyInstructions {
    private final String content;

    public BotanyInstructions(ResourceLoader resourceLoader) throws IOException {
        Resource skill = resourceLoader.getResource("classpath:skills/space-bunny-botany/SKILL.md");
        String markdown;
        try (var input = skill.getInputStream()) {
            markdown = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (markdown.startsWith("---")) {
            int end = markdown.indexOf("\n---", 3);
            if (end >= 0) markdown = markdown.substring(end + 4).stripLeading();
        }
        this.content = markdown;
    }

    public String content() { return content; }
}
