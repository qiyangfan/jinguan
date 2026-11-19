package com.longzhong.jinguan.wallet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 按用户串行执行资金操作；外层事务存在时在提交/回滚完成后释放锁。 */
@Component
class UserLockRegistry {

  private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

  <T> T run(long userId, Supplier<T> work) {
    ReentrantLock lock = locks.computeIfAbsent(userId, k -> new ReentrantLock());
    lock.lock();
    boolean deferred = false;
    try {
      if (TransactionSynchronizationManager.isActualTransactionActive()
          && TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
              @Override
              public void afterCompletion(int status) {
                lock.unlock();
              }
            });
        deferred = true;
      }
      return work.get();
    } finally {
      if (!deferred) {
        lock.unlock();
      }
    }
  }
}
