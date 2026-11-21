package com.longzhong.jinguan.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 运维 break-glass 入口：凭据走请求头，不放 URL；默认关闭。 */
@RestController
public class BreakGlassController {

  static final String TOKEN_HEADER = "X-Break-Glass-Token";

  private final BreakGlassService breakGlass;

  public BreakGlassController(BreakGlassService breakGlass) {
    this.breakGlass = breakGlass;
  }

  @PostMapping("/auth/break-glass")
  public ResponseEntity<Map<String, String>> issue(
      @RequestHeader(name = TOKEN_HEADER, required = false) String token,
      HttpServletRequest request) {
    if (!breakGlass.enabled()) {
      return ResponseEntity.notFound().build();
    }
    try {
      String access = breakGlass.issue(token, request.getRemoteAddr());
      return ResponseEntity.ok(Map.of("token", access));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
  }

  @PostMapping("/auth/break-glass/enabled")
  public Map<String, Boolean> enabled() {
    return Map.of("enabled", breakGlass.enabled());
  }
}
