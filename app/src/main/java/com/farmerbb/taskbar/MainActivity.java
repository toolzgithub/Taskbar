/* Copyright 2016 Braden Farmer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.farmerbb.taskbar;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.widget.CompoundButton;

import com.farmerbb.taskbar.activity.HomeActivity;
import com.farmerbb.taskbar.activity.ImportSettingsActivity;
import com.farmerbb.taskbar.activity.KeyboardShortcutActivity;
import com.farmerbb.taskbar.activity.ShortcutActivity;
import com.farmerbb.taskbar.activity.StartTaskbarActivity;
import com.farmerbb.taskbar.fragment.AboutFragment;
import com.farmerbb.taskbar.fragment.AdvancedFragment;
import com.farmerbb.taskbar.fragment.FreeformModeFragment;
import com.farmerbb.taskbar.fragment.GeneralFragment;
import com.farmerbb.taskbar.fragment.RecentAppsFragment;
import com.farmerbb.taskbar.service.NotificationService;
import com.farmerbb.taskbar.service.StartMenuService;
import com.farmerbb.taskbar.service.TaskbarService;
import com.farmerbb.taskbar.util.FreeformHackHelper;
import com.farmerbb.taskbar.util.IconCache;
import com.farmerbb.taskbar.util.LauncherHelper;
import com.farmerbb.taskbar.util.U;

import java.io.File;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private SwitchCompat theSwitch;

    private BroadcastReceiver switchReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateSwitch();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LocalBroadcastManager.getInstance(this).registerReceiver(switchReceiver, new IntentFilter("com.farmerbb.taskbar.UPDATE_SWITCH"));

        final SharedPreferences pref = U.getSharedPreferences(this);
        SharedPreferences.Editor editor = pref.edit();

        switch(pref.getString("theme", "light")) {
            case "light":
                setTheme(R.style.AppTheme);
                break;
            case "dark":
                setTheme(R.style.AppTheme_Dark);
                break;
        }

        if(pref.getBoolean("taskbar_active", false) && !isServiceRunning())
            editor.putBoolean("taskbar_active", false);

        // Ensure that components that should be enabled are enabled properly
        boolean launcherEnabled = pref.getBoolean("launcher", false) && canDrawOverlays();
        editor.putBoolean("launcher", launcherEnabled);

        editor.apply();

        ComponentName component = new ComponentName(BuildConfig.APPLICATION_ID, HomeActivity.class.getName());
        getPackageManager().setComponentEnabledSetting(component,
                launcherEnabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);

        ComponentName component2 = new ComponentName(BuildConfig.APPLICATION_ID, KeyboardShortcutActivity.class.getName());
        getPackageManager().setComponentEnabledSetting(component2,
                pref.getBoolean("keyboard_shortcut", false) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);

        ComponentName component3 = new ComponentName(BuildConfig.APPLICATION_ID, ShortcutActivity.class.getName());
        getPackageManager().setComponentEnabledSetting(component3,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);

        ComponentName component4 = new ComponentName(BuildConfig.APPLICATION_ID, StartTaskbarActivity.class.getName());
        getPackageManager().setComponentEnabledSetting(component4,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);

        if(BuildConfig.APPLICATION_ID.equals(BuildConfig.BASE_APPLICATION_ID))
            proceedWithAppLaunch(savedInstanceState);
        else {
            File file = new File(getFilesDir() + File.separator + "imported_successfully");
            if(freeVersionInstalled() && !file.exists()) {
                startActivity(new Intent(this, ImportSettingsActivity.class));
                finish();
            } else {
                proceedWithAppLaunch(savedInstanceState);
            }
        }
    }

    private boolean freeVersionInstalled() {
        PackageManager pm = getPackageManager();
        try {
            PackageInfo pInfo = pm.getPackageInfo(BuildConfig.BASE_APPLICATION_ID, 0);
            return pInfo.versionCode >= 68
                    && pm.checkSignatures(BuildConfig.BASE_APPLICATION_ID, BuildConfig.APPLICATION_ID)
                    == PackageManager.SIGNATURE_MATCH;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void proceedWithAppLaunch(Bundle savedInstanceState) {
        setContentView(R.layout.main);

        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) {
            actionBar.setCustomView(R.layout.switch_layout);
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);
        }

        theSwitch = (SwitchCompat) findViewById(R.id.the_switch);
        if(theSwitch != null) {
            final SharedPreferences pref = U.getSharedPreferences(this);
            theSwitch.setChecked(pref.getBoolean("taskbar_active", false));

            theSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    if(b) {
                        if(canDrawOverlays()) {
                            boolean firstRun = pref.getBoolean("first_run", true);
                            startTaskbarService();

                            if(firstRun && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                ApplicationInfo applicationInfo = null;
                                try {
                                    applicationInfo = getPackageManager().getApplicationInfo(BuildConfig.APPLICATION_ID, 0);
                                } catch (PackageManager.NameNotFoundException e) { /* Gracefully fail */ }

                                if(applicationInfo != null) {
                                    AppOpsManager appOpsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
                                    int mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, applicationInfo.uid, applicationInfo.packageName);

                                    if(mode != AppOpsManager.MODE_ALLOWED) {
                                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                                        builder.setTitle(R.string.pref_header_recent_apps)
                                                .setMessage(R.string.enable_recent_apps)
                                                .setPositiveButton(R.string.action_ok, new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        try {
                                                            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                                                            U.showToastLong(MainActivity.this, R.string.usage_stats_message);
                                                        } catch (ActivityNotFoundException e) {
                                                            U.showErrorDialog(MainActivity.this, "GET_USAGE_STATS");
                                                        }
                                                    }
                                                }).setNegativeButton(R.string.action_cancel, null);

                                        AlertDialog dialog = builder.create();
                                        dialog.show();
                                    }
                                }
                            }
                        } else {
                            U.showPermissionDialog(MainActivity.this);
                            compoundButton.setChecked(false);
                        }
                    } else
                        stopTaskbarService();
                }
            });
        }

        if(savedInstanceState == null) {
            if(!getIntent().hasExtra("theme_change"))
                getFragmentManager().beginTransaction().replace(R.id.fragmentContainer, new AboutFragment(), "AboutFragment").commit();
            else
                getFragmentManager().beginTransaction().replace(R.id.fragmentContainer, new GeneralFragment(), "GeneralFragment").commit();
        } else {
            String fragmentName = savedInstanceState.getString("fragment_name");
            if(fragmentName != null) switch(fragmentName) {
                case "AboutFragment":
                    getFragmentManager().beginTransaction().replace(R.id.fragmentContainer, new AboutFragment(), fragmentName).commit();
                    break;
                case "AdvancedFragment":
                    getFragmentManager().beginTransaction().replace(R.id.fragmentContainer, new AdvancedFragment(), fragmentName).commit();
                    break;
                case "FreeformModeFragment":
                    getFragmentManager().beginTransaction().replace(R.id.fragmentContainer, new FreeformModeFragment(), fragmentName).commit();
                    break;
                case "GeneralFragment":
                    getFragmentManager().beginTransaction().replace(R.id.fragmentContainer, new GeneralFragment(), fragmentName).commit();
                    break;
                case "RecentAppsFragment":
                    getFragmentManager().beginTransaction().replace(R.id.fragmentContainer, new RecentAppsFragment(), fragmentName).commit();
                    break;
            }
        }

        if(!BuildConfig.APPLICATION_ID.equals(BuildConfig.BASE_APPLICATION_ID) && freeVersionInstalled()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.settings_imported_successfully)
                    .setMessage(R.string.import_dialog_message)
                    .setPositiveButton(R.string.action_uninstall, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + BuildConfig.BASE_APPLICATION_ID)));
                            } catch (ActivityNotFoundException e) { /* Gracefully fail */ }
                        }
                    });

            AlertDialog dialog = builder.create();
            dialog.show();
            dialog.setCancelable(false);

            if(theSwitch != null) theSwitch.setChecked(false);

            SharedPreferences pref = U.getSharedPreferences(this);
            String iconPack = pref.getString("icon_pack", BuildConfig.BASE_APPLICATION_ID);
            if(iconPack.contains(BuildConfig.BASE_APPLICATION_ID)) {
                pref.edit().putString("icon_pack", BuildConfig.APPLICATION_ID).apply();
            } else {
                U.refreshPinnedIcons(this);
            }
        }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);

            if(shortcutManager.getDynamicShortcuts().size() == 0) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName(BuildConfig.APPLICATION_ID, StartTaskbarActivity.class.getName());
                intent.putExtra("is_launching_shortcut", true);

                ShortcutInfo shortcut = new ShortcutInfo.Builder(this, "start_taskbar")
                        .setShortLabel(getString(R.string.start_taskbar))
                        .setIcon(Icon.createWithResource(this, R.drawable.shortcut_icon_start))
                        .setIntent(intent)
                        .build();

                Intent intent2 = new Intent(Intent.ACTION_MAIN);
                intent2.setClassName(BuildConfig.APPLICATION_ID, ShortcutActivity.class.getName());
                intent2.putExtra("is_launching_shortcut", true);

                ShortcutInfo shortcut2 = new ShortcutInfo.Builder(this, "freeform_mode")
                        .setShortLabel(getString(R.string.pref_header_freeform))
                        .setIcon(Icon.createWithResource(this, R.drawable.shortcut_icon_freeform))
                        .setIntent(intent2)
                        .build();

                shortcutManager.setDynamicShortcuts(Arrays.asList(shortcut, shortcut2));
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        updateSwitch();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(switchReceiver);
    }

    @SuppressWarnings("deprecation")
    private void startTaskbarService() {
        SharedPreferences pref = U.getSharedPreferences(this);
        SharedPreferences.Editor editor = pref.edit();

        editor.putBoolean("is_hidden", false);

        if(pref.getBoolean("first_run", true)) {
            editor.putBoolean("first_run", false);
            editor.putBoolean("collapsed", true);
        }

        editor.putBoolean("taskbar_active", true);
        editor.putLong("time_of_service_start", System.currentTimeMillis());
        editor.apply();

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && pref.getBoolean("freeform_hack", false)
                && isInMultiWindowMode()
                && !FreeformHackHelper.getInstance().isFreeformHackActive()) {
            U.startFreeformHack(this, false, false);
        }

        startService(new Intent(this, TaskbarService.class));
        startService(new Intent(this, StartMenuService.class));
        startService(new Intent(this, NotificationService.class));
    }

    private void stopTaskbarService() {
        SharedPreferences pref = U.getSharedPreferences(this);
        pref.edit().putBoolean("taskbar_active", false).apply();

        if(!LauncherHelper.getInstance().isOnHomeScreen()) {
            stopService(new Intent(this, TaskbarService.class));
            stopService(new Intent(this, StartMenuService.class));

            IconCache.getInstance(this).clearCache();

            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("com.farmerbb.taskbar.START_MENU_DISAPPEARING"));
        }

        stopService(new Intent(this, NotificationService.class));
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for(ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if(NotificationService.class.getName().equals(service.service.getClassName()))
                return true;
        }

        return false;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void updateSwitch() {
        if(theSwitch != null) {
            SharedPreferences pref = U.getSharedPreferences(this);
            theSwitch.setChecked(pref.getBoolean("taskbar_active", false));
        }
    }

    @Override
    public void onBackPressed() {
        if(getFragmentManager().findFragmentById(R.id.fragmentContainer) instanceof AboutFragment)
            super.onBackPressed();
        else
            getFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new AboutFragment(), "AboutFragment")
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                    .commit();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("fragment_name", getFragmentManager().findFragmentById(R.id.fragmentContainer).getTag());

        super.onSaveInstanceState(outState);
    }
}