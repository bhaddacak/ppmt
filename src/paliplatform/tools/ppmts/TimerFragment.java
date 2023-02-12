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

import android.app.Fragment;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.graphics.drawable.Drawable;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.preference.PreferenceManager;

// for debug
//~ import android.widget.Toast;
//~ 		Toast.makeText(getActivity(), ""+remMillis , Toast.LENGTH_SHORT).show();

public class TimerFragment extends Fragment {
	enum State { READY, COUNTDOWN, PAUSED }
	private static final int GUI_UPDATE_INTERVAL = 500;
	private MainActivity mainAct;
	private SharedPreferences prefs;
	private PlayerService playerService;
	private CountDownTimer refreshTimer;
	private TextView timerDisplay;
	private TextView repeatDisplay;
	private TextView elapseDisplay;
	private TextView totalDisplay;
	private ProgressBar timerProgress;
	private int interval;
	private int repeat;
	private boolean hasPreparation;
	private int preMillis;
	private State currState = State.READY;
	private long totalMillis = 0;
	private long remMillis;
	private int lastMillis;
	private boolean isShowing;

    @Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		mainAct = ((MainActivity)getActivity());
		return inflater.inflate(R.layout.fragment_timer, container, false);
	}

	@Override
	public void onResume() {
		super.onResume();
		isShowing = true;
		timerDisplay = (TextView) mainAct.findViewById(R.id.timer_display);
		repeatDisplay = (TextView) mainAct.findViewById(R.id.repeat_display);
		elapseDisplay = (TextView) mainAct.findViewById(R.id.elapse_display);
		totalDisplay = (TextView) mainAct.findViewById(R.id.total_display);
		timerProgress = (ProgressBar) mainAct.findViewById(R.id.timer_progress);
		prefs = mainAct.getPrefs();
		interval = Integer.parseInt(prefs.getString("pref_interval", "15"));
		repeat = Integer.parseInt(prefs.getString("pref_repeat", "2"));
		hasPreparation = prefs.getBoolean("pref_preparation", true);
		preMillis = hasPreparation ? 20000 : 0;
		if (playerService == null || !playerService.isRunning() || totalMillis == 0)
			initMillis();
		setupResetButton();
		updateStartButton();
		updateTimerDisplay();
	}

	@Override
	public void onPause() {
		isShowing = false;
		super.onPause();
	}

	public State getState() {
		return currState;
	}

	public void setState(final State state) {
		currState = state;
	}

	public void initMillis() {
		totalMillis = preMillis + interval * PlayerService.ONE_MINUTE_MILLIS * repeat;
		remMillis = totalMillis;
	}

	public void startRefreshTimer() {
		initMillis();
		resumeRefreshTimer();
	}

	public void resumeRefreshTimer() {
		playerService = mainAct.getPlayerService();
		refreshTimer = new CountDownTimer(remMillis, GUI_UPDATE_INTERVAL) {
			@Override
			public void onTick(final long millisUntilFinished) {
				if (playerService.isRunning()) {
					remMillis = millisUntilFinished;
					updateTimerDisplay();
				} else {
					onFinish();
				}
			}
			@Override
			public void onFinish() {
				refreshTimer.cancel();
				refreshTimer = null;
				currState = State.READY;
				remMillis = totalMillis;
				updateTimerDisplay(true);
				updateStartButton();
				mainAct.setSettingsEnabled(true);
				if (prefs.getBoolean("pref_keepscreenon", false))
					mainAct.keepAwake(false);
			}
		};
		refreshTimer.start();
	}

	public void stopRefreshTimer() {
		if (refreshTimer != null)
			refreshTimer.cancel();
	}

	private String formatMillis(final long millis, boolean withHour) {
		final long sec = millis / 1000;
		final long secPart = sec % 60;
		final long hourPart;
		final long minPart;
		final String result;
		if (withHour) {
			hourPart = sec / 3600;
			minPart = (sec % 3600) / 60;
			result = String.format("%02d:%02d:%02d", hourPart, minPart, secPart);
		} else {
			hourPart = 0;
			minPart = sec / 60;
			result = String.format("%02d:%02d", minPart, secPart);
		}
		return result;
	}

	public void updateTimerDisplay() {
		updateTimerDisplay(false);
	}

	public void updateTimerDisplay(final boolean isInit) {
		if (!isShowing) return;
		if (timerDisplay == null) return;
		if (playerService == null || !playerService.isRunning() || isInit) {
			if (currState == State.READY) {
				lastMillis = hasPreparation ? preMillis : interval * PlayerService.ONE_MINUTE_MILLIS;
			}
		} else {
			int duration = playerService.getDuration();
			int position = playerService.getCurrPosition();
			if (duration > 0 && position >= 0 && duration - position >= 0)
				lastMillis = duration - position;
			else
				lastMillis = hasPreparation ? preMillis : interval * PlayerService.ONE_MINUTE_MILLIS;
		}
		timerDisplay.setText(formatMillis(lastMillis, false));
		updateRepeatDisplay();
		updateProgressBar();
		updateElapsingTime();
	}

	private void updateRepeatDisplay() {
		if (repeatDisplay == null) return;
		final int curr;
		if (playerService == null || !playerService.isRunning()) {
			curr = 0;
		} else {
			curr = playerService.getCurrRepeat();
		}
		repeatDisplay.setText(curr + "/" + repeat);
	}

	private void updateProgressBar() {
		final long progress = totalMillis - remMillis;
		timerProgress.setMax((int)totalMillis);
		timerProgress.setProgress((int)progress);
	}

	private void updateElapsingTime() {
		if (elapseDisplay == null || totalDisplay == null) return;
		final long elapsed = totalMillis - remMillis;
		elapseDisplay.setText(formatMillis(elapsed, true));
		totalDisplay.setText(formatMillis(totalMillis, true));
	}

	public void updateStartButton() {
		updateStartButton(currState);
	}

	public void updateStartButton(final State state) {
		if (!isShowing) return;
		final Button butStart = (Button) mainAct.findViewById(R.id.button_start);
		final Drawable icon;
		final String text;
		final int color;
		if (state == State.COUNTDOWN) {
			icon = mainAct.getResources().getDrawable(android.R.drawable.ic_media_pause, null);
			text = mainAct.getResources().getString(R.string.pause);
			color = mainAct.getResources().getColor(android.R.color.holo_orange_light, null);
		} else if (state == State.PAUSED) {
			icon = mainAct.getResources().getDrawable(android.R.drawable.ic_media_play, null);
			text = mainAct.getResources().getString(R.string.resume);
			color = mainAct.getResources().getColor(android.R.color.holo_green_light, null);
		} else {
			icon = mainAct.getResources().getDrawable(android.R.drawable.ic_media_play, null);
			text = mainAct.getResources().getString(R.string.start);
			color = mainAct.getResources().getColor(android.R.color.primary_text_dark, null);
		}
		icon.setBounds(new Rect(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight()));
		butStart.setCompoundDrawables(icon, null, null, null);
		butStart.setText(text);
		butStart.setTextColor(color);
	}

	private void setupResetButton() {
		final ImageButton butReset = (ImageButton) mainAct.findViewById(R.id.button_reset);
		butReset.setOnClickListener(new View.OnClickListener() {
			public void onClick(final View v) {
				mainAct.resetTimer();
			}
		});
		butReset.setOnLongClickListener(new View.OnLongClickListener() {
			public boolean onLongClick(final View v) {
				prefs.edit().clear().commit();
				PreferenceManager.setDefaultValues(mainAct.getApplicationContext(), R.xml.settings, false);
				mainAct.resetTimer();
				onResume();
				return true;
			}
		});
	}
}
