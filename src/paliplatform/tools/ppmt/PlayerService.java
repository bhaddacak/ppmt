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

import java.util.List;
import java.util.HashMap;
import java.util.Arrays;

import android.app.Service;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.Binder;
import android.media.MediaPlayer;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;

public class PlayerService extends Service {
	enum PlayState { SILENCE, BELL }
	enum AlarmMode { BELL, TTS }
	public static final int ONE_MINUTE_MILLIS = 60000;
	private static final int NOTI_ID = 1;
	private Notification.Builder notiBuilder;
	private SharedPreferences prefs;
	private IBinder playerServiceBinder;
	private MediaPlayer bellPlayer;
	private MediaPlayer silencePlayer;
	private TextToSpeech tts;
	private boolean settingsEnabled;
	private int interval;
	private HashMap<Integer, Integer> intervalMap;
	private int repeat;
	private String sound;
	private String lastBell;
	private int clickOption;
	private String preparation;
	private int prepareMillis;
	private PlayState currPlayState;
	private boolean runningState;
	private int currRepeat;
	private int currPosition;

	@Override
	public void onCreate() {
		super.onCreate();
		playerServiceBinder = new PlayerServiceBinder(this);
		currPlayState = PlayState.BELL;
		runningState = false;
		notiBuilder = new Notification.Builder(this)
							.setSmallIcon(R.mipmap.ic_launcher)
							.setContentTitle(getResources().getString(R.string.noti_message))
							.setContentIntent(null);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		intervalMap = new HashMap<>();
		final List<String> intervalValues = Arrays.asList(getResources().getStringArray(R.array.interval_times_values));
		for (final String s : intervalValues) {
			final int intv = Integer.parseInt(s);
			switch (intv) {
				case 1: intervalMap.put(intv, R.raw.silence1_click); break;
				case 5: intervalMap.put(intv, R.raw.silence5_click); break;
				case 10: intervalMap.put(intv, R.raw.silence10_click); break;
				case 15: intervalMap.put(intv, R.raw.silence15_click); break;
				case 20: intervalMap.put(intv, R.raw.silence20_click); break;
			}
		}
		preparation = prefs.getString("pref_preparation", "click");
		prepareMillis = preparation.equals("no") ? 3000 : preparation.equals("gong") ? 20000 : 10000;
	}

	@Override
	public IBinder onBind(final Intent intent) {
		return playerServiceBinder;
	}

	@Override
	public void onDestroy() {
		if (bellPlayer != null)
			bellPlayer.release();
		if (silencePlayer != null)
			silencePlayer.release();
		if (tts != null)
			tts.shutdown();
		super.onDestroy();
	}

	public void startSession() {
		interval = Integer.parseInt(prefs.getString("pref_interval", "15"));
		repeat = Integer.parseInt(prefs.getString("pref_repeat", "2"));
		final String[] soundOptions = prefs.getString("pref_sound", "small-large").split("-");
		sound = soundOptions[0];
		lastBell = soundOptions[1];
		clickOption = Integer.parseInt(prefs.getString("pref_click", "1"));
		preparation = prefs.getString("pref_preparation", "click");
		prepareMillis = preparation.equals("no") ? 3000 : preparation.equals("gong") ? 20000 : 10000;
		currRepeat = 0;
		runningState = true;
		startPlayerTask();
		startForeground(NOTI_ID, notiBuilder.build());
	}

	public void pauseSession() {
		if (silencePlayer != null) {
			try {
				silencePlayer.pause();
			} catch (IllegalStateException e) {
			}
		}
		stopForeground(true);
	}

	public void resumeSession() {
		if (silencePlayer != null) {
			try {
				silencePlayer.start();
			} catch (IllegalStateException e) {
			}
		}
		startForeground(NOTI_ID, notiBuilder.build());
	}

	public void stopSession() {
		stopForeground(true);
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

	private void startPlayerTask() {
		final Thread thread = new Thread(null, doThreadProcessing, "player");
		thread.start();
	}

	private Runnable doThreadProcessing = new Runnable() {
		@Override
		public void run() {
			silenceAndRing();
		}
	};

	private void silenceAndRing() {
		if (!runningState) return;
		if (currRepeat == 0) {
			prepare();
		} else {
			if (currRepeat <= repeat) {
				silence();
			} else {
				currRepeat = 0;
				stopSession();
			}
		}
	}

	private MediaPlayer.OnCompletionListener soundCompleteListener = new MediaPlayer.OnCompletionListener() {
		@Override
		public void onCompletion(final MediaPlayer mp) {
			alarm();
		}
	};

	private void alarm() {
		currPlayState = PlayState.BELL;
		if (currRepeat == 0) {
			if (sound.startsWith("tts")) {
				final String phrase = getResources().getString(R.string.tts_prepare);
				new TtsPlayer(phrase).speak();
			}
		} else {
			switch (clickOption) {
				case 0:
					if (sound.startsWith("tts")) {
						final int mins = currRepeat * interval;
						final String lastEnding = currRepeat == repeat ? getResources().getString(R.string.tts_last) : "";
						final String phrase = mins + getResources().getString(R.string.tts_loop) + lastEnding;
						new TtsPlayer(phrase).speak();
					} else {
						if (sound.equals("no")) {
							if (currRepeat == repeat)
								ring(lastBell);
						} else {
							if (currRepeat == repeat)
								ring(lastBell);
							else
								ring(sound);
						}
					}
					break;
				case 1:
					if (sound.startsWith("tts")) {
						final int mins = currRepeat * interval;
						final String lastEnding = currRepeat == repeat ? getResources().getString(R.string.tts_last) : "";
						final String phrase = mins + getResources().getString(R.string.tts_loop) + lastEnding;
						if (currRepeat == repeat) {
							new ClickPlayer(2, phrase, AlarmMode.TTS).play();
						} else {
							new TtsPlayer(phrase).speak();
						}
					} else {
						if (currRepeat == repeat) {
							new ClickPlayer(2, lastBell, AlarmMode.BELL).play();
						} else {
							if (!sound.equals("no"))
								ring(sound);
						}
					}
					break;
				case 2:
				case 3:
				case 4:
				case 5:
				case 6:
					final int clickCount = currRepeat % clickOption;
					final int clickAdded = clickCount == 0 ? clickOption - 1 : clickCount - 1;
					if (sound.startsWith("tts")) {
						final int mins = currRepeat * interval;
						final String lastEnding = currRepeat == repeat ? getResources().getString(R.string.tts_last) : "";
						final String phrase = mins + getResources().getString(R.string.tts_loop) + lastEnding;
						new ClickPlayer(clickAdded, phrase, AlarmMode.TTS).play();
					} else {
						final String bell = currRepeat == repeat ? lastBell : sound;
						new ClickPlayer(clickAdded, bell, AlarmMode.BELL).play();
					}
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
							: preparation.equals("click") ? R.raw.prepare_click
							: R.raw.prepare_3sec;
		silencePlayer = MediaPlayer.create(this, sndId);
		silencePlayer.setOnCompletionListener(soundCompleteListener);
		silencePlayer.start();
	}

	private void silence() {
		currPlayState = PlayState.SILENCE;
		silencePlayer = MediaPlayer.create(this, intervalMap.get(interval));
		silencePlayer.setOnCompletionListener(soundCompleteListener);
		silencePlayer.start();
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
				pos = silencePlayer.getCurrentPosition();
			}
		} catch (IllegalStateException e) {
			pos = -1;
		}
		return pos;
	}

	public int getDuration() {
		if (currPlayState == PlayState.BELL) return -1;
		int dur = -1;
		if (silencePlayer != null) {
			if (currRepeat == 0) {
				dur = prepareMillis;
			} else {
				try {
					dur = silencePlayer.getDuration();
				} catch (IllegalStateException e) {
					dur = -1;
				}
			}
		}
		return dur;
	}

	private void ring(final String bell) {
		final int bellId = getBell(bell);
		if (bellId == -1) return;
		bellPlayer = MediaPlayer.create(this, bellId);
		bellPlayer.start();
	}

	private int getBell(final String snd) {
		final int bell;
		if ("tiny".equals(snd)) {
			bell = R.raw.bell_tiny;
		} else if ("small".equals(snd)) {
			bell = R.raw.bell_small;
		} else if ("large".equals(snd)) {
			bell = R.raw.bell_large;
		} else {
			bell = -1;
		}
		return bell;
	}

	// inner classes
	public class PlayerServiceBinder extends Binder {
		private PlayerService service;
		public PlayerServiceBinder(final PlayerService service) {
			this.service = service;
		}
		public PlayerService getPlayerService() {
			return service;
		}
	}

	public class TtsPlayer implements TextToSpeech.OnInitListener {
		private final String phrase;
		public TtsPlayer(final String text) {
			phrase = text;
		}
		public void speak() {
			stopPlayers(PlayState.BELL);
			tts = new TextToSpeech(PlayerService.this, this);
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

	public class ClickPlayer {
		private final int totalClicks;
		private final String bellOrPhrase;
		private AlarmMode mode;
		private int currClick;
		public ClickPlayer(final int total, final String param, final AlarmMode amode) {
			mode = amode;
			totalClicks = total < 0 ? 0 : total;
			bellOrPhrase = param;
			currClick = 0;
		}
		public void play() {
			proceed();
		}
		private void proceed() {
			if (currClick < totalClicks) {
				playClick();
				currClick++;
			} else {
				if (mode == AlarmMode.TTS) {
					playTTS();
				} else {
					if (!bellOrPhrase.equals("no"))
						playBell();
				}
			}
		}
		private MediaPlayer.OnCompletionListener clickCompleteListener = new MediaPlayer.OnCompletionListener() {
			@Override
			public void onCompletion(final MediaPlayer mp) {
				proceed();
			}
		};
		private void playClick() {
			bellPlayer = MediaPlayer.create(PlayerService.this, R.raw.click);
			bellPlayer.setOnCompletionListener(clickCompleteListener);
			bellPlayer.start();
		}
		private void playBell() {
			final int bellId = getBell(bellOrPhrase);
			if (bellId == -1) return;
			bellPlayer = MediaPlayer.create(PlayerService.this, bellId);
			bellPlayer.start();
		}
		private void playTTS() {
			new TtsPlayer(bellOrPhrase).speak();
		}
	}
}
