/*
 * Copyright (C) 2016 Wiktor Nizio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pl.org.seva.texter.view.activity;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import androidx.databinding.DataBindingUtil;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import android.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import pl.org.seva.texter.R;
import pl.org.seva.texter.presenter.source.ActivityRecognitionSource;
import pl.org.seva.texter.presenter.utils.PermissionsUtils;
import pl.org.seva.texter.presenter.utils.SmsSender;
import pl.org.seva.texter.view.adapter.TitledPagerAdapter;
import pl.org.seva.texter.TexterApplication;
import pl.org.seva.texter.presenter.dagger.Graph;
import pl.org.seva.texter.databinding.ActivityMainBinding;
import pl.org.seva.texter.databinding.DialogHelpBinding;
import pl.org.seva.texter.databinding.DialogStartupBinding;
import pl.org.seva.texter.view.fragment.HistoryFragment;
import pl.org.seva.texter.view.fragment.StatsFragment;
import pl.org.seva.texter.view.fragment.NavigationFragment;
import pl.org.seva.texter.view.layout.SlidingTabLayout;
import pl.org.seva.texter.presenter.source.LocationSource;
import pl.org.seva.texter.presenter.utils.Timer;

public class MainActivity extends AppCompatActivity {

    @SuppressWarnings({"CanBeFinal"})
    @Inject
    SmsSender smsSender;
    @SuppressWarnings({"CanBeFinal"})
    @Inject
    LocationSource locationSource;
    @SuppressWarnings({"CanBeFinal"})
    @Inject
    PermissionsUtils permissionsUtils;
    @SuppressWarnings({"CanBeFinal"})
    @Inject
    Timer timer;
    @SuppressWarnings({"CanBeFinal"})
    @Inject
    ActivityRecognitionSource activityRecognitionSource;

    private static final String PREF_STARTUP_SHOWN = "pref_startup_shown";

    private static final int STATS_TAB_POSITION = 0;
    private static final int MAP_TAB_POSITION = 1;
    private static final int HISTORY_TAB_POSITION = 2;

    private static final int GOOGLE_REQUEST_CODE = 0;

    private static final int NUMBER_OF_TABS = 3;

    /** Number of milliseconds that will be taken for a double click. */
    private static final long DOUBLE_CLICK_MILLIS = 5000;
    /** Used when counting a double click. */
    private long clickTime;
    /** Obtained from intent, may be null. */
    private String action;
    private boolean showSettingsWhenPermissionGranted;
    private boolean shuttingDown;
    private Dialog dialog;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        action = getIntent().getAction();
        if (action == null) {
            finish();
        }

        Graph graph = ((TexterApplication) getApplication()).getGraph();
        graph.inject(this);
        activityRecognitionSource.init(this);

        // Set up colors depending on SDK version.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();

            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            int color;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                color = getResources().getColor(R.color.colorPrimaryDark, getTheme());
            }
            else {
                color = getResources().getColor(R.color.colorPrimaryDark);
            }
            window.setStatusBarColor(color);
        }
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        CharSequence[] titles = new CharSequence[NUMBER_OF_TABS];
        titles[STATS_TAB_POSITION] = getString(R.string.stats_tab_name);
        titles[MAP_TAB_POSITION] = getString(R.string.map_tab_name);
        titles[HISTORY_TAB_POSITION] = getString(R.string.history_tab_name);

        Toolbar toolbar = binding.toolBar.toolBar;
        setSupportActionBar(toolbar);
        List<Fragment> fragments = new ArrayList<>();
        fragments.add(StatsFragment.newInstance());
        fragments.add(NavigationFragment.newInstance());
        fragments.add(HistoryFragment.newInstance());

        TitledPagerAdapter adapter =
                new TitledPagerAdapter(getFragmentManager(), titles).
                        setItems(fragments);

        ViewPager pager = binding.pager;
        if (pager != null) {
            pager.setAdapter(adapter);
        }

        SlidingTabLayout tabs = binding.tabs;

        if (tabs != null) {
            final int tabColor;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                tabColor = getResources().getColor(R.color.tabsScrollColor, getTheme());
            }
            else {
                tabColor = getResources().getColor(R.color.tabsScrollColor);
            }
            tabs.setDistributeEvenly();
            tabs.setCustomTabColorizer(position -> tabColor);
            tabs.setViewPager(pager);
        }
        else if (savedInstanceState == null && action != null && action.equals(Intent.ACTION_MAIN)) {
            timer.reset();
        }

        smsSender.init(this, getString(R.string.speed_unit));

        int googlePlay = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        if (googlePlay != ConnectionResult.SUCCESS) {
            GoogleApiAvailability.getInstance().
                    getErrorDialog(this, googlePlay, GOOGLE_REQUEST_CODE).show();
        }

        if (!showStartupDialog()) {
            processPermissions();
        }
    }

    /**
     * All actions that require permissions must be placed here. The method performs them or
     * asks for permissions if they haven't been granted already.
     *
     * @return true if all permissions had been granted before calling the method
     */
    private boolean processPermissions() {
        List<String> permissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissionsUtils
                    .permissionGrantedListener()
                    .filter(permission -> permission.equals(Manifest.permission.ACCESS_FINE_LOCATION))
                    .subscribe(__ -> onLocationPermissionGranted());
        }
        else  {
            initGps();
        }
        if (smsSender.needsPermission() && smsSender.isTextingEnabled() && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS) !=
                PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.SEND_SMS);
        }
        if (permissions.isEmpty()) {
            return true;
        }
        String[] arr = new String[permissions.size()];
        permissions.toArray(arr);
        ActivityCompat.requestPermissions(
                this,
                arr,
                PermissionsUtils.PERMISSION_ACCESS_FINE_LOCATION_REQUEST);

        return false;
    }

    private void initGps() {
        locationSource.init(this);
        locationSource.callProviderListener();
    }

    private String read(String file) {
        String result = null;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(getAssets().open(file)))) {
            StringBuilder builder = new StringBuilder();
            String line = br.readLine();
            while (line != null){
                builder.append(line);
                line = br.readLine();
            }
            result = builder.toString();
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
        return result;
    }

    private boolean showStartupDialog() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean(PREF_STARTUP_SHOWN, false)) {
            return false;
        }
        dialog = new Dialog(this);
        dialog.setCancelable(false);
        DialogStartupBinding dialogBinding = DataBindingUtil.inflate(
                LayoutInflater.from(this),
                R.layout.dialog_startup,
                (ViewGroup) binding.getRoot(),
                false);
        dialog.setContentView(dialogBinding.getRoot());
        WebView web = dialogBinding.web;

        String language = Locale.getDefault().getLanguage();
        web.getSettings().setDefaultTextEncodingName("utf-8");

        String content = read(language.equals("pl") ? "startup_pl.html" : "startup_en.html")
                .replace("[APP_VERSION]", getVersionName());
        web.loadData(
                content,
                "text/html",
                "UTF-8");

        dialogBinding.dismiss.setOnClickListener(v -> {
            processPermissions();
            dialog.dismiss();
            prefs.edit().putBoolean(PREF_STARTUP_SHOWN, true).apply();  // asynchronously
        });
        dialogBinding.settings.setOnClickListener(v -> {
            dialog.dismiss();
            prefs.edit().putBoolean(PREF_STARTUP_SHOWN, true).apply();
            showSettingsWhenPermissionGranted = true;  // Only relevant if permission is not granted.
            if (processPermissions()) {
                // Called if permission has already been granted, e.g. when API < 23.
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        dialog.show();
        return true;
    }

    private void showHelpDialog() {
        dialog = new Dialog(this);
        dialog.setCancelable(false);
        DialogHelpBinding dialogBinding = DataBindingUtil.inflate(
                LayoutInflater.from(this),
                R.layout.dialog_help,
                (ViewGroup) binding.getRoot(),
                false);
        dialog.setContentView(dialogBinding.getRoot());
        WebView web = dialogBinding.web;
        web.getSettings().setDefaultTextEncodingName("utf-8");

        String language = Locale.getDefault().getLanguage();

        String content = read(language.equals("pl") ? "help_pl.html" : "help_en.html")
                .replace("[APP_VERSION]", getVersionName());
        web.loadData(content, "text/html", "UTF-8");

        dialogBinding.ok.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private String getVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        }
        catch (PackageManager.NameNotFoundException ex) {
            return getString(R.string.app_version_unknown);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        // If request is cancelled, the result arrays are empty.
        if (requestCode == PermissionsUtils.PERMISSION_ACCESS_FINE_LOCATION_REQUEST) {
            permissionsUtils.onRequestPermissionsResult(permissions, grantResults);
        }
    }

    @Override
    public void onDestroy() {
        // Also called when the screen is rotated.
        if (dialog != null) {
            dialog.dismiss();
        }
        if (action != null && action.equals(Intent.ACTION_MAIN)) {
            // Condition is false when activity has been launched from a notification.
            if (shuttingDown) {
                stopService();
            }
        }
        super.onDestroy();
    }

    private void stopService() {
        ((TexterApplication) getApplication()).stopService();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (System.currentTimeMillis() - clickTime < DOUBLE_CLICK_MILLIS) {
            shuttingDown = true;
            super.onBackPressed();
        }
        else {
            Toast.makeText(this, R.string.tap_back_second_time, Toast.LENGTH_SHORT).
                    show();
            clickTime = System.currentTimeMillis();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_help:
                showHelpDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onLocationPermissionGranted() {
        initGps();
        locationSource.callProviderListener();
        if (showSettingsWhenPermissionGranted) {
            startActivity(new Intent(this, SettingsActivity.class));
        }
    }
}
