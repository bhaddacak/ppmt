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
import android.os.IBinder;
import android.os.Binder;
import android.os.CountDownTimer;
import android.media.MediaPlayer;
import android.media.AudioManager;
import android.preference.PreferenceManager;

// for debugging
//~ import android.widget.Toast;
//~ 		Toast.makeText(this, "started", Toast.LENGTH_SHORT).show();

public class PlayerService extends Service {
	enum PlayState { SILENCE, BELL }
	private static final int NOTI_ID = 1;
	private Notification.Builder notiBuilder;
	private SharedPreferences prefs;
	private IBinder playerServiceBinder;
	private MediaPlayer bellPlayer;
	private MediaPlayer silencePlayer;
	private int interval;
	private int repeat;
	private String sound;
	private int clickOption;
	private boolean hasPreparation;
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
		super.onDestroy();
	}

	public void startSession() {
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		interval = Integer.parseInt(prefs.getString("pref_interval", "15"));
		repeat = Integer.parseInt(prefs.getString("pref_repeat", "2"));
		sound = prefs.getString("pref_sound", "small");
		clickOption = Integer.parseInt(prefs.getString("pref_click", "1"));
		hasPreparation = prefs.getBoolean("pref_preparation", true);
		currRepeat = hasPreparation ? 0 : 1;
		totalSilenceCount = interval; // we have 1-min silence piece
		runningState = true;
		startTask();
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
		try {
			if (bellPlayer != null)
				bellPlayer.stop();
			if (silencePlayer != null)
				silencePlayer.stop();
		} catch (IllegalStateException e) {
		}
	}

	private void startTask() {
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
			currSilence = 1;
			prepare();
		} else {
			if (currRepeat <= repeat) {
				currSilence = 1;
				silence();
			} else {
				currRepeat = hasPreparation ? 0 : 1;
				stopSession();
			}
		}
	}

	private MediaPlayer.OnCompletionListener bellCompleteListener = new MediaPlayer.OnCompletionListener() {
		@Override
		public void onCompletion(final MediaPlayer mp) {
			currPlayState = PlayState.BELL;
			if (currRepeat == 0) {
				ring(sound);
			} else {
				switch (clickOption) {
					case 0:
						ring(sound);
						break;
					case 1:
						if (currRepeat == repeat)
							new LeadingClickPlayer(2, getBell(sound)).play();
						else
							ring(sound);
						break;
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
						final int clickCount = currRepeat % clickOption;
						new LeadingClickPlayer((clickCount==0?clickOption:clickCount), getBell(sound)).play();
						break;
				}
			}
			currRepeat++;
			silenceAndRing();
		}
	};

	private void prepare() {
		currPlayState = PlayState.SILENCE;
		silencePlayer = MediaPlayer.create(this, R.raw.prepare);
		silencePlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		silencePlayer.setOnCompletionListener(bellCompleteListener);
		silencePlayer.start();
	}

	private MediaPlayer.OnCompletionListener silenceCompleteListener = new MediaPlayer.OnCompletionListener() {
		@Override
		public void onCompletion(final MediaPlayer mp) {
			currSilence++;
			silence();
		}
	};

	private void silence() {
		currPlayState = PlayState.SILENCE;
		silencePlayer = MediaPlayer.create(this, R.raw.silence);
		silencePlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		if (currSilence < totalSilenceCount)
			silencePlayer.setOnCompletionListener(silenceCompleteListener);
		else
			silencePlayer.setOnCompletionListener(bellCompleteListener);
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
		int pos = 0;
		try {
			if (silencePlayer != null) {
				final int base = currRepeat == 0 ? 0 : (currSilence - 1) * 60 * 1000;
				pos = base + silencePlayer.getCurrentPosition();
			}
		} catch (IllegalStateException e) {
			pos = -1;
		}
		return pos;
	}

	public int getDuration() {
		if (currPlayState == PlayState.BELL) return -1;
		int dur = 0;
		try {
			if (silencePlayer != null) {
				final int base = currRepeat == 0 ? 1 : totalSilenceCount; 
				dur = base * silencePlayer.getDuration();
			}
		} catch (IllegalStateException e) {
			dur = -1;
		}
		return dur;
	}

	private void ring(final String bell) {
		bellPlayer = MediaPlayer.create(this, getBell(bell));
		bellPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
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

	public class LeadingClickPlayer {
		private final int totalClicks;
		private final int bellId;
		private int currClick;
		public LeadingClickPlayer(final int total, final int bell) {
			totalClicks = total;
			bellId = bell;
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
					playBell();
				}
			}
		};
		private void playClick() {
			bellPlayer = MediaPlayer.create(PlayerService.this, R.raw.click);
			bellPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			bellPlayer.setOnCompletionListener(clickCompleteListener);
			bellPlayer.start();
		}
		private void playBell() {
			bellPlayer = MediaPlayer.create(PlayerService.this, bellId);
			bellPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			bellPlayer.start();
		}
	}
}

