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

import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
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
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;

// for debugging
//~ import android.widget.Toast;
//~ 		Toast.makeText(MainActivity.this, ""+currPosition, Toast.LENGTH_SHORT).show();

public class MainActivity extends Activity {
	enum PlayState { SILENCE, BELL }
	enum AlarmMode { BELL, TTS }
	public static final int ONE_MINUTE_MILLIS = 60000;
	private SharedPreferences prefs;
	private final TimerFragment timerFragment;
	private final SettingsFragment settingsFragment;
	private final LiveFragment liveFragment;
	private final AboutFragment aboutFragment;
	private final HashMap<Integer, Integer> bellMap;
	private MediaPlayer bellPlayer;
	private MediaPlayer silencePlayer;
	private CountDownTimer silenceTimer;
	private TextToSpeech tts;
	private boolean settingsEnabled;
	private int interval;
	private int repeat;
	private String sound;
	private int clickOption;
	private String preparation;
	private int prepareMillis;
	private PlayState currPlayState;
	private boolean runningState;
	private int currRepeat;
	private int currPosition;
	private int totalSilenceCount;
	private int currSilence;

	public MainActivity() {
		timerFragment = new TimerFragment();
		settingsFragment = new SettingsFragment();
		liveFragment = new LiveFragment();
		aboutFragment = new AboutFragment();
		bellMap = new HashMap<>();
		settingsEnabled = true;
		currPlayState = PlayState.BELL;
		runningState = false;
	}

    @Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		// init fragment
        if (findViewById(R.id.fragment_container) != null) {
			replaceFragment(timerFragment);
		}
		// init some data
		bellMap.put(R.id.radio_bell_tiny, R.raw.bell_tiny);
		bellMap.put(R.id.radio_bell_small, R.raw.bell_small);
		bellMap.put(R.id.radio_bell_large, R.raw.bell_large);
		// init settings
		PreferenceManager.setDefaultValues(this, R.xml.settings, false);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		preparation = prefs.getString("pref_preparation", "clicks");
		prepareMillis = preparation.equals("gong") ? 20000
						: preparation.equals("clicks") || preparation.equals("melody") ? 10000
						: 0;
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
			startSession();
			timerFragment.startRefreshTimer();
			if (prefs.getBoolean("pref_keepscreenon", false))
				keepAwake(true);
		} else if (state == TimerFragment.State.COUNTDOWN) {
			timerFragment.setState(TimerFragment.State.PAUSED);
			pauseSession();
			timerFragment.stopRefreshTimer();
		} else if (state == TimerFragment.State.PAUSED) {
			timerFragment.setState(TimerFragment.State.COUNTDOWN);
			resumeSession();
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
		runningState = false;
		currPlayState = PlayState.BELL;
		stopPlayers();
		if (silenceTimer != null)
			silenceTimer.cancel();
		if (prefs.getBoolean("pref_keepscreenon", false))
			keepAwake(false);
		if (tts != null)
			tts.shutdown();
	}

	private void startSession() {
		interval = Integer.parseInt(prefs.getString("pref_interval", "15"));
		repeat = Integer.parseInt(prefs.getString("pref_repeat", "2"));
		sound = prefs.getString("pref_sound", "small");
		clickOption = Integer.parseInt(prefs.getString("pref_click", "1"));
		preparation = prefs.getString("pref_preparation", "clicks");
		prepareMillis = preparation.equals("gong") ? 20000
						: preparation.equals("clicks") || preparation.equals("melody") ? 10000
						: 0;
		currRepeat = preparation.equals("no") ? 1 : 0;
		totalSilenceCount = interval; // we have 1-min silence piece
		runningState = true;
		silenceAndRing();
	}

	public void pauseSession() {
		if (silencePlayer != null) {
			try {
				silencePlayer.pause();
			} catch (IllegalStateException e) {
			}
		}
	}

	public void resumeSession() {
		if (silencePlayer != null) {
			try {
				silencePlayer.start();
			} catch (IllegalStateException e) {
			}
		}
	}

	public void stopSession() {
		runningState = false;
	}

	public void stopPlayers() {
		stopPlayers(null);
	}

	public void stopPlayers(final PlayState which) {
		try {
			if (which == PlayState.BELL || which == null) {
				if (bellPlayer != null) {
					bellPlayer.stop();
					bellPlayer = null;
				}
			}
			if (which == PlayState.SILENCE || which == null) {
				if (silencePlayer != null) {
					silencePlayer.stop();
					silencePlayer = null;
				}
			}
		} catch (IllegalStateException e) {
		}
	}

	private void silenceAndRing() {
		if (!runningState) return;
		if (currRepeat == 0) {
			currSilence = 1;
			prepare();
		} else {
			if (currRepeat <= repeat) {
				currSilence = 1;
				silence();
			} else {
				currRepeat = preparation.equals("no") ? 1 : 0;
				stopSession();
			}
		}
	}

	private MediaPlayer.OnCompletionListener bellCompleteListener = new MediaPlayer.OnCompletionListener() {
		@Override
		public void onCompletion(final MediaPlayer mp) {
			alarm();
		}
	};

	private void alarm() {
		currPlayState = PlayState.BELL;
		if (currRepeat == 0) {
			if (sound.startsWith("tts")) {
				final String phrase = MainActivity.this.getResources().getString(R.string.tts_prepare);
				new TtsPlayer(phrase).speak();
			}
		} else {
			switch (clickOption) {
				case 0:
					if (sound.startsWith("tts")) {
						final int num = currRepeat * interval;
						final String lastEnding = currRepeat == repeat ? MainActivity.this.getResources().getString(R.string.tts_last) : "";
						final String phrase = num + MainActivity.this.getResources().getString(R.string.tts_loop) + lastEnding;
						new TtsPlayer(phrase).speak();
					} else {
						ring(sound);
					}
					break;
				case 1:
					if (sound.startsWith("tts")) {
						final int num = currRepeat * interval;
						final String lastEnding = currRepeat == repeat ? MainActivity.this.getResources().getString(R.string.tts_last) : "";
						final String phrase = num + MainActivity.this.getResources().getString(R.string.tts_loop) + lastEnding;
						if (currRepeat == repeat) {
							new LeadingClickPlayer(2, phrase).play();
						} else {
							new TtsPlayer(phrase).speak();
						}
					} else {
						if (currRepeat == repeat)
							new LeadingClickPlayer(2, getBell(sound)).play();
						else
							ring(sound);
					}
					break;
				case 2:
				case 3:
				case 4:
				case 5:
				case 6:
					final int clickCount = currRepeat % clickOption;
					final int num = currRepeat * interval;
					final String lastEnding = currRepeat == repeat ? MainActivity.this.getResources().getString(R.string.tts_last) : "";
					final String phrase = num + MainActivity.this.getResources().getString(R.string.tts_loop) + lastEnding;
					if (sound.startsWith("tts"))
						new LeadingClickPlayer((clickCount==0?clickOption:clickCount), phrase).play();
					else
						new LeadingClickPlayer((clickCount==0?clickOption:clickCount), getBell(sound)).play();
					break;
			}
		}
		currRepeat++;
		silenceAndRing();
	}

	private void prepare() {
		currPlayState = PlayState.SILENCE;
		final int sndId = preparation.equals("gong") ? R.raw.prepare_gong
							: preparation.equals("melody") ? R.raw.prepare_melody
							: R.raw.prepare_clicks;
		silencePlayer = MediaPlayer.create(this, sndId);
		silencePlayer.setOnCompletionListener(bellCompleteListener);
		silencePlayer.start();
	}

	private void silence() {
		currPlayState = PlayState.SILENCE;
		silencePlayer = MediaPlayer.create(this, R.raw.silence_melody);
		silencePlayer.start();
		startSilenceTimer();
	}

	private void startSilenceTimer() {
		silenceTimer = new CountDownTimer(ONE_MINUTE_MILLIS, 1000) {
			@Override
			public void onTick(final long millisUntilFinished) {
			}
			@Override
			public void onFinish() {
				silenceTimer.cancel();
				stopPlayers(PlayState.SILENCE);
				if (currSilence < totalSilenceCount) {
					currSilence++;
					silence();
				} else {
					alarm();
				}
			}
		};
		silenceTimer.start();
	}

	public int getCurrRepeat() {
		return currRepeat;
	}

	public PlayState getCurrPlayState() {
		return currPlayState;
	}

	public boolean isRunning() {
		return runningState;
	}

	public int getCurrPosition() {
		if (currPlayState == PlayState.BELL) return -1;
		int pos = -1;
		try {
			if (silencePlayer != null) {
				final int base = currRepeat == 0 ? 0 : (currSilence - 1) * ONE_MINUTE_MILLIS;
				pos = base + silencePlayer.getCurrentPosition();
			}
		} catch (IllegalStateException e) {
			pos = -1;
		}
		return pos;
	}

	public int getDuration() {
		if (currPlayState == PlayState.BELL) return -1;
		final int dur;
		if (silencePlayer != null) {
			if (currRepeat == 0) {
				dur = prepareMillis;
			} else {
				dur = totalSilenceCount * ONE_MINUTE_MILLIS;
			}
		} else {
			dur = -1;
		}
		return dur;
	}

	private void ring(final String bell) {
		bellPlayer = MediaPlayer.create(this, getBell(bell));
		bellPlayer.start();
	}

	private int getBell(final String snd) {
		final int bell;
		if ("tiny".equals(snd)) {
			bell = R.raw.bell_tiny;
		} else if ("small".equals(snd)) {
			bell = R.raw.bell_small;
		} else {
			bell = R.raw.bell_large;
		}
		return bell;
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

	// for Live fragment
	public void chime(final View view) {
		if (liveFragment == null) return;
		bellPlayer = MediaPlayer.create(this, bellMap.get(((LiveFragment)liveFragment).getCurrBell()));
		bellPlayer.start();
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

	public void quit() {
		MainActivity.this.finish();
	}

    @Override
	protected void onDestroy() {
		if (bellPlayer != null)
			bellPlayer.release();
		if (silencePlayer != null)
			silencePlayer.release();
		if (tts != null)
			tts.shutdown();
		super.onDestroy();
	}
	
	// inner classes
	public class TtsPlayer implements TextToSpeech.OnInitListener {
		private final String phrase;
		public TtsPlayer(final String text) {
			phrase = text;
		}
		public void speak() {
			stopPlayers(PlayState.BELL);
			tts = new TextToSpeech(MainActivity.this, this);
		}
		@Override
		public void onInit(final int status) {
			if (status == TextToSpeech.SUCCESS) {
				final java.util.Locale lang;
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
					lang = tts.getDefaultVoice().getLocale();
					tts.setLanguage(lang);
					tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, phrase);
				} else {
					lang = tts.getDefaultLanguage();
					tts.setLanguage(lang);
					tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, null);
				}
			} else if (status == TextToSpeech.ERROR) {
				tts.shutdown();
			}
		}
	}

	public class LeadingClickPlayer {
		private final int totalClicks;
		private final int bellId;
		private final String phrase;
		private AlarmMode mode;
		private int currClick;
		public LeadingClickPlayer(final int total, final int bell) {
			mode = AlarmMode.BELL;
			totalClicks = total;
			bellId = bell;
			phrase = "";
			currClick = 1;
		}
		public LeadingClickPlayer(final int total, final String text) {
			mode = AlarmMode.TTS;
			totalClicks = total;
			bellId = 0;
			phrase = text;
			currClick = 1;
		}
		public void play() {
			playClick();
		}
		private MediaPlayer.OnCompletionListener clickCompleteListener = new MediaPlayer.OnCompletionListener() {
			@Override
			public void onCompletion(final MediaPlayer mp) {
				if (currClick < totalClicks) {
					currClick++;
					playClick();
				} else {
					if (mode == AlarmMode.TTS)
						playTTS();
					else
						playBell();
				}
			}
		};
		private void playClick() {
			bellPlayer = MediaPlayer.create(MainActivity.this, R.raw.click);
			bellPlayer.setOnCompletionListener(clickCompleteListener);
			bellPlayer.start();
		}
		private void playBell() {
			bellPlayer = MediaPlayer.create(MainActivity.this, bellId);
			bellPlayer.start();
		}
		private void playTTS() {
			new TtsPlayer(phrase).speak();
		}
	}
}
