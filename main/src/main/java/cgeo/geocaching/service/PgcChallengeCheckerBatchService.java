package cgeo.geocaching.service;

import cgeo.geocaching.R;
import cgeo.geocaching.connector.ConnectorFactory;
import cgeo.geocaching.connector.IConnector;
import cgeo.geocaching.connector.capability.PgcChallengeCheckerCapability;
import cgeo.geocaching.connector.pgc.ProjectGCAPI;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.storage.DataStore;
import cgeo.geocaching.ui.notifications.NotificationChannels;
import cgeo.geocaching.ui.notifications.Notifications;
import cgeo.geocaching.utils.LocalizationUtils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PgcChallengeCheckerBatchService extends AbstractGeocacheBatchService {

    public static void start(final Context context, final List<Geocache> caches) {
        final List<String> geocodes = caches.stream()
                .filter(cache -> {
                    final IConnector connector = null != cache ? ConnectorFactory.getConnector(cache) : null;
                    return connector != null && ((PgcChallengeCheckerCapability) connector).isChallengeCache(cache);
                })
                .map(Geocache::getGeocode)
                .collect(Collectors.toList());
        addGeocodes(context, PgcChallengeCheckerBatchService.class, geocodes);
    }

    @Override
    protected boolean processGeocode(final String geocode) {
        final Geocache cache = DataStore.loadCache(geocode, LoadFlags.LOAD_CACHE_OR_DB);
        if (cache == null) {
            return false;
        }
        final Boolean result = ProjectGCAPI.checkChallenge(geocode);
        if (result == null) {
            return false;
        }
        final String icon = result ? Settings.getPgcChallengeSuccessIcon() : Settings.getPgcChallengeFailureIcon();
        DataStore.setCacheIcons(Collections.singletonList(cache), icon);
        GeocacheChangedBroadcastReceiver.sendBroadcast(this, geocode);
        return true;
    }

    @Override
    protected int getTitle() {
        return R.string.pgc_challenge_check_batch_notification_title;
    }

    @Override
    protected int getForegroundNotificationId() {
        return Notifications.ID_FOREGROUND_NOTIFICATION_PGC_CHALLENGE_CHECK;
    }

    @Override
    protected NotificationChannels getCompletionNotificationChannel() {
        return NotificationChannels.CACHES_DOWNLOADED_NOTIFICATION;
    }

    @Override
    @Nullable
    protected String getCompletedText(final int processedCount) {
        return LocalizationUtils.getString(R.string.pgc_challenge_check_batch_completed, processedCount);
    }

    @Override
    @NonNull
    protected String getInterruptedText(final boolean wasCanceled, final int processedCount, final int totalCount) {
        return LocalizationUtils.getString(
                wasCanceled ? R.string.pgc_challenge_check_batch_canceled : R.string.pgc_challenge_check_batch_failed,
                processedCount, totalCount);
    }
}
