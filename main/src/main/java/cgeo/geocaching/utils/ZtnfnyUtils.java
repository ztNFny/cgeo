package cgeo.geocaching.utils;


import cgeo.geocaching.CacheDetailActivity.Page;
import cgeo.geocaching.R;

public class ZtnfnyUtils {
    public static int getTabIconFromTitle(final long pageId) {
        if (pageId == Page.VARIABLES.id) {
            return R.drawable.cachedetails_variables;
        } else if (pageId == Page.WAYPOINTS.id) {
            return R.drawable.cachedetails_waypoints;
        } else if (pageId == Page.DETAILS.id) {
            return R.drawable.cachedetails_details;
        } else if (pageId == Page.DESCRIPTION.id) {
            return R.drawable.cachedetails_description;
        } else if (pageId == Page.LOGS.id) {
            return R.drawable.cachedetails_logbook;
        } else if (pageId == Page.LOGSFRIENDS.id) {
            return R.drawable.cachedetails_friends;
        } else if (pageId == Page.IMAGEGALLERY.id) {
            return R.drawable.cachedetails_images;
        } else if (pageId == Page.INVENTORY.id) {
            return R.drawable.cachedetails_trackable;
        } else {
            return R.drawable.cachedetails_details;
        }
    }

    public static int getTabIconBadge(final long pageId, final String tabTitle) {
        try {
            return Integer.parseInt(tabTitle.replaceAll(".* \\(([^<]*)\\)", "$1"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

}
