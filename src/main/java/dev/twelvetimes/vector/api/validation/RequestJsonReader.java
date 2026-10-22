package dev.twelvetimes.vector.api.validation;

import dev.twelvetimes.vector.api.error.BusinessException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 显式要求恰好一个JSON根值，避免多段正文被默认解析器忽略。 */
@Component
public final class RequestJsonReader {
    private final ObjectMapper mapper;

    public RequestJsonReader(ObjectMapper mapper) { this.mapper = mapper; }

    public JsonNode read(byte[] bytes) {
        try (var parser = mapper.createParser(bytes)) {
            JsonNode body = mapper.readTree(parser);
            if (body == null || parser.nextToken() != null) {
                throw new IllegalArgumentException("JSON根值数量非法");
            }
            return body;
        } catch (Exception exception) {
            throw new BusinessException(400, "MALFORMED_JSON", "正文必须是单个合法JSON值");
        }
    }
}
