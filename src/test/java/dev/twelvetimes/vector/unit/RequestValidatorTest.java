package dev.twelvetimes.vector.unit;

import dev.twelvetimes.vector.api.contract.ApiModels.SubmitDocumentRequest;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.api.validation.RequestValidator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestValidatorTest {
    private final RequestValidator validator = new RequestValidator(B2TestSupport.properties(3));
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 空文本合法且渠道缺省或null使用default() throws Exception {
        for (String body : new String[] {"{\"documentId\":\"a\",\"text\":\"\"}",
                "{\"documentId\":\"a\",\"text\":\"\",\"channel\":null}"}) {
            SubmitDocumentRequest result = validator.submission(mapper.readTree(body));
            assertEquals("", result.text());
            assertEquals("default", result.channel());
        }
        assertNull(validator.channel(null));
    }

    @Test
    void JSON字符串不接受框架隐式类型转换() throws Exception {
        for (String body : new String[] {"null", "[]", "{}", "{\"documentId\":1,\"text\":\"\"}",
                "{\"documentId\":\"a\",\"text\":null}", "{\"documentId\":\"a\",\"text\":true}",
                "{\"documentId\":\"a\",\"text\":\"\",\"channel\":7}"}) {
            var json = mapper.readTree(body);
            assertThrows(BusinessException.class, () -> validator.submission(json));
        }
    }

    @Test
    void 轻量检查原文与规范化限额及非法代理() {
        validator.text("🙂字a");
        validator.text("\ufb03");
        assertThrows(BusinessException.class, () -> validator.text("🙂字aa"));
        assertThrows(BusinessException.class, () -> validator.text("\ufb03a"));
        assertThrows(BusinessException.class, () -> validator.text("\ud800"));
        assertThrows(BusinessException.class, () -> validator.text("\udc00"));
        assertThrows(BusinessException.class, () -> validator.text(null));
    }

    @Test
    void 渠道Unicode裁剪和补充字符上限不改大小写() {
        assertEquals("FAQ", validator.channel("\u00a0FAQ\u3000"));
        assertEquals("🙂".repeat(64), validator.channel("🙂".repeat(64)));
        assertThrows(BusinessException.class, () -> validator.channel("🙂".repeat(65)));
        assertThrows(BusinessException.class, () -> validator.channel("\u00a0\u3000"));
        assertThrows(BusinessException.class, () -> validator.channel(""));
        assertThrows(BusinessException.class, () -> validator.channel("\ud800"));
    }

    @Test
    void ID与分页布尔必须符合冻结格式() {
        assertEquals("A._-0", validator.documentId("A._-0"));
        assertThrows(BusinessException.class, () -> validator.documentId("a/b"));
        assertThrows(BusinessException.class, () -> validator.documentId(" a"));
        assertThrows(BusinessException.class, () -> validator.taskId("1-1-1-1-1"));
        assertEquals(0, validator.page(null));
        assertEquals(20, validator.size(null));
        for (String value : new String[] {"", "-1", "1.0", "2147483648", " 1"}) {
            assertThrows(BusinessException.class, () -> validator.page(value));
        }
        assertThrows(BusinessException.class, () -> validator.size("101"));
        assertThrows(BusinessException.class, () -> validator.invalidated("TRUE"));
        assertEquals(true, validator.invalidated("true"));
    }

    @Test
    void 搜索topK只接受范围内JSON整数且渠道缺省是全部() throws Exception {
        for (String value : new String[] {"null", "0", "-1", "101", "1.0", "\"5\"", "true", "[]", "2147483648"}) {
            var body = mapper.readTree("{\"text\":\"\",\"topK\":" + value + "}");
            assertThrows(BusinessException.class, () -> validator.search(body), value);
        }
        assertThrows(BusinessException.class, () -> validator.search(mapper.readTree("{\"text\":\"\"}")));
        assertThrows(BusinessException.class, () -> validator.search(mapper.readTree("{\"text\":null,\"topK\":1}")));
        assertThrows(BusinessException.class, () -> validator.search(mapper.readTree("{\"text\":\"\",\"topK\":1,\"channel\":\" \"}")));
        assertNull(validator.search(mapper.readTree("{\"text\":\"\",\"topK\":1}")).channel());
        assertNull(validator.search(mapper.readTree("{\"text\":\"\",\"topK\":100,\"channel\":null}")).channel());
        assertEquals("FAQ", validator.search(mapper.readTree("{\"text\":\"🙂\",\"topK\":5,\"channel\":\" FAQ \"}")).channel());
    }
}
