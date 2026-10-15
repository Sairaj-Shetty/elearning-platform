package com.lumina.common;

import com.lumina.config.MongoCapabilities;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TxRunner {
    private final MongoTransactionManager transactionManager;
    private final MongoCapabilities capabilities;

    public TxRunner(MongoDatabaseFactory databaseFactory, MongoCapabilities capabilities) {
        this.transactionManager = new MongoTransactionManager(databaseFactory);
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