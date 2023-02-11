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

import java.util.HashMap;

import android.os.Bundle;
import android.os.IBinder;
import android.app.Activity;
import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RadioButton;
import android.media.MediaPlayer;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.ServiceConnection;
import android.preference.PreferenceManager;

// for debugging
//~ import android.widget.Toast;
//~ 		Toast.makeText(MainActivity.this, ""+currPosition, Toast.LENGTH_SHORT).show();

public class MainActivity extends Activity {
	private SharedPreferences prefs;
	private Intent playerServiceIntent;
	private ComponentName playerServiceCompName;
	private PlayerService playerService;
	private ServiceConnection serviceConnection;
	private final TimerFragment timerFragment;
	private final SettingsFragment settingsFragment;
	private final LiveFragment liveFragment;
	private final AboutFragment aboutFragment;
	private final HashMap<Integer, Integer> liveBellMap;
	private MediaPlayer liveBellPlayer;
	private boolean settingsEnabled;

	public MainActivity() {
		timerFragment = new TimerFragment();
		settingsFragment = new SettingsFragment();
		liveFragment = new LiveFragment();
		aboutFragment = new AboutFragment();
		liveBellMap = new HashMap<>();
		settingsEnabled = true;
	}

    @Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		// init fragment
        if (findViewById(R.id.fragment_container) != null) {
			replaceFragment(timerFragment);
		}
		// init bell data for Live fragment
		liveBellMap.put(R.id.radio_bell_tiny, R.raw.bell_tiny);
		liveBellMap.put(R.id.radio_bell_small, R.raw.bell_small);
		liveBellMap.put(R.id.radio_bell_large, R.raw.bell_large);
		// init settings
		PreferenceManager.setDefaultValues(this, R.xml.settings, false);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		// init player service
		playerServiceIntent = new Intent(this, PlayerService.class);
		playerServiceCompName = startService(playerServiceIntent);
	}

    @Override
	protected void onDestroy() {
		if (liveBellPlayer != null)
			liveBellPlayer.release();
		if (playerServiceCompName != null)
			stopService(playerServiceIntent);
		super.onDestroy();
	}
	
    @Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		// set up action bar
		final ActionBar abar = getActionBar();
		abar.setTitle(R.string.app_name);
		abar.setSubtitle(R.string.app_subtitle);
		abar.setLogo(R.mipmap.ic_launcher);
		// inflate menus
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

    @Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		final int id = item.getItemId();
		switch (id) {
			case R.id.menu_quit:
				quit();
				break;
		}
		return super.onOptionsItemSelected(item);
	}
	
	public SharedPreferences getPrefs() {
		return prefs;
	}

	private void replaceFragment(final Fragment frag) {
		final Button[] buttons = { 
			(Button) findViewById(R.id.button_timer),
			(Button) findViewById(R.id.button_settings),
			(Button) findViewById(R.id.button_live),
			(Button) findViewById(R.id.button_about) };
		for (int i = 0; i < buttons.length; i++) {
			if (i == 1 && !settingsEnabled)
				buttons[i].setTextColor(getResources().getColor(android.R.color.tertiary_text_dark, null));
			else
				buttons[i].setTextColor(getResources().getColor(android.R.color.primary_text_dark, null));
		}
		final Button bthl;
		if (frag instanceof TimerFragment)
			bthl = buttons[0];
		else if (frag instanceof SettingsFragment)
			bthl = buttons[1];
		else if (frag instanceof LiveFragment)
			bthl = buttons[2];
		else
			bthl = buttons[3];
		bthl.setTextColor(getResources().getColor(android.R.color.holo_blue_bright, null));
		final FragmentTransaction transaction = getFragmentManager().beginTransaction();
		transaction.replace(R.id.fragment_container, frag);
		transaction.commit();
	}

	public void goTimer(final View view) {
		replaceFragment(timerFragment);
	}

	public void goSettings(final View view) {
		replaceFragment(settingsFragment);
	}

	public void goLive(final View view) {
		replaceFragment(liveFragment);
	}

	public void goAbout(final View view) {
		replaceFragment(aboutFragment);
	}

	public void startOrPauseTimer(final View view) {
		final TimerFragment.State state = timerFragment.getState();
		if (state == TimerFragment.State.READY) {
			timerFragment.setState(TimerFragment.State.COUNTDOWN);
			setSettingsEnabled(false);
			bindPlayerService();
			if (prefs.getBoolean("pref_keepscreenon", false))
				keepAwake(true);
		} else if (state == TimerFragment.State.COUNTDOWN) {
			if (playerService == null) return;
			if (playerService.getCurrPlayState() == PlayerService.PlayState.BELL) return;
			playerService.pauseSession();
			timerFragment.setState(TimerFragment.State.PAUSED);
			timerFragment.stopRefreshTimer();
		} else if (state == TimerFragment.State.PAUSED) {
			if (playerService == null) return;
			if (playerService.getCurrPlayState() == PlayerService.PlayState.BELL) return;
			playerService.resumeSession();
			timerFragment.setState(TimerFragment.State.COUNTDOWN);
			timerFragment.resumeRefreshTimer();
		}
		timerFragment.updateStartButton();
	}

	public void resetTimer() {
		timerFragment.setState(TimerFragment.State.READY);
		setSettingsEnabled(true);
		timerFragment.stopRefreshTimer();
		timerFragment.initMillis();
		timerFragment.updateTimerDisplay(true);
		timerFragment.updateStartButton();
		unbindPlayerService();
		stopLiveBellPlayer();
		if (prefs.getBoolean("pref_keepscreenon", false))
			keepAwake(false);
	}

	public PlayerService getPlayerService() {
		return playerService;
	}

	public void unbindPlayerService() {
		if (playerService != null) {
			playerService.stopPlayers();
			playerService.stopSession();
			unbindService(serviceConnection);
			playerService = null;
		}
	}
	private void bindPlayerService() {
		serviceConnection = new ServiceConnection() {
			@Override
			public void onServiceConnected(final ComponentName className, final IBinder service) {
				final PlayerService.PlayerServiceBinder binder = (PlayerService.PlayerServiceBinder) service;
				playerService = binder.getPlayerService();
				playerService.startSession();
				timerFragment.startRefreshTimer();
			}
			@Override
			public void onServiceDisconnected(final ComponentName componentName) {
				playerService.stopSession();
				serviceConnection = null;
				playerService = null;
				timerFragment.stopRefreshTimer();
			}
		};
		bindService(playerServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
	}

	// for Live fragment
	public void chime(final View view) {
		if (liveFragment == null) return;
		liveBellPlayer = MediaPlayer.create(this, liveBellMap.get(((LiveFragment)liveFragment).getCurrBell()));
		liveBellPlayer.start();
	}

	private void stopLiveBellPlayer() {
		try {
			if (liveBellPlayer != null) {
				liveBellPlayer.stop();
				liveBellPlayer = null;
			}
		} catch (IllegalStateException e) {
		}
	}

	public void onBellSizeClicked(final View view) {
		if (liveFragment == null) return;
		if (((RadioButton) view).isChecked()) {
			final int bellId = view.getId();
			((LiveFragment)liveFragment).setCurrBell(bellId);
			final SharedPreferences.Editor peditor = prefs.edit();
			peditor.putInt("pref_bell_size", bellId);
			peditor.commit();
		}
	}

	public void setSettingsEnabled(final boolean val) {
		final Button butSettings = (Button) findViewById(R.id.button_settings);
		butSettings.setClickable(val);
		if (val)
			butSettings.setTextColor(getResources().getColor(android.R.color.primary_text_dark, null));
		else
			butSettings.setTextColor(getResources().getColor(android.R.color.tertiary_text_dark, null));
		settingsEnabled = val;
	}

	public void keepAwake(final boolean val) {
		final Window win = getWindow();
		final WindowManager.LayoutParams winParams = win.getAttributes();
		if (val) {
			win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			winParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
		} else {
			win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			win.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			winParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
		}
		win.setAttributes(winParams);
	}

	public void quit() {
		resetTimer();
		MainActivity.this.finish();
	}
}
