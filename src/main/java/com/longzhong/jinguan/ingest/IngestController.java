package com.longzhong.jinguan.ingest;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 计量接收契约端点(入站适配器):muniu IngestSink 推送,Bearer 静态 token 认证。 */
@RestController
@Validated
public class IngestController {

  public record Envelope(@Valid List<IngestUsageRow> rows) {}

  private final IngestService ingest;
  private final String expectedToken;

  public IngestController(
      IngestService ingest, @Value("${jinguan.ingest.token}") String expectedToken) {
    this.ingest = ingest;
    this.expectedToken = expectedToken;
  }

  @PostMapping("/ingest/usage")
  public Object accept(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestBody Envelope envelope) {
    if (authorization == null || !authorization.equals("Bearer " + expectedToken)) {
      return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "ingest token 无效");
    }
    return ingest.accept(envelope.rows() == null ? List.of() : envelope.rows());
  }
}
