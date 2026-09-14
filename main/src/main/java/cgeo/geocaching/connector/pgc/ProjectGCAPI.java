package cgeo.geocaching.connector.pgc;

import cgeo.geocaching.R;
import cgeo.geocaching.connector.gc.GCUtils;
import cgeo.geocaching.network.HttpRequest;
import cgeo.geocaching.network.Parameters;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.CryptUtils;
import cgeo.geocaching.utils.LocalizationUtils;
import cgeo.geocaching.utils.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;

public final class ProjectGCAPI {

    private static final String APP_ID = "103";
    // TODO: Move this into build files
    private static final String APP_SECRET = LocalizationUtils.getPlainString(R.string.projectgc_consumer_key);;
    private static final String BASE_URL = "https://project-gc.com/api/v1";

    private ProjectGCAPI() {
        // no instance
    }

    /** Returns the "data" field of the API response on success, null on error or missing auth. */
    @Nullable
    public static JsonNode get(final String endpointPath) {
        return executeRequest(endpointPath, HttpRequest.Method.GET, null);
    }

    /** Returns the "data" field of the API response on success, null on error or missing auth. */
    @Nullable
    public static JsonNode post(final String endpointPath, @Nullable final JsonNode jsonBody) {
        return executeRequest(endpointPath, HttpRequest.Method.POST, jsonBody);
    }

    /** Returns the "data" field of the API response on success, null on error or missing auth. */
    @Nullable
    public static JsonNode put(final String endpointPath, @Nullable final JsonNode jsonBody) {
        return executeRequest(endpointPath, HttpRequest.Method.PUT, jsonBody);
    }

    /**
     * DELETE is not yet supported by HttpRequest.Method; use this method for future use.
     * Currently returns null always.
     */
    @Nullable
    public static JsonNode delete(final String endpointPath) {
        // TODO: implement DELETE once HttpRequest.Method includes DELETE
        Log.w("ProjectGCAPI.delete: DELETE not yet implemented for " + endpointPath);
        return null;
    }

    /** Returns list of available Virtual GPS lists for the authenticated user. */
    public static List<VgpsList> getVgpsLists() {
        final JsonNode data = get("/virtual-gps");
        if (data == null) {
            return Collections.emptyList();
        }
        final JsonNode listsNode = data.get("lists");
        if (listsNode == null || !listsNode.isArray()) {
            return Collections.emptyList();
        }
        final List<VgpsList> result = new ArrayList<>();
        for (final JsonNode item : listsNode) {
            if (item.isObject()) {
                final int id = item.path("id").asInt(-1);
                final String name = item.path("name").asText(null);
                if (id > 0) {
                    result.add(new VgpsList(id, name));
                }
            } else if (item.isNumber() || item.isTextual()) {
                final int id = item.asInt(-1);
                if (id > 0) {
                    result.add(new VgpsList(id, null));
                }
            }
        }
        return result;
    }

    /** Returns list of geocodes (GC codes) for the given VGPS list ID. */
    public static List<String> getVgpsCaches(final int vgpsId) {
        final JsonNode data = get("/virtual-gps/vgpsid:" + vgpsId);
        if (data == null || !data.isArray()) {
            return Collections.emptyList();
        }
        final List<String> geocodes = new ArrayList<>();
        for (final JsonNode gcId : data) {
            geocodes.add(GCUtils.gcIdToGcCode(gcId.asInt()));
        }
        return geocodes;
    }

    @Nullable
    private static JsonNode executeRequest(final String endpointPath, final HttpRequest.Method method, @Nullable final JsonNode jsonBody) {
        final String fullPath = "/api/v1" + endpointPath;
        final Parameters headers = buildAuthHeaders(fullPath);
        if (headers == null) {
            return null;
        }
        try {
            HttpRequest request = new HttpRequest()
                    .uri(BASE_URL + endpointPath)
                    .method(method)
                    .headers(headers)
                    .callTimeout(15000);
            if (jsonBody != null) {
                request = request.bodyJson(jsonBody);
            }
            final JsonNode response = request.requestJsonNode().blockingGet();
            if (response == null || !response.has("data")) {
                return null;
            }
            final JsonNode status = response.get("status");
            if (status != null && !status.isNull()) {
                final String statusMsg = status.get("message").asText();
                if (statusMsg.equals("ok")) {
                    return response.get("data");
                } else {
                    final Optional<ProjectGCAPIError> error = ProjectGCAPIError.isError(statusMsg);
                    if (error.isPresent()) {
                        throw new Exception(error.get().getUserErrorMessage());
                    } else {
                        Log.e("ProjectGCAPI: API returned unknown non-ok status: " + statusMsg);
                        throw new Exception("ProjectGCAPI: API returned unknown non-ok status: " + statusMsg);
                    }
                }
            }
        } catch (final Exception e) {
            Log.w("ProjectGCAPI: request failed for " + endpointPath, e);
            return null;
        }
        return null;
    }

    /** Checks the challenge cache eligibility for the current user. Returns true/false, or null on error. */
    @Nullable
    public static Boolean checkChallenge(final String gcCode) {
        try {
            final JsonNode response = get("/challenge-checker/gccode:" + gcCode + "/prcode:" + Settings.getGCUserId() + "/cached");
            if (response == null || !response.isArray() || response.isEmpty()) {
                return null;
            }
            return response.get(0).path("status").asBoolean();
        } catch (final Exception e) {
            Log.w("ProjectGCAPI: checkChallenge failed for " + gcCode, e);
            return null;
        }
    }

    public static boolean isPlusUser() {
        final JsonNode result = get("/test/pgc-plus");
        return result.asText().equals("Project-GC Plus membership confirmed.");
    }

    public static boolean loginDataPresent() {
        final String userToken = Settings.getProjectGCAccessToken();
        final String userId = String.valueOf(GCUtils.userCodeToUserId(Settings.getGCUserId()));
        return !StringUtils.isBlank(userToken) && !StringUtils.isBlank(userId);

    }

    @Nullable
    private static Parameters buildAuthHeaders(final String endpointPath) {
        final String userToken = Settings.getProjectGCAccessToken();
        final String userId = String.valueOf(GCUtils.userCodeToUserId(Settings.getGCUserId()));
        final long unixtime = System.currentTimeMillis() / 1000;
        final String message = APP_SECRET + unixtime + userId + userToken + endpointPath;
        final String signature = CryptUtils.bytesToHex(CryptUtils.hashHmac(message, APP_SECRET, "HmacSHA256"));
        if (StringUtils.isBlank(signature)) {
            return null;
        }
        return new Parameters(
                "Authorization", "Bearer " + userToken,
                "X-App-ID", APP_ID,
                "X-User-ID", userId,
                "X-Unixtime", String.valueOf(unixtime),
                "X-Signature", signature
        );
    }
}
