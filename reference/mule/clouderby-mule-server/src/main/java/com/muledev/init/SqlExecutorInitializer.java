package com.muledev.init;

import com.muledev.server.ClouderbySessionManager;
import com.muledev.server.SqlExecutor;
import org.springframework.beans.factory.InitializingBean;

public class SqlExecutorInitializer implements InitializingBean {

    private ClouderbySessionManager sessionManager;

    public void setSessionManager(ClouderbySessionManager mgr) {
        this.sessionManager = mgr;
    }

    @Override
    public void afterPropertiesSet() {
        SqlExecutor.setSessionManager(sessionManager);
    }
}
