package dev.twelvetimes.vector.api.validation;

import dev.twelvetimes.vector.api.contract.ApiModels.SubmitDocumentRequest;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.config.VectorProperties;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** 只做有界、轻量的输入校验，不在接收线程调用CPU向量算法。 */
@Component
public final class RequestValidator {
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private final VectorProperties properties;

    public RequestValidator(VectorProperties properties) {
        this.properties = properties;
    }

    public SubmitDocumentRequest submission(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw BusinessException.invalid("body", "正文必须是JSON对象");
        }
        String id = requiredString(body, "documentId");
        String text = requiredString(body, "text");
        JsonNode channel = body.get("channel");
        if (channel != null && !channel.isNull() && !channel.isString()) {
            throw BusinessException.invalid("channel", "渠道必须是字符串或null");
        }
        return submission(new SubmitDocumentRequest(id, text,
                channel == null || channel.isNull() ? null : channel.asString()));
    }

    public SubmitDocumentRequest submission(SubmitDocumentRequest request) {
        if (request == null) {
            throw BusinessException.invalid("body", "正文不能为空");
        }
        documentId(request.documentId());
        text(request.text());
        String channel = channel(request.channel());
        return new SubmitDocumentRequest(request.documentId(), request.text(), channel == null ? "default" : channel);
    }

    public String documentId(String value) {
        if (value == null || !ID.matcher(value).matches()) {
            throw BusinessException.invalid("documentId", "文档ID格式非法");
        }
        return value;
    }

    public String taskId(String value) {
        try {
            if (value == null || !UUID.fromString(value).toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException();
            }
            return value.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.invalid("taskId", "任务ID必须是完整UUID");
        }
    }

    /** 原文与最终规范化文本分别检查，拒绝孤立代理，避免UTF-8替换隐藏不同输入。 */
    public void text(String value) {
        validUtf16(value, "text");
        checkLength(value, properties.maxTextCodePoints(), "text");
        String folded = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        int count = 0;
        boolean hasText = false;
        boolean pendingSpace = false;
        for (int offset = 0; offset < folded.length();) {
            int cp = folded.codePointAt(offset);
            offset += Character.charCount(cp);
            if (whitespace(cp)) {
                pendingSpace = hasText;
            } else {
                count += pendingSpace ? 2 : 1;
                pendingSpace = false;
                hasText = true;
                if (count > properties.maxTextCodePoints()) {
                    throw BusinessException.invalid("text", "规范化文本超过长度限制");
                }
            }
        }
    }

    public String channel(String value) {
        if (value == null) {
            return null;
        }
        validUtf16(value, "channel");
        int begin = 0;
        int end = value.length();
        while (begin < end && whitespace(value.codePointAt(begin))) {
            begin += Character.charCount(value.codePointAt(begin));
        }
        while (end > begin && whitespace(value.codePointBefore(end))) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        String trimmed = value.substring(begin, end);
        if (trimmed.isEmpty()) {
            throw BusinessException.invalid("channel", "显式渠道不能为空白");
        }
        checkLength(trimmed, 64, "channel");
        return trimmed;
    }

    public int page(String value) {
        return integer(value, 0, 0, Integer.MAX_VALUE, "page");
    }

    public int size(String value) {
        return integer(value, 20, 1, properties.maxPageSize(), "size");
    }

    public Boolean invalidated(String value) {
        if (value == null) { return null; }
        if (value.equals("true")) { return true; }
        if (value.equals("false")) { return false; }
        throw BusinessException.invalid("invalidated", "失效筛选必须是true或false");
    }

    private int integer(String value, int defaultValue, int min, int max, String field) {
        if (value == null) { return defaultValue; }
        try {
            if (!value.matches("[0-9]+")) { throw new NumberFormatException(); }
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) { throw new NumberFormatException(); }
            return parsed;
        } catch (NumberFormatException exception) {
            throw BusinessException.invalid(field, "分页参数非法或溢出");
        }
    }

    private static String requiredString(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isString()) {
            throw BusinessException.invalid(field, "字段必须是非null字符串");
        }
        return value.asString();
    }

    private static boolean whitespace(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp);
    }

    private static void checkLength(String value, int maximum, String field) {
        if (value.codePointCount(0, value.length()) > maximum) {
            throw BusinessException.invalid(field, "字段超过code point长度限制");
        }
    }

    private static void validUtf16(String value, String field) {
        if (value == null) { throw BusinessException.invalid(field, "字段不能为null"); }
        for (int index = 0; index < value.length(); index++) {
            char unit = value.charAt(index);
            if (Character.isHighSurrogate(unit)) {
                if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index))) {
                    throw BusinessException.invalid(field, "字段包含非法UTF-16");
                }
            } else if (Character.isLowSurrogate(unit)) {
                throw BusinessException.invalid(field, "字段包含非法UTF-16");
            }
        }
    }
}
