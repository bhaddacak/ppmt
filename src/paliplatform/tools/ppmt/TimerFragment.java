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

import android.app.Fragment;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.graphics.drawable.Drawable;
import android.graphics.Rect;
import android.media.MediaPlayer;

// for debug
//~ import android.widget.Toast;
//~ 		Toast.makeText(getActivity(), ""+remMillis , Toast.LENGTH_SHORT).show();

public class TimerFragment extends Fragment {
	enum State { READY, COUNTDOWN, PAUSED }
	private static final int GUI_UPDATE_INTERVAL = 500;
	private static final int PREPARE_MILLIS = 10000;
	private SharedPreferences prefs;
	private PlayerService playerService;
	private CountDownTimer refreshTimer;
	private TextView timerDisplay;
	private TextView repeatDisplay;
	private ProgressBar timerProgress;
	private int interval;
	private int repeat;
	private boolean hasPreparation;
	private int preMillis;
	private State currState = State.READY;
	private long totalMillis;
	private long remMillis;
	private int lastMillis;

    @Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_timer, container, false);
	}

	@Override
	public void onResume() {
		super.onResume();
		timerDisplay = (TextView) getActivity().findViewById(R.id.timer_display);
		repeatDisplay = (TextView) getActivity().findViewById(R.id.repeat_display);
		timerProgress = (ProgressBar) getActivity().findViewById(R.id.timer_progress);
		prefs = ((MainActivity)getActivity()).getPrefs();
		interval = Integer.parseInt(prefs.getString("pref_interval", "15"));
		repeat = Integer.parseInt(prefs.getString("pref_repeat", "2"));
		hasPreparation = prefs.getBoolean("pref_preparation", true);
		preMillis = hasPreparation ? PREPARE_MILLIS : 0;
		updateStartButton();
		updateTimerDisplay();
	}

	public State getState() {
		return currState;
	}

	public void setState(final State state) {
		currState = state;
	}

	public void resetMillis() {
		totalMillis = preMillis + interval * 60 * 1000 * repeat;
		remMillis = totalMillis;
	}

	public void startRefreshTimer() {
		resetMillis();
		resumeRefreshTimer();
	}

	public void resumeRefreshTimer() {
		playerService = ((MainActivity)getActivity()).getPlayerService();
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
				updateTimerDisplay();
				updateStartButton();
				((MainActivity)getActivity()).setSettingsEnabled(true);
			}
		};
		refreshTimer.start();
	}

	public void stopRefreshTimer() {
		if (refreshTimer != null)
			refreshTimer.cancel();
	}

	private String formatMillis(final long millis) {
		final long sec = millis / 1000;
		final long minPart = sec / 60;
		final long secPart = sec % 60;
		return String.format("%02d:%02d", minPart, secPart);
	}

	public void updateTimerDisplay() {
		if (timerDisplay == null) return;
		if (playerService == null || !playerService.isRunning()) {
			if (currState == State.READY) {
				if (hasPreparation)
					lastMillis =  preMillis;
				else
					lastMillis = interval * 60 * 1000;
			}
		} else {
			if (playerService.getCurrPlayState() == PlayerService.PlayState.BELL) {
				if (playerService.getCurrRepeat() == 0)
					lastMillis = preMillis;
				else
					lastMillis = interval * 60 * 1000;
			} else {
				int duration = playerService.getDuration();
				int position = playerService.getCurrPosition();
				if (duration >0 && position >= 0)
					lastMillis = duration - position;
			}
		}
		timerDisplay.setText(formatMillis(lastMillis));
		updateRepeatDisplay();
		updateProgressBar();
	}

	private void updateRepeatDisplay() {
		if (repeatDisplay == null) return;
		final int curr;
		if (playerService == null || !playerService.isRunning()) {
			curr = hasPreparation ? 0 : 1;
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

	public void updateStartButton() {
		updateStartButton(currState);
	}

	public void updateStartButton(final State state) {
		final Button butStart = (Button) getActivity().findViewById(R.id.button_start);
		final Drawable icon;
		final String text;
		final int color;
		if (state == State.COUNTDOWN) {
			icon = getActivity().getResources().getDrawable(android.R.drawable.ic_media_pause, null);
			text = getActivity().getResources().getString(R.string.pause);
			color = getResources().getColor(android.R.color.holo_orange_light, null);
		} else if (state == State.PAUSED) {
			icon = getActivity().getResources().getDrawable(android.R.drawable.ic_media_play, null);
			text = getActivity().getResources().getString(R.string.resume);
			color = getResources().getColor(android.R.color.holo_green_light, null);
		} else {
			icon = getActivity().getResources().getDrawable(android.R.drawable.ic_media_play, null);
			text = getActivity().getResources().getString(R.string.start);
			color = getResources().getColor(android.R.color.primary_text_dark, null);
		}
		icon.setBounds(new Rect(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight()));
		butStart.setCompoundDrawables(icon, null, null, null);
		butStart.setText(text);
		butStart.setTextColor(color);
	}
}
