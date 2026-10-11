package cgeo.geocaching.settings.fragments;

import cgeo.geocaching.R;
import cgeo.geocaching.connector.gc.GCLogin;
import cgeo.geocaching.connector.pgc.ProjectGCAPI;
import cgeo.geocaching.connector.pgc.ProjectGCConnector;
import cgeo.geocaching.databinding.GcManualLoginBinding;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.AndroidRxUtils;
import cgeo.geocaching.utils.EmojiUtils;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.PreferenceUtils;
import cgeo.geocaching.utils.ShareUtils;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

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
            ShareUtils.openUrl(getContext(), ProjectGCConnector.BASE_URL);
            return true;
        });

        wireIconPicker(R.string.pref_pgc_challenge_success_icon, Settings::getPgcChallengeSuccessIcon, Settings::setPgcChallengeSuccessIcon);
        wireIconPicker(R.string.pref_pgc_challenge_failure_icon, Settings::getPgcChallengeFailureIcon, Settings::setPgcChallengeFailureIcon);
    }

    @UiThread
    @SuppressLint("AddJavascriptInterface")
    private void openPgcTokenWebView() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.cgeo_fullScreenDialog);
        final GcManualLoginBinding binding = GcManualLoginBinding.inflate(LayoutInflater.from(requireContext()));
        binding.info.setText(R.string.pgc_settings_description);
        binding.okButton.setVisibility(View.GONE);
        final AlertDialog dialog = builder.create();
        dialog.setView(binding.getRoot());
        GCLogin.initializeWebview(binding.webview);

        WindowCompat.enableEdgeToEdge(requireActivity().getWindow());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            final Insets innerPadding = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            v.setPadding(innerPadding.left, innerPadding.top, innerPadding.right, innerPadding.bottom);
            return windowInsets;
        });

        binding.webview.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onValuesExtracted(final String pgcToken, final String userId) {
                Settings.setProjectGCAccessToken(pgcToken);
                Settings.setProjectGCUserId(userId);
                Log.d("[ProjectGC] pgcToken=" + pgcToken + ", userId=" + userId);
                //Settings.setProjectGCSessionCookie(Cookies.extractCookie(ProjectGCConnector.BASE_URL, "PHPSESSID").value());
                requireActivity().runOnUiThread(() -> {
                    dialog.dismiss();
                    Toast.makeText(requireContext(), R.string.pgc_login_complete, Toast.LENGTH_SHORT).show();
                });
            }
        }, "AndroidBridge");

        // Poll for the token/userId inputs after each page load (page may render dynamically).
        binding.webview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(final WebView view, final String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(
                    "(function() {" +
                    "  var interval = setInterval(function() {" +
                    "    var inputs = document.querySelectorAll('input');" +
                    "    var pgcToken = '', userId = '';" +
                    "    inputs.forEach(function(el) {" +
                    "      var name = (el.name || el.id || '').toLowerCase();" +
                    "      if (name.indexOf('token') >= 0 || name.indexOf('key') >= 0) pgcToken = el.value;" +
                    "      if (name.indexOf('user') >= 0 || name.indexOf('uid') >= 0) userId = el.value;" +
                    "    });" +
                    "    if (pgcToken && userId) {" +
                    "      clearInterval(interval);" +
                    "      AndroidBridge.onValuesExtracted(pgcToken, userId);" +
                    "    }" +
                    "  }, 500);" +
                    "})()",
                    null
                );
            }
        });

        GCLogin.injectGcCookiesIntoWebView();
        binding.webview.loadUrl("https://project-gc.com/User/Settings");
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
