package cgeo.geocaching.unifiedmap.tileproviders;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.R;

import android.net.Uri;

import androidx.core.util.Pair;

import static org.oscim.map.Viewport.MIN_ZOOM_LEVEL;

public class MapilionHillshadingSource extends AbstractMapsforgeOnlineTileProvider {
    public MapilionHillshadingSource() {
        super("Mapilion Hillshading", Uri.parse("https://mapilion-vector-and-raster-map-tiles.p.rapidapi.com/rapid-api/hillshades/v2"), "/{Z}/{X}/{Y}?rapidapi-key=18666f7b94msh345838bae5e9539p10efafjsn0a3aa26ec3e5", MIN_ZOOM_LEVEL, 12, new Pair<>("© <a href=\"https://mapilion.com/attribution\">Mapilion</a>", false));
    }
}
