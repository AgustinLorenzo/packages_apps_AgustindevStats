/*
 * Copyright (C) 2012 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package agustindev.romstats

import android.app.NotificationManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.DateFormat

class AnonymousStats: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (supportFragmentManager.findFragmentById(android.R.id.content) == null) {
            supportFragmentManager.beginTransaction()
                    .add(android.R.id.content, SettingsFragment())
                    .commit()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu, menu)

        // remove Uninstall option if RomStats is installed as System App
        try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                // installed as user app, ok
            } else {
                menu.findItem(R.id.uninstall).isVisible = false
            }
        } catch (e: Exception) {
            menu.findItem(R.id.uninstall).isVisible = false
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.learn_more -> AlertDialog.Builder(this)
                    .setMessage(resources.getString(R.string.anonymous_statistics_warning))
                    .setTitle(R.string.anonymous_statistics_warning_title)
                    .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }.show()
            R.id.uninstall -> uninstallSelf()
            R.id.hideicon -> {
                val dialogClickListener = DialogInterface.OnClickListener { dialog, which ->
                    when (which) {
                        DialogInterface.BUTTON_POSITIVE -> {
                            // Yes button clicked
                            hideLauncherIcon()
                            dialog.dismiss()
                        }

                        DialogInterface.BUTTON_NEGATIVE ->
                            // No button clicked
                            dialog.dismiss()
                    }
                }
                val builder = AlertDialog.Builder(this)
                builder.setTitle(R.string.app_name)
                        .setIcon(R.drawable.ic_launcher)
                        .setMessage(R.string.pref_hideicon_desc)
                        .setPositiveButton(android.R.string.ok, dialogClickListener)
                        .setNegativeButton(android.R.string.cancel, dialogClickListener)
                        .setCancelable(true)
                        .show()
            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun uninstallSelf() {
        val intent = Intent(Intent.ACTION_DELETE)
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)
    }

    private fun hideLauncherIcon() {
        val p = packageManager
        p.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        )

        try {
            val sdCard = Environment.getExternalStorageDirectory()
            val dir = File(sdCard.absolutePath + "/.ROMStats")
            dir.mkdirs()
            val cookieFile = File(dir, "hide_icon")

            val optOutCookie = FileOutputStream(cookieFile)
            val oStream = OutputStreamWriter(optOutCookie)
            oStream.write("true")
            oStream.close()
            optOutCookie.close()
        } catch (e: IOException) {
            Log.e(Const.TAG, "Error while writing 'hide_icon' file to sdcard", e)
        }
    }

    class SettingsFragment : PreferenceFragmentCompat(), DialogInterface.OnClickListener, DialogInterface.OnDismissListener,
            Preference.OnPreferenceChangeListener {

        private var mEnableReporting: CheckBoxPreference? = null
        private var mPersistentOptout: CheckBoxPreference? = null
        private var mViewStats: Preference? = null
        //private Preference btnUninstall;

        private var mOkClicked: Boolean = false

        private lateinit var mPrefs: SharedPreferences

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

            val ctx = context!!
            addPreferencesFromResource(R.xml.anonymous_stats)

            mPrefs = getPreferences(ctx)

            mPrefs = ctx.getSharedPreferences(Utilities.SETTINGS_PREF_NAME, 0)
            mEnableReporting = preferenceManager.findPreference(Const.ANONYMOUS_OPT_IN) as CheckBoxPreference
            mPersistentOptout = preferenceManager.findPreference(Const.ANONYMOUS_OPT_OUT_PERSIST) as CheckBoxPreference
            mViewStats = preferenceManager.findPreference(PREF_VIEW_STATS) as Preference
            mViewStats?.setOnPreferenceClickListener {
                val uri = Uri.parse(Const.VIEW_URL + "stats")
                startActivity(Intent(Intent.ACTION_VIEW, uri))
                true
            }
            //btnUninstall = preferenceManager.findPreference(PREF_UNINSTALL);
            mPrefs.edit().putBoolean(Const.ANONYMOUS_OPT_IN, true).apply()

            val firstBoot = mPrefs.getBoolean(Const.ANONYMOUS_FIRST_BOOT, true)
            if (firstBoot) {
                Log.d(Const.TAG, "First app start, set params and report immediately")
                mPrefs.edit().putBoolean(Const.ANONYMOUS_FIRST_BOOT, false).apply()
                mPrefs.edit().putLong(Const.ANONYMOUS_LAST_CHECKED, 1).apply()
                ReportingServiceManager.launchService(ctx)
            }

            val mPrefAboutVersion = preferenceManager.findPreference(PREF_ABOUT) as Preference
            var versionString = resources.getString(R.string.app_name)
            try {
                versionString += " v" + ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
            } catch (e: Exception) {
                // nothing
            }

            mPrefAboutVersion.title = versionString

            val aboutWesbite = preferenceManager.findPreference(PREF_WEBSITE) as Preference
            aboutWesbite.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resources.getString(R.string.pref_info_website_url)))
                startActivity(intent)

                false
            }

            var mPrefHolder: Preference
            /* Experimental feature 2 */
            val lastCheck = mPrefs.getLong(Const.ANONYMOUS_LAST_CHECKED, 0)
            if (lastCheck > 1) {
                // show last checkin date
                var lastCheckStr =
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(java.util.Date(lastCheck))
                lastCheckStr = resources.getString(R.string.last_report_on) + ": " + lastCheckStr

                mPrefHolder = preferenceManager.findPreference(PREF_LAST_REPORT_ON)
                mPrefHolder.setOnPreferenceClickListener {
                    ReportingServiceManager.launchService(ctx, true)
                    true
                }
                mPrefHolder.title = lastCheckStr

                val nextCheck = mPrefs.getLong(Const.ANONYMOUS_NEXT_ALARM, 0)
                if (nextCheck > 0) {
                    var nextAlarmStr = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(java.util.Date(nextCheck))
                    nextAlarmStr = resources.getString(R.string.next_report_on) + ": " + nextAlarmStr
                    mPrefHolder.summary = nextAlarmStr
                }
            } else {
                mPrefHolder = preferenceManager.findPreference(PREF_LAST_REPORT_ON)
                val prefCat = preferenceManager.findPreference("pref_stats") as PreferenceCategory
                prefCat.removePreference(mPrefHolder)
            }

            mPrefHolder = preferenceManager.findPreference(PREF_REPORT_INTERVAL)
            val tFrame = Utilities.timeFrame.toInt()
            mPrefHolder.summary = resources.getQuantityString(R.plurals.reporting_interval_days, tFrame, tFrame)

            // Cancel notification on app open, in case it doesn't AutoCancel
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(Utilities.NOTIFICATION_ID)

            Utilities.checkIconVisibility(ctx)
        }

        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            return false
        }

        override fun onDismiss(dialog: DialogInterface) {
            if (!mOkClicked) {
                mEnableReporting!!.isChecked = false
            }
        }

        override fun onClick(dialog: DialogInterface, which: Int) {
            when (which) {
                DialogInterface.BUTTON_POSITIVE -> {
                    mOkClicked = true
                    mPrefs.edit().putBoolean(Const.ANONYMOUS_OPT_IN, true).apply()

                    mPersistentOptout!!.isChecked = false
                    try {
                        val sdCard = Environment.getExternalStorageDirectory()
                        val dir = File(sdCard.absolutePath + "/.ROMStats")
                        val cookieFile = File(dir, "optout")
                        cookieFile.delete()
                        Log.d(Const.TAG, "Persistent Opt-Out cookie removed successfully")
                    } catch (e: Exception) {
                        Log.w(Const.TAG, "Unable to write persistent optout cookie", e)
                    }

                    ReportingServiceManager.launchService(context!!)
                }
                DialogInterface.BUTTON_NEGATIVE -> mEnableReporting!!.isChecked = false
                else -> {
                    val uri = Uri.parse("http://www.cyanogenmod.com/blog/cmstats-what-it-is-and-why-you-should-opt-in")
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            }
        }
    }

    companion object {

        private const val PREF_VIEW_STATS = "pref_view_stats"
        private const val PREF_LAST_REPORT_ON = "pref_last_report_on"
        private const val PREF_REPORT_INTERVAL = "pref_reporting_interval"
        private const val PREF_ABOUT = "pref_about"
        private const val PREF_WEBSITE = "pref_website"

        fun getPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences(Utilities.SETTINGS_PREF_NAME, 0)
        }
    }
}
