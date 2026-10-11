package com.lumina.common;

import com.lumina.config.MongoCapabilities;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TxRunner {
    private final PlatformTransactionManager transactionManager;
    private final MongoCapabilities capabilities;

    public TxRunner(PlatformTransactionManager transactionManager, MongoCapabilities capabilities) {
        this.transactionManager = transactionManager;
        this.capabilities = capabilities;
    }

    public void run(Runnable work) {
        if (!capabilities.replicaSet()) {
            work.run();
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }
}
