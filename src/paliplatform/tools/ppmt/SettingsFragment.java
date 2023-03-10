/*
 * Copyright (C) 2023 J.R. Bhaddacak
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

package paliplatform.tools.ppmt;

import java.util.Arrays;
import java.util.List;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

public class SettingsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.settings);
	}
	
	@Override
	public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
		updateSummary(sharedPreferences, key);
	}

	private void updateSummary(final SharedPreferences sharedPreferences, final String key) {
		final Preference pref = findPreference(key);
		if ("pref_interval".equals(key)) {
			pref.setSummary(sharedPreferences.getString(key, "") + " minutes");
		} else if ("pref_repeat".equals(key)) {
			final String[] repeatEntries = getResources().getStringArray(R.array.repeat_times_entries);
			final String repeat = sharedPreferences.getString(key, "");
			final String summRepeat;
			if("1".equals(repeat))
				summRepeat = repeatEntries[0];
			else if("2".equals(repeat))
				summRepeat = repeatEntries[1];
			else
				summRepeat = repeat + " times";
			pref.setSummary(summRepeat);
		} else if ("pref_sound".equals(key)) {
			final String[] soundEntries = getResources().getStringArray(R.array.sound_entries);
			final List<String> soundValues = Arrays.asList(getResources().getStringArray(R.array.sound_values));
			final String sound = sharedPreferences.getString(key, "");
			final int ind = soundValues.indexOf(sound);
			pref.setSummary(soundEntries[ind]);
		} else if ("pref_ending_bell".equals(key)) {
			final String[] endingBellEntries = getResources().getStringArray(R.array.ending_bell_entries);
			final List<String> endingBellValues = Arrays.asList(getResources().getStringArray(R.array.ending_bell_values));
			final String ending_bell = sharedPreferences.getString(key, "");
			final int ind = endingBellValues.indexOf(ending_bell);
			pref.setSummary(endingBellEntries[ind]);
		} else if ("pref_click".equals(key)) {
			final String[] clickEntries = getResources().getStringArray(R.array.click_entries);
			final List<String> clickValues = Arrays.asList(getResources().getStringArray(R.array.click_values));
			final String click = sharedPreferences.getString(key, "");
			final int ind = clickValues.indexOf(click);
			pref.setSummary(clickEntries[ind]);
		} else if ("pref_preparation".equals(key)) {
			final String[] prepareEntries = getResources().getStringArray(R.array.preparation_entries);
			final List<String> prepareValues = Arrays.asList(getResources().getStringArray(R.array.preparation_values));
			final String prepare = sharedPreferences.getString(key, "");
			final int ind = prepareValues.indexOf(prepare);
			pref.setSummary(prepareEntries[ind]);
		} else if ("pref_keepscreenon".equals(key)) {
			final String summKeep = sharedPreferences.getBoolean(key, true)
								? getResources().getString(R.string.keepscreenon_summ_yes)
								: getResources().getString(R.string.keepscreenon_summ_no);
			pref.setSummary(summKeep);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		// update preference summaries
		final SharedPreferences prefs = ((MainActivity) getActivity()).getPrefs();
		updateSummary(prefs, "pref_interval");
		updateSummary(prefs, "pref_repeat");
		updateSummary(prefs, "pref_sound");
		updateSummary(prefs, "pref_ending_bell");
		updateSummary(prefs, "pref_click");
		updateSummary(prefs, "pref_preparation");
		updateSummary(prefs, "pref_keepscreenon");
	}

	@Override
	public void onResume() {
		super.onResume();
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onPause() {
		super.onPause();
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	}	
}
