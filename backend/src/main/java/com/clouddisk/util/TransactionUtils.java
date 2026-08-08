package com.clouddisk.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class TransactionUtils {

    /**
     * 确保在当前 Spring 事务提交 (commit) 成功后才执行该 Runnable 操作。
     * 如果当前环境没有处于事务中，则立即同步执行。
     *
     * @param action 事务提交后执行的回调动作（如刷新 Redis 缓存、推送 ES 索引等）
     */
    public static void afterCommit(Runnable action) {
        if (action == null) return;
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
