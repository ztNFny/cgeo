package cgeo.geocaching.connector.pgc;

import androidx.annotation.Nullable;

public final class VgpsList {

    public final int id;
    @Nullable public final String name;

    public VgpsList(final int id, @Nullable final String name) {
        this.id = id;
        this.name = name;
    }
}
