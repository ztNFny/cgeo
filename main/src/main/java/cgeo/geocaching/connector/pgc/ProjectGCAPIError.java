package cgeo.geocaching.connector.pgc;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Optional;

enum ProjectGCAPIError {
    UNAUTHORIZED("Unauthorized", "Check auth token"),
    INVALID_SIGNATURE("Invalid signature", "Check auth token"),
    EXPIRED("Request timestamp expired.", "Check system time"),
    UNKNOWN("", "Unknown error");

    @NonNull final String statusResponse;
    @NonNull final String userErrorMessage;

    ProjectGCAPIError(final String statusResponse, final String userErrorMessage) {
        this.statusResponse = statusResponse;
        this.userErrorMessage = userErrorMessage;
    }

    public String getUserErrorMessage() {
        return userErrorMessage;
    }

    public static Optional<ProjectGCAPIError> isError(final String statusMsg) {
        return Arrays.stream(values()).filter(e -> e.statusResponse.equalsIgnoreCase(statusMsg)).findFirst();
    }
}
