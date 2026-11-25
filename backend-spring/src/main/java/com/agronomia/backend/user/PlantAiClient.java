package com.agronomia.backend.user;

import java.util.List;
import java.util.Map;

public interface PlantAiClient {
    String loadSkill(String name);
    String loadCategorySkill(String category);
    Map<String, Object> jsonCall(String system, String user, double temperature);
    String chatReply(List<Map<String, Object>> messages, double temperature);
}
