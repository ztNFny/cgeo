package cgeo.geocaching.settings.fragments;

import cgeo.geocaching.R;
import cgeo.geocaching.connector.pgc.ProjectGCConnector;
import cgeo.geocaching.utils.PreferenceUtils;
import cgeo.geocaching.utils.ShareUtils;

import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class PreferenceServiceProjectGCFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        setPreferencesFromResource(R.xml.preferences_services_projectgc, rootKey);

        final Preference getToken = findPreference(getString(R.string.pref_fakekey_pgc_get_token));
        PreferenceUtils.setOnPreferenceClickListener(getToken, preference -> {
            ShareUtils.openUrl(getContext(), "https://project-gc.com/User/Settings");
            return true;
        });

        final Preference openWebsite = findPreference(getString(R.string.pref_fakekey_pgc_website));
        PreferenceUtils.setOnPreferenceClickListener(openWebsite, preference -> {
            ShareUtils.openUrl(getContext(), ProjectGCConnector.URL);
            return true;
        });

        setIconPreviewSummary(R.string.pref_pgc_challenge_success_icon);
        setIconPreviewSummary(R.string.pref_pgc_challenge_failure_icon);
    }

    private void setIconPreviewSummary(final int prefKeyRes) {
        final EditTextPreference pref = findPreference(getString(prefKeyRes));
        if (pref == null) {
            return;
        }
        final TypedValue tv = new TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true);
        final int primaryColor = requireContext().getColor(tv.resourceId);
        pref.setSummaryProvider(p -> {
            final String value = ((EditTextPreference) p).getText();
            if (value == null || value.isEmpty()) {
                return null;
            }
            final SpannableString ss = new SpannableString(value);
            ss.setSpan(new ForegroundColorSpan(primaryColor), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return ss;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.init_projectgc);
    }
}
