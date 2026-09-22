package cgeo.geocaching.settings.fragments;

import cgeo.geocaching.R;
import cgeo.geocaching.connector.gc.GCLogin;
import cgeo.geocaching.connector.pgc.ProjectGCAPI;
import cgeo.geocaching.connector.pgc.ProjectGCConnector;
import cgeo.geocaching.databinding.GcManualLoginBinding;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.AndroidRxUtils;
import cgeo.geocaching.utils.EmojiUtils;
import cgeo.geocaching.utils.JsonUtils;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.PreferenceUtils;
import cgeo.geocaching.utils.ShareUtils;

import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;

import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.fasterxml.jackson.databind.JsonNode;

public class PreferenceServiceProjectGCFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        setPreferencesFromResource(R.xml.preferences_services_projectgc, rootKey);

        wireTokenValidation();

        final Preference getToken = findPreference(getString(R.string.pref_fakekey_pgc_get_token));
        PreferenceUtils.setOnPreferenceClickListener(getToken, preference -> {
            openPgcTokenWebView();
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

    @UiThread
    private void openPgcTokenWebView() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.cgeo_fullScreenDialog);
        final GcManualLoginBinding binding = GcManualLoginBinding.inflate(LayoutInflater.from(requireContext()));
        binding.info.setText(R.string.pgc_settings_description);
        final AlertDialog dialog = builder.create();
        dialog.setView(binding.getRoot());
        GCLogin.initializeWebview(binding.webview);

        WindowCompat.enableEdgeToEdge(requireActivity().getWindow());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            final Insets innerPadding = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            v.setPadding(innerPadding.left, innerPadding.top, innerPadding.right, innerPadding.bottom);
            return windowInsets;
        });

        GCLogin.injectGcCookiesIntoWebView();
        binding.webview.loadUrl("https://project-gc.com/User/Settings");
        binding.okButton.setOnClickListener(v ->
            binding.webview.evaluateJavascript(
                "(function() {" +
                "  var inputs = document.querySelectorAll('input');" +
                "  var result = {};" +
                "  inputs.forEach(function(el) {" +
                "    var name = (el.name || el.id || '').toLowerCase();" +
                "    if (name.indexOf('token') >= 0 || name.indexOf('key') >= 0) result.pgcToken = el.value;" +
                "    if (name.indexOf('user') >= 0 || name.indexOf('uid') >= 0) result.userId = el.value;" +
                "  });" +
                "  return result;" +
                "})()",
                value -> {
                    final JsonNode json = JsonUtils.stringToNode(value);
                    if (null != json) {
                        final String pgcToken = JsonUtils.toText(JsonUtils.get(json, "pgcToken"), "");
                        final String userId = JsonUtils.toText(JsonUtils.get(json, "userId"), "");
                        if (!userId.isBlank() && !pgcToken.isBlank()) {
                            Settings.setProjectGCAccessToken(pgcToken);
                            Settings.setProjectGCUserId(userId);
                        }
                        Log.d("[ProjectGC] pgcToken=" + pgcToken + ", userId=" + userId);
                    }
                }
            )
        );
        binding.cancelButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
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
