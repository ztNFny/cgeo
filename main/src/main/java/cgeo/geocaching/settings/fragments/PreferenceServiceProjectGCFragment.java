package cgeo.geocaching.settings.fragments;

import cgeo.geocaching.R;
import cgeo.geocaching.connector.pgc.ProjectGCAPI;
import cgeo.geocaching.connector.pgc.ProjectGCConnector;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.AndroidRxUtils;
import cgeo.geocaching.utils.EmojiUtils;
import cgeo.geocaching.utils.PreferenceUtils;
import cgeo.geocaching.utils.ShareUtils;

import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class PreferenceServiceProjectGCFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        setPreferencesFromResource(R.xml.preferences_services_projectgc, rootKey);

        wireTokenValidation();

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

        wireIconPicker(R.string.pref_pgc_challenge_success_icon, Settings::getPgcChallengeSuccessIcon, Settings::setPgcChallengeSuccessIcon);
        wireIconPicker(R.string.pref_pgc_challenge_failure_icon, Settings::getPgcChallengeFailureIcon, Settings::setPgcChallengeFailureIcon);
    }

    private void wireTokenValidation() {
        final EditTextPreference tokenPref = findPreference(getString(R.string.pref_pgc_access_token));
        if (tokenPref == null) {
            return;
        }
        tokenPref.setOnPreferenceChangeListener((preference, newValue) -> {
            final String token = (String) newValue;
            AndroidRxUtils.networkScheduler.scheduleDirect(() -> {
                final ProjectGCAPI.TokenValidationResult result = ProjectGCAPI.validateToken(token);
                requireActivity().runOnUiThread(() -> onTokenValidationResult(result, token));
            });
            // block automatic storage — we store manually on success
            return false;
        });
    }

    private void onTokenValidationResult(final ProjectGCAPI.TokenValidationResult result, final String token) {
        if (result == ProjectGCAPI.TokenValidationResult.PLUS_MEMBER) {
            Settings.setProjectGCAccessToken(token);
            final EditTextPreference tokenPref = findPreference(getString(R.string.pref_pgc_access_token));
            if (tokenPref != null) {
                tokenPref.setText(token);
            }
            return;
        }

        final String message = result == ProjectGCAPI.TokenValidationResult.NOT_PLUS_MEMBER
                ? getString(R.string.pgc_token_not_plus)
                : getString(R.string.pgc_token_wrong_key);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.pgc_token_validation_failed_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();

        // clear token and deactivate connector
        Settings.setProjectGCAccessToken(null);
        final EditTextPreference tokenPref = findPreference(getString(R.string.pref_pgc_access_token));
        if (tokenPref != null) {
            tokenPref.setText(null);
        }
        final CheckBoxPreference activePref = findPreference(getString(R.string.pref_connectorProjectGCActive));
        if (activePref != null) {
            activePref.setChecked(false);
        }
    }

    private void wireIconPicker(final int prefKeyRes,
                                final java.util.function.Supplier<String> getter,
                                final java.util.function.Consumer<String> setter) {
        final Preference pref = findPreference(getString(prefKeyRes));
        if (pref == null) {
            return;
        }
        updateIconSummary(pref, getter.get());
        PreferenceUtils.setOnPreferenceClickListener(pref, preference -> {
            EmojiUtils.selectEmojiPopup(requireContext(), getter.get(), false, null, selected -> {
                setter.accept(selected);
                updateIconSummary(pref, selected);
            });
            return true;
        });
    }

    private void updateIconSummary(final Preference pref, final String icon) {
        if (icon == null || icon.isEmpty()) {
            pref.setSummary(null);
            return;
        }
        final TypedValue tv = new TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true);
        final int primaryColor = requireContext().getColor(tv.resourceId);
        final SpannableString ss = new SpannableString(icon);
        ss.setSpan(new ForegroundColorSpan(primaryColor), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        pref.setSummary(ss);
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.init_projectgc);
    }
}
