package com.lumina.config;

import org.springframework.stereotype.Component;

@Component
public class MongoCapabilities {
    private volatile boolean replicaSet;

    public boolean replicaSet() {
        return replicaSet;
    }

    public void setReplicaSet(boolean replicaSet) {
        this.replicaSet = replicaSet;
    }
}
