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

import android.app.Service;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.Binder;
import android.os.CountDownTimer;
import android.os.Looper;
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
	private CountDownTimer silenceTimer;
	private Looper silenceLooper;
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
		preparation = prefs.getString("pref_preparation", "clicks");
		prepareMillis = preparation.equals("gong") ? 20000
						: preparation.equals("clicks") || preparation.equals("melody") ? 10000
						: 3000;
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
		if (silenceTimer != null)
			silenceTimer.cancel();
		if (silenceLooper != null)
			silenceLooper.quit();
		if (tts != null)
			tts.shutdown();
		super.onDestroy();
	}

	public void startSession() {
		interval = Integer.parseInt(prefs.getString("pref_interval", "15"));
		repeat = Integer.parseInt(prefs.getString("pref_repeat", "2"));
		sound = prefs.getString("pref_sound", "small");
		clickOption = Integer.parseInt(prefs.getString("pref_click", "1"));
		preparation = prefs.getString("pref_preparation", "clicks");
		prepareMillis = preparation.equals("gong") ? 20000
						: preparation.equals("clicks") || preparation.equals("melody") ? 10000
						: 3000;
		currRepeat = 0;
		totalSilenceCount = interval; // we have 1-min silence piece
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
		if (silenceTimer != null)
			silenceTimer.cancel();
		if (silenceLooper != null)
			silenceLooper.quit();
		stopForeground(true);
		runningState = false;
	}

	public void stopPlayers() {
		if (silenceTimer != null)
			silenceTimer.cancel();
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
			silenceLooper = Looper.myLooper();
			silenceLooper.prepare();
			silenceAndRing();
		}
	};

	private void silenceAndRing() {
		if (!runningState) return;
		if (currRepeat == 0) {
			currSilence = 1;
			prepare();
			silenceLooper.loop();
		} else {
			if (currRepeat <= repeat) {
				currSilence = 1;
				silence();
				silenceLooper.loop();
			} else {
				currRepeat = 0;
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
				final String phrase = getResources().getString(R.string.tts_prepare);
				new TtsPlayer(phrase).speak();
			}
		} else {
			switch (clickOption) {
				case 0:
					if (sound.startsWith("tts")) {
						final int num = currRepeat * interval;
						final String lastEnding = currRepeat == repeat ? getResources().getString(R.string.tts_last) : "";
						final String phrase = num + getResources().getString(R.string.tts_loop) + lastEnding;
						new TtsPlayer(phrase).speak();
					} else {
						ring(sound);
					}
					break;
				case 1:
					if (sound.startsWith("tts")) {
						final int num = currRepeat * interval;
						final String lastEnding = currRepeat == repeat ? getResources().getString(R.string.tts_last) : "";
						final String phrase = num + getResources().getString(R.string.tts_loop) + lastEnding;
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
					final String lastEnding = currRepeat == repeat ? getResources().getString(R.string.tts_last) : "";
					final String phrase = num + getResources().getString(R.string.tts_loop) + lastEnding;
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
							: preparation.equals("clicks") ? R.raw.prepare_clicks
							: R.raw.prepare_3sec;
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
			bellPlayer = MediaPlayer.create(PlayerService.this, R.raw.click);
			bellPlayer.setOnCompletionListener(clickCompleteListener);
			bellPlayer.start();
		}
		private void playBell() {
			bellPlayer = MediaPlayer.create(PlayerService.this, bellId);
			bellPlayer.start();
		}
		private void playTTS() {
			new TtsPlayer(phrase).speak();
		}
	}
}
