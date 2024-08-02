package cgeo.geocaching.unifiedmap.tileproviders;

import cgeo.geocaching.storage.LocalStorage;
import cgeo.geocaching.unifiedmap.LayerHelper;
import cgeo.geocaching.unifiedmap.mapsforgevtm.MapsforgeVtmFragment;

import android.net.Uri;

import androidx.core.util.Pair;

import java.io.File;
import java.util.HashMap;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import org.oscim.layers.tile.bitmap.BitmapTileLayer;
import org.oscim.map.Map;
import org.oscim.tiling.source.OkHttpEngine;
import org.oscim.tiling.source.bitmap.BitmapTileSource;

class AbstractMapsforgeOnlineTileProvider extends AbstractMapsforgeTileProvider {

    private final String tilePath;
    private final HashMap<String, String> header = new HashMap<>();

    AbstractMapsforgeOnlineTileProvider(final String name, final Uri uri, final String tilePath, final int zoomMin, final int zoomMax, final Pair<String, Boolean> mapAttribution) {
        super(name, uri, zoomMin, zoomMax, mapAttribution);
        this.tilePath = tilePath;
        this.header.put("User-Agent", "cgeo-android");
    }

    @Override
    public void addTileLayer(final MapsforgeVtmFragment fragment, final Map map) {
        fragment.addLayer(LayerHelper.ZINDEX_BASEMAP, getBitmapTileLayer(map));
    }

    public BitmapTileLayer getBitmapTileLayer(final Map map) {
        final OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
        final Cache cache = new Cache(new File(LocalStorage.getExternalPrivateCgeoDirectory(), "tiles"), 20 * 1024 * 1024);
        httpBuilder.cache(cache);
        final BitmapTileSource tileSource = BitmapTileSource.builder()
                .url(mapUri.toString())
                .tilePath(tilePath)
                .zoomMax(zoomMax)
                .zoomMin(zoomMin)
                .build();
        tileSource.setHttpEngine(new OkHttpEngine.OkHttpFactory(httpBuilder));
        tileSource.setHttpRequestHeaders(header);
        return new BitmapTileLayer(map, tileSource);
    }

    public void addHeader(final String name, final String value) {
        this.header.put(name, value);
    }

}
