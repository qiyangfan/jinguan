package com.longzhong.jinguan.pricing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理面:价目运营(改价即时生效于下次 muniu 拉取,数据面零重启)。 */
@RestController
@RequestMapping("/admin/pricing")
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class PricingAdminController {

  public record TimeWindowReq(
      @NotBlank String window,
      @NotBlank @Pattern(regexp = "^\\d+(\\.\\d{1,4})?$") String multiplier) {}

  public record UpsertReq(
      @NotBlank String currency,
      @PositiveOrZero long inputMicros,
      @PositiveOrZero long cachedInputMicros,
      @PositiveOrZero long outputMicros,
      String timezone,
      List<TimeWindowReq> timeWindows) {}

  private final PricingService pricing;

  public PricingAdminController(PricingService pricing) {
    this.pricing = pricing;
  }

  @GetMapping
  public List<PricingService.PricingView> list() {
    return pricing.list();
  }

  @PutMapping("/{alias}")
  public void upsert(@PathVariable String alias, @RequestBody @Validated UpsertReq req) {
    List<PricingService.TimeWindow> windows =
        req.timeWindows() == null
            ? List.of()
            : req.timeWindows().stream()
                .map(w -> new PricingService.TimeWindow(w.window(), w.multiplier()))
                .toList();
    pricing.upsert(
        alias,
        new PricingService.UpsertCommand(
            req.currency(),
            req.inputMicros(),
            req.cachedInputMicros(),
            req.outputMicros(),
            req.timezone(),
            windows));
  }

  @DeleteMapping("/{alias}")
  public void delete(@PathVariable String alias) {
    pricing.delete(alias);
  }
}
