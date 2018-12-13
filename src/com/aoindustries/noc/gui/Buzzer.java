/*
 * Copyright 2007-2013, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.gui;

import com.aoindustries.io.IoUtils;
import com.aoindustries.lang.NullArgumentException;
import com.aoindustries.noc.monitor.common.AlertCategory;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.util.BufferManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
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
	private static void playSound(byte[] audioFile) throws IOException, UnsupportedAudioFileException, LineUnavailableException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";
		NullArgumentException.checkNotNull(audioFile, "audioFile");

		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(audioFile));
		AudioFormat format = audioInputStream.getFormat();
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
		SourceDataLine auline = (SourceDataLine) AudioSystem.getLine(info);
		auline.open(format);
		auline.start();
		try {
			byte[] buff = BufferManager.getBytes();
			try {
				int nBytesRead;
				while ((nBytesRead = audioInputStream.read(buff)) != -1) {
					/*if(nBytesRead>0)*/ auline.write(buff, 0, nBytesRead);
				}
			} finally {
				BufferManager.release(buff, false);
			}
		} finally {
			auline.drain();
			auline.close();
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
	void playBuzzer(String audioResource) {
		synchronized(buzzerLock) {
			if(!isBuzzing) {
				isBuzzing = true;
				alertsPane.noc.executorService.submit(() -> {
					try {
						byte[] buffered;
						try (InputStream in = SystemsPane.class.getResourceAsStream(audioResource)) {
							buffered = IoUtils.readFully(in);
						}
						playSound(buffered);
					} catch(RuntimeException | IOException | UnsupportedAudioFileException | LineUnavailableException err) {
						logger.log(Level.SEVERE, null, err);
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
							final Thread thisThread = Thread.currentThread();
							// Plays sound immediately when the thread is first started.
							// This means we'll always buzz for the initial state, even when things changing rapidly
							AlertLevel lastBuzzedLevel = newLevel;
							AlertCategory lastBuzzedCategory = newCategory;
							long lastBuzzedTime = System.currentTimeMillis();
							playBuzzer(getBuzzerAudioResource(lastBuzzedLevel, lastBuzzedCategory));
							while(true) {
								AlertLevel currentLevel;
								AlertCategory currentCategory;
								synchronized(BuzzerThread.this) {
									if(thisThread != buzzerThread) break;
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
								}
								// Check for time set to past
								else if(currentTime < lastBuzzedTime) {
									// Do not play sound, but reset timer
									lastBuzzedTime = currentTime;
									sleepTime = buzzInterval;
								} else {
									// Plays sound immediately if the new timeout, based on new level and category, would now indicate time to play the sound.
									long timeSince = currentTime - lastBuzzedTime; // Handle time set to past
									if(timeSince >= buzzInterval) {
										lastBuzzedLevel = currentLevel;
										lastBuzzedCategory = currentCategory;
										lastBuzzedTime = currentTime;
										playBuzzer(getBuzzerAudioResource(lastBuzzedLevel, lastBuzzedCategory));
										sleepTime = buzzInterval;
									} else {
										// Sleep the remaining time
										sleepTime = buzzInterval - timeSince;
									}
								}
								try {
									Thread.sleep(sleepTime);
								} catch(InterruptedException err) {
									// Normal during thread shutdown
								}
							}
						});
						buzzerThread.start();
					} else {
						// Interrupt existing thread so it can get updated data
						buzzerThread.interrupt();
					}
				} else {
					// Stop the thread, if running
					if(buzzerThread != null) {
						buzzerThread.interrupt();
						buzzerThread = null;
					}
				}
			}
		}

		private void exitApplication() {
			synchronized(this) {
				// Stop the thread, if running
				if(buzzerThread != null) {
					buzzerThread.interrupt();
					buzzerThread = null;
				}
			}
		}
	}
}
