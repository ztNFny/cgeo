package cgeo.geocaching.utils;

import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.security.auth.login.LoginException;

import cgeo.geocaching.CacheDetailActivity;
import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.R;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.enumerations.WaypointType;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.GeopointFormatter;
import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.models.Waypoint;
import cgeo.geocaching.network.Network;
import cgeo.geocaching.network.Parameters;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.storage.DataStore;
import cgeo.geocaching.ui.dialog.Dialogs;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ZtnfnyGcDb {
    /**
     * DB
     */

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final OkHttpClient OK_HTTP_CLIENT = getNewHttpClient();
    private static OkHttpClient getNewHttpClient() {
        final OkHttpClient.Builder client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true);

        return client.build();
    }
    private static GcDbTransaction gcDbTransaction;

    private static void doUpload() {
        callGcDbApi("upload");
    }

    private static void doDownload() {
        final Geocache cache = gcDbTransaction.getCache();
        final GcDbInfo dbInfo = gcDbTransaction.getDbInfo();

        if (gcDbTransaction.coordsMatch()) {
            setStatus(gcDbTransaction.getStatus(), R.drawable.marker_note, "Updated Note");
        } else {
            setStatus(gcDbTransaction.getStatus(), R.drawable.marker_usermodifiedcoords, "Final & Note updated");
        }

        // Delete old DB waypoints
        List<Waypoint> waypointList = cache.getWaypoints();
        for (Waypoint waypoint : waypointList) {
            if ("DB".equals(waypoint.getName())) {
                cache.deleteWaypoint(waypoint);
            }
        }

        // Create "Original" waypoint
        if (!cache.hasUserModifiedCoords()) {
            final Waypoint origWaypoint = new Waypoint(CgeoApplication.getInstance().getString(R.string.cache_coordinates_original), WaypointType.ORIGINAL, false);
            origWaypoint.setCoords(cache.getCoords());
            cache.addOrChangeWaypoint(origWaypoint, false);
            cache.setUserModifiedCoords(true);
        }

        cache.setPersonalNote(dbInfo.getUsernote());

        final Waypoint wp = new Waypoint("DB", WaypointType.FINAL, true);
        wp.setCoords(dbInfo.getGeopoint());
        cache.addOrChangeWaypoint(wp, true);
        cache.setCoords(dbInfo.getGeopoint());

        saveAndRefresh(cache);
    }

    public static void syncDb(final Button btn, final Geocache cache) {
        final TextView status = (TextView) ((ViewGroup)btn.getParent()).getChildAt(0);
        gcDbTransaction = new GcDbTransaction(cache, status, btn.getContext());
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final TextView status = gcDbTransaction.getStatus();

                // get DB
                final GcDbInfo dbInfo;
                final GcDbInfo[] dbInfos = callGcDbApi("download");
                if (dbInfos == null || dbInfos[0] == null) {
                    dbInfo = null;
                } else {
                    dbInfo = dbInfos[0];
                }
                gcDbTransaction.setDbInfo(dbInfo);
                if ((cache.hasUserModifiedCoords() || gcDbTransaction.hasCacheHasPersonalNote()) && dbInfo != null) {
                    // local and remote set
                    if (gcDbTransaction.coordsMatch() && gcDbTransaction.getCachePersonalNote().equals(dbInfo.getUsernote())) {
                        setStatus(status, R.drawable.marker_disable, "Coordinates & Note already match");
                        return;
                    }
                    showDiffDialog();
                } else if (!gcDbTransaction.hasCacheHasPersonalNote() && cache.hasUserModifiedCoords()) {
                    showUploadConfirmationDialog("Usernote is empty. Upload to DB?");
                } else if (gcDbTransaction.hasCacheHasPersonalNote() && !cache.hasUserModifiedCoords()) {
                    showUploadConfirmationDialog("No corrected coordinates, only Note. Upload to DB?");
                } else if (!gcDbTransaction.hasCacheHasPersonalNote() && !cache.hasUserModifiedCoords() && dbInfo != null) {
                    doDownload();
                } else if (gcDbTransaction.hasCacheHasPersonalNote() && cache.hasUserModifiedCoords()) {
                    showUploadConfirmationDialog("");
                } else {
                    Toast.makeText(v.getContext(), "No results", Toast.LENGTH_LONG);
                }
            }
        });
    }

    public static void getDbHistory(final Button btn, final Geocache cache) {
        final TextView status = (TextView) ((ViewGroup)btn.getParent()).getChildAt(0);
        gcDbTransaction = new GcDbTransaction(cache, status, btn.getContext());
        btn.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onClick(View v) {
                // get DB
                final GcDbInfo[] dbInfos = callGcDbApi("history");

                if (dbInfos == null || dbInfos.length == 0) return;

                // Delete old DB waypoints
                List<Waypoint> waypointList = cache.getWaypoints();
                for (Waypoint waypoint : waypointList) {
                    if (waypoint.getName().startsWith("DB v")) {
                        cache.deleteWaypoint(waypoint);
                    }
                }

                for (GcDbInfo dbInfo : dbInfos) {
                    final Waypoint wp = new Waypoint("DB v" + dbInfo.version + " (" + dbInfo.datetime + ")", WaypointType.FINAL, true);
                    wp.setNote(dbInfo.getUsernote());
                    wp.setCoords(dbInfo.getGeopoint());
                    cache.addOrChangeWaypoint(wp, true);
                }

                saveAndRefresh(cache);

                setStatus(status, R.drawable.marker_visited, dbInfos.length + " history waypoints added. Hold this button to delete them again.");
            }
        });
        btn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                int delCount = 0;

                // Delete old DB waypoints
                List<Waypoint> waypointList = cache.getWaypoints();
                for (Waypoint waypoint : waypointList) {
                    if (waypoint.getName().startsWith("DB v")) {
                        cache.deleteWaypoint(waypoint);
                        delCount++;
                    }
                }

                saveAndRefresh(cache);
                setStatus(status, R.drawable.marker_visited, delCount + " history waypoints removed.");

                // consume the event as not to fire click-listener
                return true;
            }
        });
    }

    private static void showDiffDialog() {
        final String localCoords = gcDbTransaction.getCache().getCoords().format(GeopointFormatter.Format.LAT_LON_DECMINUTE_RAW);
        final String dbCoords = gcDbTransaction.getDbInfo().getGeopoint().format(GeopointFormatter.Format.LAT_LON_DECMINUTE_RAW);
        final String localTxt = gcDbTransaction.getCachePersonalNote();
        final String dbTxt = gcDbTransaction.getDbInfo().getUsernote();

        final List<String> dbList = Arrays.asList(dbTxt.split("\n+"));
        final List<String> localList = Arrays.asList(localTxt.split("\n+"));

        final List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff("DB", "Local", dbList, DiffUtils.diff(dbList, localList), 1000);

        final SpannableStringBuilder ssb = new SpannableStringBuilder();
        SpannableString s;
        final int dbColor = gcDbTransaction.getCtx().getResources().getColor(R.color.gcdb_remote);
        final int localColor = gcDbTransaction.getCtx().getResources().getColor(R.color.gcdb_local);

        if (localCoords.equals(dbCoords)) {
            s = new SpannableString(localCoords);
            ssb.append(s);
        } else {
            s = new SpannableString(localCoords);
            s.setSpan(new BackgroundColorSpan(localColor), 0, localCoords.length(), 0);
            ssb.append(s);
            ssb.append("\n");
            s = new SpannableString(dbCoords);
            s.setSpan(new BackgroundColorSpan(dbColor), 0, dbCoords.length(), 0);
            ssb.append(s);
        }
        ssb.append("\n");

        String rowText;
        String rowChar;

        // remove lines with "filenames"
        if (unifiedDiff.size() > 1) {
            unifiedDiff.remove(0);
            unifiedDiff.remove(0);
        }
        if (unifiedDiff.size() == 0) {
            s = new SpannableString(localTxt);
            ssb.append(s);
        } else {
            for (String row : unifiedDiff) {
                rowText = row.substring(1);
                rowChar = row.substring(0, 1);
                if (rowChar.equals(" ")) {
                    s = new SpannableString(rowText);
                    ssb.append(s);
                } else if (rowChar.equals("-")) {
                    s = new SpannableString(rowText);
                    s.setSpan(new BackgroundColorSpan(dbColor), 0, rowText.length(), 0);
                    ssb.append(s);
                } else if (rowChar.equals("+")) {
                    s = new SpannableString(rowText);
                    s.setSpan(new BackgroundColorSpan(localColor), 0, rowText.length(), 0);
                    ssb.append(s);
                }
                ssb.append("\n");
            }
        }

        final View v = LayoutInflater.from(gcDbTransaction.getCtx()).inflate(R.layout.gcdb_diffdialog, null);
        ((TextView) v.findViewById(R.id.gcdb_diff)).setText(ssb, TextView.BufferType.SPANNABLE);
        Dialog dialog = Dialogs.bottomSheetDialogWithActionbar(gcDbTransaction.getCtx(), v, R.string.gcdb_diff_title);
        dialog.show();
        final Button downloadButton = v.findViewById(R.id.gcdb_download_remote);
        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doDownload();
                dialog.dismiss();
            }
        });
        final Button uploadButton = v.findViewById(R.id.gcdb_upload_local);
        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doUpload();
                dialog.dismiss();
            }
        });
    }

    private static void showUploadConfirmationDialog(final String dialogText) {
        final String localCoords = gcDbTransaction.getCache().getCoords().format(GeopointFormatter.Format.LAT_LON_DECMINUTE_RAW);
        final String localTxt = gcDbTransaction.getCachePersonalNote();
        final SpannableStringBuilder ssb = new SpannableStringBuilder();
        if (!"".equals(dialogText)) {
            ssb.append(dialogText).append("\n\n");
        }
        ssb.append(localCoords).append("\n\n");
        ssb.append(localTxt);

        final View v = LayoutInflater.from(gcDbTransaction.getCtx()).inflate(R.layout.gcdb_diffdialog, null);
        ((TextView) v.findViewById(R.id.gcdb_diff)).setText(ssb, TextView.BufferType.SPANNABLE);
        Dialog dialog = Dialogs.bottomSheetDialogWithActionbar(gcDbTransaction.getCtx(), v, R.string.gcdb_diff_title);
        dialog.show();
        final Button downloadButton = v.findViewById(R.id.gcdb_download_remote);
        downloadButton.setVisibility(View.GONE);
        final Button uploadButton = v.findViewById(R.id.gcdb_upload_local);
        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doUpload();
                dialog.dismiss();
            }
        });
    }

    private static GcDbInfo[] callGcDbApi(final String apiAction) {
        final TextView status = gcDbTransaction.getStatus();
        final Geocache cache = gcDbTransaction.getCache();
        try {
            final String token = getDbToken(status);
            final String url = "https://gc.larskl.de/gcdb/";

            final Parameters headers = new Parameters("Authorization", Settings.getGcCredentials().getUserName() + ":" + token);

            Response response = null;
            if ("upload".equals(apiAction)) {
                final Request.Builder request = new Request.Builder().url(url + "corrected/" + cache.getGeocode()).put(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), mapper.writeValueAsString(new GcDbInfo(cache))));
                addHeaders(request, headers);
                response = RxOkHttpUtils.request(OK_HTTP_CLIENT, request.build()).blockingGet();
            } else if ("download".equals(apiAction)) {
                response = Network.getRequest(url + "corrected/" + cache.getGeocode(), null, headers).blockingGet();
            } else if ("history".equals(apiAction)) {
                response = Network.getRequest(url + "history/" + cache.getGeocode(), null, headers).blockingGet();
            }
            final String jsonString;
            if (response.code() == 401) {
                setStatus(status, R.drawable.marker_archive, "Unauthorized");
                Settings.setDbToken("");
            } else if (response.code() == 400) {
                setStatus(status, R.drawable.marker_archive, "Invalid data sent: " + mapper.writeValueAsString(new GcDbInfo(cache)));
            } else if (response.code() == 500) {
                setStatus(status, R.drawable.marker_archive, "Server error");
            } else if (response.code() == 403) {
                setStatus(status, R.drawable.marker_archive, "Request not written to DB");
            } else if (response.code() == 200 && "upload".equals(apiAction)) {
                setStatus(status, R.drawable.marker_visited, "Upload OK");
            } else if (response.code() == 200) {
                jsonString = Network.getResponseData(response);
                if ("[]".equals(jsonString) || "{}".equals(jsonString)) {
                    setStatus(status, R.drawable.marker_not_found_offline, "Not available");
                } else {
                    if ("download".equals(apiAction)) {
                        return new GcDbInfo[] { mapper.readValue(jsonString, GcDbInfo.class) };
                    } else if ("history".equals(apiAction)) {
                        return mapper.readValue(jsonString, GcDbInfo[].class);
                    }
                }
            } else {
                setStatus(status, R.drawable.marker_archive, "Unhandled error");
            }
            return null;

        } catch (JsonProcessingException | LoginException e) {
            setStatus(status, R.drawable.marker_archive, "Failed to parse server response");
            return null;
        }
    }

    private static String getDbToken(final TextView status) throws LoginException {
        String dbToken = Settings.getDbToken();
        if ("".equals(dbToken)) {
            RequestBody requestBody = new FormBody.Builder()
                .add("username", Settings.getGcCredentials().getUserName())
                .add("password", Settings.getGcCredentials().getPassword())
                .add("action", "login")
                .add("getLoginToken", "true")
                .build();
            final Request.Builder request = new Request.Builder().url("https://gc.larskl.de/").post(requestBody);
            final Response response = RxOkHttpUtils.request(OK_HTTP_CLIENT, request.build()).blockingGet();
            if (response.code() == 200) {
                dbToken = Network.getResponseData(response);
                Settings.setDbToken(dbToken);
            } else {
                setStatus(status, R.drawable.marker_archive, "Invalid or unauthorized login. " + response.code());
                throw new LoginException();
            }
        }
        return dbToken;
    }

    public static void checkBarnyAvailability(final Button btn, final Geocache cache) {
        btn.setVisibility(View.VISIBLE);
        final TextView status = (TextView) ((ViewGroup)btn.getParent()).getChildAt(0);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RequestBody requestBody = new FormBody.Builder()
                    .add("gccode", cache.getGeocode())
                    .build();
                final Request.Builder request = new Request.Builder().url("https://barnyruilt.alwaysdata.net/").post(requestBody);
                final Response response = RxOkHttpUtils.request(OK_HTTP_CLIENT, request.build()).blockingGet();
                if (response.code() == 200) {
                    final String jsonString = Network.getResponseData(response);
                    if (jsonString.contains("hasCorrected\": true")) {
                        setStatus(status, R.drawable.marker_found, "Barny Available");
                        return;
                    }
                }
                setStatus(status, R.drawable.marker_not_found_offline, "Barny NOT available");
            }
        });
    }

    private static void saveAndRefresh(final Geocache cache) {
        // Save
        DataStore.saveCache(cache, EnumSet.of(LoadFlags.SaveFlag.DB));

        // Refresh the view
        ((CacheDetailActivity) gcDbTransaction.getStatus().getContext()).notifyDataSetChanged();
    }

    private static class GcDbTransaction {
        private Context context;
        private final Geocache cache;
        private GcDbInfo dbInfo;
        private final TextView status;

        public GcDbTransaction(Geocache cache, TextView status, Context context) {
            this.cache = cache;
            this.status = status;
            this.context = context;
        }

        public Geocache getCache() {
            return cache;
        }

        public GcDbInfo getDbInfo() {
            return dbInfo;
        }

        public TextView getStatus() {
            return status;
        }

        public void setDbInfo(final GcDbInfo dbInfo) {
            this.dbInfo = dbInfo;
        }

        public Context getCtx() {
            return context;
        }

        private boolean hasCacheHasPersonalNote() {
            return cache.getPersonalNote() != null;
        }

        private String getCachePersonalNote() {
            return cache.getPersonalNote() == null ? "" : cache.getPersonalNote();
        }

        private boolean cacheHasModifiedCoords() {
            return cache.hasUserModifiedCoords();
        }

        private boolean coordsMatch() {
            return cache.getCoords().format(GeopointFormatter.Format.LAT_LON_DECMINUTE_RAW).equals(dbInfo.getGeopoint().format(GeopointFormatter.Format.LAT_LON_DECMINUTE_RAW));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class GcDbInfo {
        @JsonProperty("latitude")
        private final float latitude;
        @JsonProperty("longitude")
        private final float longitude;
        @JsonProperty("usernote")
        private final String usernote;
        @JsonProperty("username")
        private final String username;
        @JsonProperty("gcname")
        private final String gcname;
        @JsonProperty("gctype")
        private final String gctype;
        @JsonProperty("coordschanged")
        private boolean coordschanged = false;
        @JsonProperty("version")
        private int version = 0;
        @JsonProperty("avatar")
        private String avatar = "";
        @JsonProperty("datetime")
        private String datetime = "";

        public GcDbInfo(@JsonProperty("latitude") final float latitude, @JsonProperty("longitude") final float longitude, @JsonProperty("usernote") final String usernote, @JsonProperty("username") final String username, @JsonProperty("gcname") final String gcname, @JsonProperty("gctype") final String gctype, @JsonProperty("coordschanged") final boolean coordschanged, @JsonProperty("version") final int version, @JsonProperty("avatar") final String avatar, @JsonProperty("datetime") final String datetime) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.usernote = usernote;
            this.username = username;
            this.gcname = gcname;
            this.gctype = gctype;
            this.coordschanged = coordschanged;
            this.version = version;
            this.avatar = avatar;
            this.datetime = datetime;
        }

        public GcDbInfo(final Geocache cache) {
            this.latitude = (float) cache.getCoords().getLatitude();
            this.longitude = (float) cache.getCoords().getLongitude();
            this.usernote = cache.getPersonalNote() == null ? "" : cache.getPersonalNote();
            this.username = Settings.getGcCredentials().getUserName();
            this.gcname = cache.getName();
            this.gctype = cache.getType().wptTypeId;
        }

        @JsonIgnore
        public Geopoint getGeopoint() {
            return new Geopoint(latitude, longitude);
        }

        public String getUsernote() {
            return usernote;
        }
    }

    private static void addHeaders(final Request.Builder request, final Parameters headers) {
        for (final ImmutablePair<String, String> header : headers) {
            request.header(header.left, header.right);
        }
    }

    private static void setStatus(final TextView tv, final int icon, final String message) {
        tv.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
        tv.setText(message);
    }

}
