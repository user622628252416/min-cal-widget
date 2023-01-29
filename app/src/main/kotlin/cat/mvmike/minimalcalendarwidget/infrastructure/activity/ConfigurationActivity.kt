// Copyright (c) 2016, Miquel Martí <miquelmarti111@gmail.com>
// See LICENSE for licensing information
package cat.mvmike.minimalcalendarwidget.infrastructure.activity

import android.content.*
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.*
import cat.mvmike.minimalcalendarwidget.BuildConfig
import cat.mvmike.minimalcalendarwidget.R
import cat.mvmike.minimalcalendarwidget.application.RedrawWidgetUseCase
import cat.mvmike.minimalcalendarwidget.domain.configuration.*
import cat.mvmike.minimalcalendarwidget.domain.configuration.item.InvisibleLauncherAlias
import cat.mvmike.minimalcalendarwidget.domain.configuration.item.VisibleLauncherAlias


open class ConfigurationActivity : AppCompatActivity() {

    companion object {
        fun start(context: Context) = context.startActivity(
            Intent(context, ConfigurationActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.configuration)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.configuration_view, SettingsFragment())
            .commit()
    }

    class SettingsFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.sharedPreferencesName = PREFERENCE_KEY
            setPreferencesFromResource(R.xml.preferences, rootKey)

            fillEntriesAndValues()
            updateCurrentSelection()
            fillAboutSection()

            preferenceManager.sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(p0: SharedPreferences?, p1: String?) {
            updateCurrentSelection()
            RedrawWidgetUseCase.execute(requireContext())
            setLauncherIconVisible()
        }

        override fun onDestroyView() {
            super.onDestroyView()
            preferenceManager.sharedPreferences!!.unregisterOnSharedPreferenceChangeListener(this)
        }

        private fun setLauncherIconVisible() {
            val visible = !BooleanConfigurationItem.WidgetShowTitleBar.get(requireContext())

            val pm: PackageManager = requireContext().packageManager

            if (visible) {
                pm.setAliasEnabled(VisibleLauncherAlias::class.java, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
                pm.setAliasEnabled(InvisibleLauncherAlias::class.java, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
            } else {
                pm.setAliasEnabled(VisibleLauncherAlias::class.java, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
                pm.setAliasEnabled(InvisibleLauncherAlias::class.java, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            }
        }

        private fun PackageManager.setAliasEnabled(alias: Class<*>, value: Int) =
            setComponentEnabledSetting(ComponentName(requireContext(), alias), value, PackageManager.DONT_KILL_APP)

        private fun fillEntriesAndValues() = enumConfigurationItems().forEach {
            it.asListPreference().apply {
                entries = it.getDisplayValues(this@SettingsFragment.requireContext())
                entryValues = it.getKeys()
                value = it.getCurrentKey(this@SettingsFragment.requireContext())
            }
        }

        private fun fillAboutSection() {
            SOURCE_KEY.asPreference().let {
                it.summary = SOURCE_URL
                it.setOnPreferenceClickListener {
                    SOURCE_URL.openInBrowser()
                    true
                }
            }
            TRANSLATE_KEY.asPreference().let {
                it.summary = TRANSLATE_URL
                it.setOnPreferenceClickListener {
                    TRANSLATE_URL.openInBrowser()
                    true
                }
            }
            VERSION_KEY.asPreference().summary = BuildConfig.VERSION_NAME
        }

        private fun updateCurrentSelection() {
            enumConfigurationItems().forEach {
                it.asListPreference().summary = it.getCurrentDisplayValue(requireContext())
            }

            booleanConfigurationItems().forEach {
                it.asCheckBoxPreference().isChecked = it.get(requireContext())
            }

            ConfigurationItem.WidgetTransparency.asSeekBarPreference().value = ConfigurationItem.WidgetTransparency.get(requireContext()).percentage
        }

        private fun enumConfigurationItems() = setOf(
            EnumConfigurationItem.WidgetTheme,
            EnumConfigurationItem.FirstDayOfWeek,
            EnumConfigurationItem.InstancesSymbolSet,
            EnumConfigurationItem.InstancesColour
        )

        private fun booleanConfigurationItems() = setOf(
            BooleanConfigurationItem.WidgetShowDeclinedEvents,
            BooleanConfigurationItem.WidgetFocusOnCurrentWeek,
            BooleanConfigurationItem.WidgetShowTitleBar,
        )

        private fun String.asPreference() =
            preferenceManager.findPreference<Preference>(this) as Preference

        private fun <E> ConfigurationItem<E>.asListPreference() =
            preferenceManager.findPreference<Preference>(this.key) as ListPreference

        private fun <E> ConfigurationItem<E>.asCheckBoxPreference() =
            preferenceManager.findPreference<Preference>(this.key) as CheckBoxPreference

        private fun <E> ConfigurationItem<E>.asSeekBarPreference() =
            preferenceManager.findPreference<Preference>(this.key) as SeekBarPreference

        private fun String.openInBrowser() = try {
            requireContext().startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setData(Uri.parse(this))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (ignored: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.no_browser_application, Toast.LENGTH_SHORT).show()
        }
    }
}
