package cgeo.geocaching.connector.pgc;

import cgeo.geocaching.settings.Settings;

public final class ProjectGCConnector {

    public static final ProjectGCConnector INSTANCE = new ProjectGCConnector();
    public static final String HOST = "project-gc.com";
    public static final String URL = "https://www." + HOST;

    private ProjectGCConnector() {
        // singleton
    }

    public boolean isActive() {
        return Settings.isProjectGCConnectorActive();
    }

    public boolean hasAuthorization() {
        return Settings.hasProjectGCAuthorization();
    }
}
