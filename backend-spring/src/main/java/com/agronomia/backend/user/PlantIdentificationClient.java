package com.agronomia.backend.user;

import java.util.List;
import java.util.Map;

public interface PlantIdentificationClient {
    Map<String, Object> identify(List<byte[]> images, String organ, String project, String lang);
}
