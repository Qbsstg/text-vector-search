package dev.twelvetimes.vector.api;

import dev.twelvetimes.vector.api.contract.ApiModels.SearchResponse;
import dev.twelvetimes.vector.api.validation.RequestJsonReader;
import dev.twelvetimes.vector.api.validation.RequestValidator;
import dev.twelvetimes.vector.search.SearchService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 复用实际字节限制、同源与单根JSON边界；只有计数服务真实提交后才返回成功。 */
@RestController
@RequestMapping("/api/search")
public final class SearchController {
    private final SearchService search;
    private final RequestValidator validator;
    private final RequestJsonReader reader;

    public SearchController(SearchService search, RequestValidator validator, RequestJsonReader reader) {
        this.search = search;
        this.validator = validator;
        this.reader = reader;
    }

    @PostMapping
    public SearchResponse search(HttpServletRequest request) throws IOException {
        // 原始流已经由ApiRequestFilter最多读取上限+1字节，此处只读取有界缓存正文。
        return search.search(validator.search(reader.read(request.getInputStream().readAllBytes())));
    }
}
