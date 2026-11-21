package com.longzhong.jinguan.user;

import com.longzhong.jinguan.security.JwtService;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** refresh 轮换与吊销:落库 jti、原子消费、并发宽限、重用检测、全量吊销。 */
@Service
public class RefreshTokenService {

  private static final int MAX_CHAIN_HOPS = 10;

  private final RefreshSessionRepository sessions;
  private final JwtService jwtService;
  private final Duration grace;

  public RefreshTokenService(
      RefreshSessionRepository sessions,
      JwtService jwtService,
      @Value("${jinguan.security.refresh-grace-seconds:30}") long graceSeconds) {
    this.sessions = sessions;
    this.jwtService = jwtService;
    this.grace = Duration.ofSeconds(graceSeconds);
  }

  /** 记录一枚新签发的 refresh 票。 */
  @Transactional
  public void record(String jti, String subject, Instant expiresAt) {
    sessions.save(RefreshSession.create(jti, subject, expiresAt));
  }

  /**
   * 消费旧票并轮换。未落库票 fail-closed;宽限窗口内重复提交(并发续期、客户端重试)重签同一后继票;超窗重复提交视为重放,吊销该用户全部会话。返回可下发的 refresh
   * 票,拒绝时为 null。
   */
  @Transactional
  public String rotate(JwtService.LoginUser presented) {
    Instant now = Instant.now();
    RefreshSession session = sessions.findById(presented.jti()).orElse(null);
    if (session == null) {
      return null;
    }
    if (!session.isConsumed() && sessions.consume(session.getJti(), now) == 1) {
      return rotateToSuccessor(presented, now);
    }
    // 已消费或并发败者:重读墓碑后走宽限判定。
    return replay(sessions.findById(session.getJti()).orElse(null), presented, now);
  }

  /** 吊销该用户全部 refresh 会话(登出/改密)。 */
  @Transactional
  public void revokeAll(String subject) {
    sessions.revokeAllByUser(subject);
  }

  private String rotateToSuccessor(JwtService.LoginUser presented, Instant now) {
    JwtService.RefreshTicket successor =
        jwtService.issueRefreshTicket(presented.subject(), presented.username(), presented.role());
    sessions.save(
        RefreshSession.create(successor.jti(), presented.subject(), successor.expiresAt()));
    // 消费清空了持久化上下文,重载后写入墓碑链接(同一事务,与消费一并提交)。
    sessions.findById(presented.jti()).orElseThrow().markRotated(now, successor.jti());
    return successor.token();
  }

  private String replay(RefreshSession session, JwtService.LoginUser presented, Instant now) {
    if (session != null
        && session.getRotatedAt() != null
        && !now.isAfter(session.getRotatedAt().plus(grace))) {
      return liveSuccessor(session.getReplacedJti(), presented, now);
    }
    sessions.revokeAllByUser(presented.subject());
    return null;
  }

  /** 沿墓碑链接找到仍活跃的后继票并重签:宽限内的并发续期与客户端重试因此落到同一会话。 链断(后继已吊销、已过期或超出跳数)则吊销全家族并拒绝,宽限不得复活已吊销会话。 */
  private String liveSuccessor(String jti, JwtService.LoginUser presented, Instant now) {
    String current = jti;
    for (int hop = 0; hop < MAX_CHAIN_HOPS && current != null; hop++) {
      RefreshSession row = sessions.findById(current).orElse(null);
      if (row == null || !now.isBefore(row.getExpiresAt())) {
        break;
      }
      if (!row.isConsumed()) {
        return jwtService.reissueRefresh(
            presented.subject(),
            presented.username(),
            presented.role(),
            row.getJti(),
            row.getExpiresAt());
      }
      if (row.getRotatedAt() == null) {
        break;
      }
      current = row.getReplacedJti();
    }
    sessions.revokeAllByUser(presented.subject());
    return null;
  }
}
