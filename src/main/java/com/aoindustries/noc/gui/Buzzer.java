/*
 * noc-gui - Graphical User Interface for Network Operations Center.
 * Copyright (C) 2007-2013, 2016, 2018, 2020, 2021, 2022  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of noc-gui.
 *
 * noc-gui is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * noc-gui is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with noc-gui.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.aoindustries.noc.gui;

import com.aoapps.lang.NullArgumentException;
import com.aoindustries.noc.monitor.common.AlertCategory;
import com.aoindustries.noc.monitor.common.AlertLevel;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.SwingUtilities;

/**
 * Controls the audible buzzer.
 *
 * @author  AO Industries, Inc.
 */
class Buzzer {

	private static final Logger logger = Logger.getLogger(Buzzer.class.getName());

	/**
	 * Gets the number of milliseconds between buzzers for a given alert level and category.
	 */
	private static int getBuzzerInterval(AlertLevel level, AlertCategory category) {
		if(category == AlertCategory.MONITORING) return 2 * 60 * 1000; // Two minutes
		if(category == AlertCategory.SIGNUP) return 10 * 60 * 1000; // Ten minutes
		// Anything else is unexpected, just do 10 minutes, too.
		return 10 * 60 * 1000; // Ten minutes
	}

	/**
	 * Gets the buzzer audio sound to play for the given alert level and category.
	 */
	private static String getBuzzerAudioResource(AlertLevel level, AlertCategory category) {
		if(category == AlertCategory.MONITORING) return "buzzer.wav";
		if(category == AlertCategory.SIGNUP) return "cashregister.wav";
		// Anything else is unexpected, have a little fun
		return "wtf.wav";
	}

	/**
	 * Does not return quickly.  Plays the sound on the current thread.
	 *
	 * Source: http://www.anyexample.com/programming/java/java_play_wav_sound_file.xml
	 */
	@SuppressWarnings("SleepWhileInLoop")
	private static void playSound(String audioResource) throws IOException, UnsupportedAudioFileException, LineUnavailableException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";
		NullArgumentException.checkNotNull(audioResource, "audioResource");

		URL audioUrl = SystemsPane.class.getResource(audioResource);
		if(audioUrl == null) throw new IOException("Audio Resource not found: " + audioResource);

		try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(audioUrl)) {
			try (Clip clip = AudioSystem.getClip()) {
				clip.open(audioInputStream);
				clip.start();
				clip.drain();
				// Wait until stopped playing, but at most 10 seconds to avoid buggy stuff
				int sleepCount = 0;
				while(clip.isRunning()) {
					if(sleepCount < 100) {
						try {
							// Wait 1/10 of a second, since not too much of the clip should play after drain completes
							Thread.sleep(100);
						} catch(InterruptedException e) {
							// Restore the interrupted status
							Thread.currentThread().interrupt();
							InterruptedIOException ioErr = new InterruptedIOException();
							ioErr.initCause(e);
							throw ioErr;
						}
						sleepCount++;
					} else {
						logger.log(Level.WARNING, "Clip still running after 10 seconds, closing now: " + audioResource);
						break;
					}
				}
			}
		}
	}

	private final AlertsPane alertsPane;
	private final BuzzerThread buzzerThread = new BuzzerThread();

	Buzzer(AlertsPane alertsPane) {
		this.alertsPane = alertsPane;
	}

	void controlBuzzer(List<AlertsPane.Alert> history) {
		assert SwingUtilities.isEventDispatchThread() : "Not running in Swing event dispatch thread";

		// Find the highest alertLevel and its associated category in the list
		AlertLevel highestLevel;
		AlertCategory highestCategory;
		{
			AlertLevel _highestLevel = AlertLevel.NONE;
			AlertCategory _highestCategory = AlertCategory.UNCATEGORIZED;
			for(AlertsPane.Alert alert : history) {
				if(alert.newAlertLevel.compareTo(_highestLevel) > 0) {
					_highestLevel = alert.newAlertLevel;
					_highestCategory = alert.newAlertCategory;
				} else if(
					alert.newAlertLevel == _highestLevel
					&& alert.newAlertCategory.compareTo(_highestCategory) > 0
				) {
					_highestCategory = alert.newAlertCategory;
				}
			}
			highestLevel = _highestLevel;
			highestCategory = _highestCategory;
		}
		buzzerThread.setAlertLevel(highestLevel, highestCategory);
	}

	private final Object buzzerLock = new Object();
	private boolean isBuzzing = false;

	/**
	 * Returns immediately (works in a background thread).  Only one buzzer
	 * at a time will play.  If a buzzer is currently being played, the request
	 * is ignored.
	 */
	@SuppressWarnings({"NestedSynchronizedStatement", "UseSpecificCatch", "TooBroadCatch"})
	void playBuzzer(String audioResource) {
		synchronized(buzzerLock) {
			if(!isBuzzing) {
				isBuzzing = true;
				alertsPane.noc.executorService.submit(() -> {
					try {
						playSound(audioResource);
					} catch(ThreadDeath td) {
						throw td;
					} catch(Throwable t) {
						logger.log(Level.SEVERE, null, t);
					} finally {
						synchronized(buzzerLock) {
							isBuzzing = false;
						}
					}
				});
			}
		}
	}

	public void exitApplication() {
		buzzerThread.exitApplication();
	}

	/**
	 * Manages the current buzzer thread, if any, along with its sleep durations
	 * and sound it will play.
	 */
	private class BuzzerThread {

		private final Object sleepLock = new Object();
		private Thread buzzerThread;

		private AlertLevel level;
		private AlertCategory category;

		/**
		 * <p>
		 * Plays sound immediately when the thread is first started.
		 * </p>
		 * <p>
		 * Plays sound immediately when the alert level goes up.
		 * </p>
		 * <p>
		 * Plays sound immediately when in the same alert level, but the category goes up.
		 * </p>
		 * <p>
		 * Plays sound immediately if the new timeout, based on new level and category, would now indicate time to play the sound.
		 * See {@link #getBuzzerInterval(com.aoindustries.noc.monitor.common.AlertLevel, com.aoindustries.noc.monitor.common.AlertCategory)}.
		 * </p>
		 * <p>
		 * Stop the thread when there is nothing to play.  This will cause
		 * the thread to be restarted, along with an immediately play, should there be something to play again.
		 * The idea is to be notified quickly of new things when have already manually acknowledged all exiting.
		 * </p>
		 */
		@SuppressWarnings({"SleepWhileInLoop", "SleepWhileHoldingLock"})
		private void setAlertLevel(AlertLevel newLevel, AlertCategory newCategory) {
			// TODO: Have user selectable alert thresholds by AlertCategory
			boolean runBuzzer = newLevel == AlertLevel.CRITICAL || newLevel == AlertLevel.UNKNOWN;
			synchronized(this) {
				level = newLevel;
				category = newCategory;
				if(runBuzzer) {
					if(buzzerThread == null) {
						// Start a new thread
						buzzerThread = new Thread(() -> {
							final Thread currentThread = Thread.currentThread();
							// Plays sound immediately when the thread is first started.
							// This means we'll always buzz for the initial state, even when things changing rapidly
							AlertLevel lastBuzzedLevel = newLevel;
							AlertCategory lastBuzzedCategory = newCategory;
							long lastBuzzedTime = System.currentTimeMillis();
							playBuzzer(getBuzzerAudioResource(lastBuzzedLevel, lastBuzzedCategory));
							while(!currentThread.isInterrupted()) {
								AlertLevel currentLevel;
								AlertCategory currentCategory;
								synchronized(BuzzerThread.this) {
									if(currentThread != buzzerThread) break;
									currentLevel = level;
									currentCategory = category;
								}
								long currentTime = System.currentTimeMillis();
								int buzzInterval = getBuzzerInterval(currentLevel, currentCategory);
								long sleepTime;
								if(
									// Plays sound immediately when the alert level goes up.
									currentLevel.compareTo(lastBuzzedLevel) > 0
									|| (
										// Plays sound immediately when in the same alert level, but the category goes up.
										currentLevel == lastBuzzedLevel
										&& currentCategory.compareTo(lastBuzzedCategory) > 0
									)
								) {
									// Buzz now if alert level increased
									lastBuzzedLevel = currentLevel;
									lastBuzzedCategory = currentCategory;
									lastBuzzedTime = currentTime;
									playBuzzer(getBuzzerAudioResource(lastBuzzedLevel, lastBuzzedCategory));
									sleepTime = buzzInterval;
								} else {
									// Check for time set to past
									if(currentTime < lastBuzzedTime) {
										lastBuzzedTime = currentTime;
									}
									// Plays sound immediately if the new timeout, based on new level and category, would now indicate time to play the sound.
									long timeSince = currentTime - lastBuzzedTime; // Handle time set to past
									if(timeSince >= buzzInterval) {
										lastBuzzedLevel = currentLevel;
										lastBuzzedCategory = currentCategory;
										lastBuzzedTime = currentTime;
										playBuzzer(getBuzzerAudioResource(lastBuzzedLevel, lastBuzzedCategory));
										sleepTime = buzzInterval;
									} else {
										// Drop lastBuzzedCategory, so an increase in category, while at sale level, will cause a quick alert again
										if(currentLevel == lastBuzzedLevel) lastBuzzedCategory = currentCategory;
										// Sleep the remaining time
										sleepTime = buzzInterval - timeSince;
									}
								}
								try {
									synchronized(sleepLock) {
										sleepLock.wait(sleepTime);
									}
								} catch(InterruptedException err) {
									err.printStackTrace(System.err);
									// Restore the interrupted status
									currentThread.interrupt();
								}
							}
						});
						buzzerThread.start();
					} else {
						// Notify existing thread so it can get updated data
						synchronized(sleepLock) {
							sleepLock.notify(); // notifyAll() not needed since only one thread
						}
					}
				} else {
					// Stop the thread, if running
					buzzerThread = null;
					synchronized(sleepLock) {
						sleepLock.notify(); // notifyAll() not needed since only one thread
					}
				}
			}
		}

		private void exitApplication() {
			synchronized(this) {
				// Stop the thread, if running
				buzzerThread = null;
				synchronized(sleepLock) {
					sleepLock.notify(); // notifyAll() not needed since only one thread
				}
			}
		}
	}
}
