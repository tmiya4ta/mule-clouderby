package com.muledev.init;

import com.muledev.server.ClouderbySessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Seeds the embedded Derby database on startup: every dataset profile gets its
 * own schema and all of them are loaded (see {@link ProfileManager}), so both
 * datasets are queryable from the moment the app starts.
 *
 * <p>This only picks which schema a new session lands on by default: the
 * {@code defaultProfile} property below, else the {@code db.init.profile} system
 * property, else {@code defaultProfile} in {@code /init/profiles.json}.
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

        // Every profile gets its own schema and they are all seeded, so both
        // datasets stay queryable and switching never destroys anything.
        Map<String, Object> r = ProfileManager.ensureAllSeeded();
        LOG.info("[DB-INIT] Seeding complete, ok={}", r.get("ok"));

        String profile = resolveDefaultProfile();
        if (profile == null) {
            LOG.error("[DB-INIT] No default profile (check /init/profiles.json)");
            return;
        }
        LOG.info("[DB-INIT] Default schema for new sessions: {}",
                 ProfileManager.schemaOf(profile));
    }

    /**
     * The Spring value may arrive as an unresolved {@code ${...}} placeholder
     * depending on how the app is launched, so treat that as "not set".
     */
    private String resolveDefaultProfile() {
        String p = defaultProfile;
        String source = "config file (db.init.profile, via the Spring property)";

        if (p == null || p.trim().isEmpty() || p.startsWith("${")) {
            p = System.getProperty("db.init.profile");
            source = "system property (db.init.profile)";
        }
        if (p == null || p.trim().isEmpty()) {
            p = ProfileManager.catalogDefaultProfileId();
            source = "profiles.json (defaultProfile)";
        }
        p = p == null ? null : p.trim();
        ProfileManager.recordStartupResolution(p, source);
        LOG.info("[DB-INIT] default profile '{}' resolved from {}", p, source);
        return p;
    }
}
