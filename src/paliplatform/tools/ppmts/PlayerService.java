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

package paliplatform.tools.ppmts;

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
	private int clickOption;
	private boolean useTtsMarker;
	private boolean hasPreparation;
	private int prepareMillis;
	private boolean runningState;
	private int currRepeat;
	private int currPosition;

	@Override
	public void onCreate() {
		super.onCreate();
		playerServiceBinder = new PlayerServiceBinder(this);
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
				case 5: intervalMap.put(intv, R.raw.silence5_bell); break;
				case 10: intervalMap.put(intv, R.raw.silence10_bell); break;
				case 15: intervalMap.put(intv, R.raw.silence15_bell); break;
				case 20: intervalMap.put(intv, R.raw.silence20_bell); break;
			}
		}
		hasPreparation = prefs.getBoolean("pref_preparation", true);
		prepareMillis = hasPreparation ? 20000 : 0;
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
		clickOption = Integer.parseInt(prefs.getString("pref_click", "1"));
		useTtsMarker = prefs.getBoolean("pref_ttsmarker", false);
		hasPreparation = prefs.getBoolean("pref_preparation", true);
		prepareMillis = hasPreparation ? 20000 : 0;
		currRepeat = hasPreparation ? 0 : 1;
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
		stopBellPlayer();
		stopSilencePlayer();
	}

	private void stopBellPlayer() {
		try {
			if (bellPlayer != null) {
				bellPlayer.stop();
				bellPlayer = null;
			}
		} catch (IllegalStateException e) {
		}
	}

	private void stopSilencePlayer() {
		try {
			if (silencePlayer != null) {
				silencePlayer.stop();
				silencePlayer = null;
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
			silence(true);
		} else {
			if (currRepeat <= repeat) {
				silence(false);
			} else {
				currRepeat = hasPreparation ? 0 : 1;
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
		if (currRepeat == 0) {
			if (useTtsMarker) {
				final String phrase = getResources().getString(R.string.tts_prepare);
				new TtsPlayer(phrase).speak();
			}
		} else {
			switch (clickOption) {
				case 0:
					if (useTtsMarker) {
						final int num = currRepeat * interval;
						final String lastEnding = currRepeat == repeat ? getResources().getString(R.string.tts_last) : "";
						final String phrase = num + getResources().getString(R.string.tts_loop) + lastEnding;
						new TtsPlayer(phrase).speak();
					}
					break;
				case 1:
					if (useTtsMarker) {
						final int num = currRepeat * interval;
						final String lastEnding = currRepeat == repeat ? getResources().getString(R.string.tts_last) : "";
						final String phrase = num + getResources().getString(R.string.tts_loop) + lastEnding;
						if (currRepeat == repeat) {
							new ClickPlayer(2, phrase).play();
						} else {
							new TtsPlayer(phrase).speak();
						}
					} else {
						if (currRepeat == repeat)
							new ClickPlayer(2).play();
					}
					break;
				case 2:
				case 3:
				case 4:
				case 5:
				case 6:
					final int clickCount = currRepeat % clickOption;
					final int num = currRepeat * interval;
					final String lastEnding = currRepeat == repeat ? getResources().getString(R.string.tts_last) : "";
					final String phrase = num + getResources().getString(R.string.tts_loop) + lastEnding;
					if (useTtsMarker)
						new ClickPlayer((clickCount==0?clickOption:clickCount), phrase).play();
					else
						new ClickPlayer((clickCount==0?clickOption:clickCount)).play();
					break;
			}
		}
		currRepeat++;
		silenceAndRing();
	}

	private void silence(final boolean isPrepare) {
		final int sndId = isPrepare ? R.raw.prepare_bell : intervalMap.get(interval);
		silencePlayer = MediaPlayer.create(this, sndId);
		silencePlayer.setOnCompletionListener(soundCompleteListener);
		silencePlayer.start();
	}

	public int getCurrRepeat() {
		return currRepeat;
	}

	public boolean isRunning() {
		return runningState;
	}

	public int getCurrPosition() {
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
			stopBellPlayer();
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
		private final String phrase;
		private int currClick;
		public ClickPlayer(final int total) {
			this(total, "");
		}
		public ClickPlayer(final int total, final String text) {
			totalClicks = total;
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
					if (!phrase.isEmpty())
						playTTS();
				}
			}
		};
		private void playClick() {
			bellPlayer = MediaPlayer.create(PlayerService.this, R.raw.click);
			bellPlayer.setOnCompletionListener(clickCompleteListener);
			bellPlayer.start();
		}
		private void playTTS() {
			new TtsPlayer(phrase).speak();
		}
	}
}
