package cgeo.geocaching.unifiedmap.tileproviders;

import static org.oscim.map.Viewport.MIN_ZOOM_LEVEL;

import android.net.Uri;

import androidx.core.util.Pair;

public class MapToolkitHillshadingSource extends AbstractMapsforgeOnlineTileProvider {
    public MapToolkitHillshadingSource() {
        super("MapToolkit Hillshading", Uri.parse("https://maptoolkit.p.rapidapi.com/tiles"), "/{Z}/{X}/{Y}/hillshading.png?rapidapi-key=6943cc8345mshc667ac743c7da40p10028bjsn9dab21a7cf07", MIN_ZOOM_LEVEL, 14, new Pair<>("© <a href='https://www.maptoolkit.com' target='_blank'>Maptoolkit</a>", false));
    }
}
