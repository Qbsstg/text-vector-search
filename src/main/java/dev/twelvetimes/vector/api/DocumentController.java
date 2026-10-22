package dev.twelvetimes.vector.api;

import dev.twelvetimes.vector.api.contract.ApiModels.DocumentDetail;
import dev.twelvetimes.vector.api.contract.ApiModels.DocumentPage;
import dev.twelvetimes.vector.api.contract.ApiModels.Invalidation;
import dev.twelvetimes.vector.api.contract.ApiModels.Submission;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.api.validation.RequestJsonReader;
import dev.twelvetimes.vector.api.validation.RequestValidator;
import dev.twelvetimes.vector.document.DocumentAdmissionService;
import dev.twelvetimes.vector.document.DocumentQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP层只处理协议边界，真实提交与字段级更新由服务负责。 */
@RestController
@RequestMapping("/api/documents")
public final class DocumentController {
    private final DocumentAdmissionService admission;
    private final DocumentQueryService query;
    private final RequestValidator validator;
    private final RequestJsonReader reader;

    public DocumentController(DocumentAdmissionService admission, DocumentQueryService query,
                               RequestValidator validator, RequestJsonReader reader) {
        this.admission = admission;
        this.query = query;
        this.validator = validator;
        this.reader = reader;
    }

    @PostMapping
    public ResponseEntity<Submission> submit(HttpServletRequest request) throws IOException {
        return ResponseEntity.accepted().body(admission.submit(validator.submission(
                reader.read(request.getInputStream().readAllBytes()))));
    }

    @GetMapping
    public DocumentPage list(HttpServletRequest request) {
        return query.list(validator.page(parameter(request, "page")), validator.size(parameter(request, "size")),
                parameter(request, "channel"), validator.invalidated(parameter(request, "invalidated")));
    }

    @GetMapping("/{documentId}")
    public DocumentDetail detail(@PathVariable String documentId) { return query.detail(documentId); }

    @PutMapping("/{documentId}/invalidation")
    public Invalidation invalidate(@PathVariable String documentId) { return query.invalidate(documentId); }

    private static String parameter(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        if (values != null && values.length != 1) { throw BusinessException.invalid(name, "参数不能重复"); }
        return values == null ? null : values[0];
    }
}
