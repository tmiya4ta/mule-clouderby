package com.muledev.init;

import com.muledev.server.ClouderbySessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Seeds the embedded Derby database on startup by applying a dataset profile
 * (see {@link ProfileManager}).
 *
 * <p>Which profile: the {@code defaultProfile} property below, else the
 * {@code defaultProfile} recorded in {@code /init/profiles.json}. A profile that
 * was applied earlier through the API/UI wins over both, so a restart keeps
 * whatever the operator last selected.
 */
public class DatabaseInitializer implements InitializingBean {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseInitializer.class);

    private DataSource dataSource;
    private ClouderbySessionManager sessionManager;
    private String defaultProfile;

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void setSessionManager(ClouderbySessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public void setDefaultProfile(String defaultProfile) {
        this.defaultProfile = defaultProfile;
    }

    @Override
    public void afterPropertiesSet() {
        ProfileManager.configure(dataSource, sessionManager);
        new Thread(this::init, "db-init").start();
        LOG.info("[DB-INIT] Scheduled in background thread");
    }

    private void init() {
        // Give the Mule runtime time to finish wiring the shared-library
        // classloader that Derby is loaded from.
        try { Thread.sleep(5000); } catch (InterruptedException e) { return; }

        // Set thread context classloader to the DataSource's classloader (shared lib)
        // to avoid Derby XJ040.C classloader conflict in Mule
        Thread.currentThread().setContextClassLoader(dataSource.getClass().getClassLoader());

        String already = ProfileManager.currentProfile();
        if (already != null) {
            LOG.info("[DB-INIT] Profile '{}' already applied; leaving data as is", already);
            return;
        }

        String profile = resolveProfile();
        if (profile == null) {
            LOG.error("[DB-INIT] No profile to apply (check /init/profiles.json)");
            return;
        }

        try {
            LOG.info("[DB-INIT] Applying profile '{}' ...", profile);
            Map<String, Object> r = ProfileManager.applyInternal(profile);
            LOG.info("[DB-INIT] Profile '{}' applied: {} rows, ok={}",
                     profile, r.get("totalRows"), r.get("ok"));
            if (Boolean.FALSE.equals(r.get("ok"))) {
                LOG.error("[DB-INIT] Errors during seed: {}", r.get("errors"));
            }
        } catch (Exception e) {
            LOG.error("[DB-INIT] Error applying profile '{}': {}", profile, e.getMessage(), e);
        }
    }

    /**
     * The Spring value may arrive as an unresolved {@code ${...}} placeholder
     * depending on how the app is launched, so treat that as "not set".
     */
    private String resolveProfile() {
        String p = defaultProfile;
        String source = "config file (db.init.profile, via the Spring property)";

        if (p == null || p.trim().isEmpty() || p.startsWith("${")) {
            p = System.getProperty("db.init.profile");
            source = "system property (db.init.profile)";
        }
        if (p == null || p.trim().isEmpty()) {
            p = ProfileManager.defaultProfileId();
            source = "profiles.json (defaultProfile)";
        }
        p = p == null ? null : p.trim();
        ProfileManager.recordStartupResolution(p, source);
        LOG.info("[DB-INIT] startup profile '{}' resolved from {}", p, source);
        return p;
    }
}
