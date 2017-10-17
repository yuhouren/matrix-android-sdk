/*
 * Copyright 2014 OpenMarket Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.androidsdk.call;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.MediaStore;

import org.matrix.androidsdk.R;
import org.matrix.androidsdk.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class manages the call sound.
 * It is in charge of playing ring tones and managing the audio focus.
 */
public class CallSoundsManager {
    private static final String LOG_TAG = CallSoundsManager.class.getSimpleName();

    /**
     * Track the audio focus update.
     */
    public interface OnAudioFocusListener {
        /**
         * Call back indicating new focus events (ex: {@link AudioManager#AUDIOFOCUS_GAIN},
         * {@link AudioManager#AUDIOFOCUS_LOSS}..).
         *
         * @param aFocusEvent the focus event (see {@link AudioManager.OnAudioFocusChangeListener})
         */
        void onFocusChanged(int aFocusEvent);
    }

    /**
     * Track the media statuses.
     */
    public interface OnMediaListener {

        /**
         * The media is ready to be played
         */
        void onMediaReadyToPlay();

        /**
         * The media is playing.
         */
        void onMediaPlay();

        /**
         * The media has been played
         */
        void onMediaCompleted();
    }

    private static CallSoundsManager mSharedInstance = null;
    private Context mContext;

    /**
     * Constructor
     *
     * @param context the context
     */
    public CallSoundsManager(Context context) {
        mContext = context;
    }

    /**
     * Provides the shared instance.
     *
     * @param context the context
     * @return the shared instance
     */
    public static CallSoundsManager getSharedInstance(Context context) {
        if (null == mSharedInstance) {
            mSharedInstance = new CallSoundsManager(context.getApplicationContext());
        }

        return mSharedInstance;
    }

    //==============================================================================================================
    // Focus management
    //==============================================================================================================

    // audio focus management
    private final Set<OnAudioFocusListener> mAudioFocusListeners = new HashSet<>();

    private final AudioManager.OnAudioFocusChangeListener mFocusListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int aFocusEvent) {
            switch (aFocusEvent) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    Log.d(LOG_TAG, "## OnAudioFocusChangeListener(): AUDIOFOCUS_GAIN");
                    // TODO resume voip call (ex: ending GSM call)
                    break;

                case AudioManager.AUDIOFOCUS_LOSS:
                    Log.d(LOG_TAG, "## OnAudioFocusChangeListener(): AUDIOFOCUS_LOSS");
                    // TODO pause voip call (ex: incoming GSM call)
                    break;

                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                    Log.d(LOG_TAG, "## OnAudioFocusChangeListener(): AUDIOFOCUS_GAIN_TRANSIENT");
                    break;

                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    Log.d(LOG_TAG, "## OnAudioFocusChangeListener(): AUDIOFOCUS_LOSS_TRANSIENT");
                    // TODO pause voip call (ex: incoming GSM call)
                    break;

                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    // TODO : continue playing at an attenuated level
                    Log.d(LOG_TAG, "## OnAudioFocusChangeListener(): AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                    break;

                case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                    Log.d(LOG_TAG, "## OnAudioFocusChangeListener(): AUDIOFOCUS_REQUEST_FAILED");
                    break;

                default:
                    break;
            }

            synchronized (LOG_TAG) {
                // notify listeners
                for (OnAudioFocusListener listener : mAudioFocusListeners) {
                    try {
                        listener.onFocusChanged(aFocusEvent);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## onFocusChanged() failed " + e.getMessage());
                    }
                }
            }
        }
    };

    /**
     * Add a focus listener.
     *
     * @param focusListener the listener.
     */
    public void addFocusListener(OnAudioFocusListener focusListener) {
        synchronized (LOG_TAG) {
            mAudioFocusListeners.add(focusListener);
        }
    }

    /**
     * Remove a focus listener.
     *
     * @param focusListener the listener.
     */
    public void removeFocusListener(OnAudioFocusListener focusListener) {
        synchronized (LOG_TAG) {
            mAudioFocusListeners.remove(focusListener);
        }
    }

    //==============================================================================================================
    // Ringtone management management
    //==============================================================================================================

    /**
     * @return the audio manager
     */
    private AudioManager getAudioManager() {
        if (null == mAudioManager) {
            mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        }

        return mAudioManager;
    }

    // audio focus
    private boolean mIsFocusGranted = false;

    private static final int VIBRATE_DURATION = 500; // milliseconds
    private static final int VIBRATE_SLEEP = 1000;  // milliseconds
    private static final long[] VIBRATE_PATTERN = {0, VIBRATE_DURATION, VIBRATE_SLEEP};


    private Ringtone mRingTone;
    private MediaPlayer mMediaPlayer = null;

    // the audio manager
    private AudioManager mAudioManager = null;

    /**
     * Tells that the device is ringing.
     *
     * @return true if the device is ringing
     */
    public boolean isRinging() {
        return (null != mRingTone);
    }

    /**
     * Getter method.
     *
     * @return true is focus is granted, false otherwise.
     */
    public boolean isFocusGranted() {
        return mIsFocusGranted;
    }

    /**
     * Stop any playing sound.
     */
    private void stopSounds() {
        if (null != mRingTone) {
            mRingTone.stop();
            mRingTone = null;
        }

        if (null != mMediaPlayer) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.stop();
            }
            mMediaPlayer = null;
        }
    }

    /**
     * Stop the ringing sound
     */
    public void stopRinging() {
        Log.d(LOG_TAG, "stopRinging");
        stopSounds();

        // stop vibrate
        enableVibrating(false);
    }

    /**
     * Request a permanent audio focus if the focus was not yet granted.
     */
    public void requestAudioFocus() {
        if (!mIsFocusGranted) {
            int focusResult;
            AudioManager audioMgr;

            if ((null != (audioMgr = getAudioManager()))) {
                // Request permanent audio focus for voice call
                focusResult = audioMgr.requestAudioFocus(mFocusListener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);

                if (AudioManager.AUDIOFOCUS_REQUEST_GRANTED == focusResult) {
                    mIsFocusGranted = true;
                    Log.d(LOG_TAG, "## getAudioFocus(): granted");
                } else {
                    mIsFocusGranted = false;
                    Log.w(LOG_TAG, "## getAudioFocus(): refused - focusResult=" + focusResult);
                }
            }
        } else {
            Log.d(LOG_TAG, "## getAudioFocus(): already granted");
        }
    }

    /**
     * Release the audio focus if it was granted.
     */
    public void releaseAudioFocus() {
        if (mIsFocusGranted) {
            Handler handler = new Handler(Looper.getMainLooper());

            // the audio focus is abandoned with delay
            // to let the call to finish properly
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    AudioManager audioMgr;

                    if ((null != (audioMgr = getAudioManager()))) {
                        // release focus
                        int abandonResult = audioMgr.abandonAudioFocus(mFocusListener);

                        if (AudioManager.AUDIOFOCUS_REQUEST_GRANTED == abandonResult) {
                            mIsFocusGranted = false;
                            Log.d(LOG_TAG, "## releaseAudioFocus(): abandonAudioFocus = AUDIOFOCUS_REQUEST_GRANTED");
                        }

                        if (AudioManager.AUDIOFOCUS_REQUEST_FAILED == abandonResult) {
                            Log.d(LOG_TAG, "## releaseAudioFocus(): abandonAudioFocus = AUDIOFOCUS_REQUEST_FAILED");
                        }
                    } else {
                        Log.d(LOG_TAG, "## releaseAudioFocus(): failure - invalid AudioManager");
                    }
                }
            }, 300);
        }
    }

    /**
     * Start the ringing sound
     */
    public void startRinging(int resId, String filename) {
        Log.d(LOG_TAG, "startRinging");

        if (null != mRingTone) {
            Log.d(LOG_TAG, "ring tone already ringing");
            return;
        }

        // stop any playing ringtone
        stopSounds();

        // use the ringTone to manage sound volume properly
        mRingTone = getRingTone(mContext, resId, filename, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));

        if (null != mRingTone) {
            setSpeakerphoneOn(false, true);
            mRingTone.play();
        } else {
            Log.e(LOG_TAG, "startRinging : fail to retrieve RING_TONE_START_RINGING");
        }

        // start vibrate
        enableVibrating(true);
    }

    /**
     * Enable the vibrate mode.
     *
     * @param aIsVibrateEnabled true to force vibrate, false to stop vibrate.
     */
    private void enableVibrating(boolean aIsVibrateEnabled) {
        Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);

        if ((null != vibrator) && vibrator.hasVibrator()) {
            if (aIsVibrateEnabled) {
                vibrator.vibrate(VIBRATE_PATTERN, 0 /*repeat till stop*/);
                Log.d(LOG_TAG, "## startVibrating(): Vibrate started");
            } else {
                vibrator.cancel();
                Log.d(LOG_TAG, "## startVibrating(): Vibrate canceled");
            }
        } else {
            Log.w(LOG_TAG, "## startVibrating(): vibrator access failed");
        }
    }

    /**
     * Start a sound.
     */
    public void startSound(int resId, boolean isLooping, final OnMediaListener listener) {
        Log.d(LOG_TAG, "startSound");

        stopSounds();

        mMediaPlayer = MediaPlayer.create(mContext, resId);

        if (null != mMediaPlayer) {
            mMediaPlayer.setLooping(isLooping);

            if (null != listener) {
                listener.onMediaReadyToPlay();
            }

            mMediaPlayer.start();

            if (null != listener) {
                listener.onMediaPlay();
            }

            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    if (null != listener) {
                        listener.onMediaCompleted();
                    }
                }
            });

        } else {
            Log.e(LOG_TAG, "startSound : failed");
        }
    }

    //==============================================================================================================
    // resid / filenime to ringtone
    //==============================================================================================================

    private static final Map<String, Uri> mRingtoneUrlByFileName = new HashMap<>();

    /**
     * Provide a ringtone uri from a resource and a filename.
     *
     * @param context  the conext
     * @param resId    The audio resource.
     * @param filename the audio filename
     * @return the ringtone uri
     */
    private static Uri getRingToneUri(Context context, int resId, String filename) {
        Uri ringToneUri = mRingtoneUrlByFileName.get(filename);
        // test if the ring tone has been cached

        if (null != ringToneUri) {
            // check if the file exists
            try {
                File ringFile = new File(ringToneUri.toString());

                // check if the file exists
                if ((null != ringFile) && ringFile.exists() && ringFile.canRead()) {
                    // provide it
                    return ringToneUri;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getRingToneUri() failed " + e.getMessage());
            }
        }

        try {
            File directory = new File(Environment.getExternalStorageDirectory(), "/" + context.getApplicationContext().getPackageName().hashCode() + "/Audio/");

            // create the directory if it does not exist
            if (!directory.exists()) {
                directory.mkdirs();
            }

            File file = new File(directory + "/", filename);

            // if the file exists, check if the resource has been created
            if (file.exists()) {
                Cursor cursor = context.getContentResolver().query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Audio.Media._ID},
                        MediaStore.Audio.Media.DATA + "=? ",
                        new String[]{file.getAbsolutePath()}, null);

                if ((null != cursor) && cursor.moveToFirst()) {
                    int id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID));
                    ringToneUri = Uri.withAppendedPath(Uri.parse("content://media/external/audio/media"), "" + id);
                }

                if (null != cursor) {
                    cursor.close();
                }
            }

            // the Uri has been retrieved
            if (null == ringToneUri) {
                // create the file
                if (!file.exists()) {
                    try {
                        byte[] readData = new byte[1024];
                        InputStream fis = context.getResources().openRawResource(resId);
                        FileOutputStream fos = new FileOutputStream(file);
                        int i = fis.read(readData);

                        while (i != -1) {
                            fos.write(readData, 0, i);
                            i = fis.read(readData);
                        }

                        fos.close();
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## getRingToneUri():  Exception1 Msg=" + e.getMessage());
                    }
                }

                // and the resource Uri
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DATA, file.getAbsolutePath());
                values.put(MediaStore.MediaColumns.TITLE, filename);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/ogg");
                values.put(MediaStore.MediaColumns.SIZE, file.length());
                values.put(MediaStore.Audio.Media.ARTIST, R.string.app_name);
                values.put(MediaStore.Audio.Media.IS_RINGTONE, true);
                values.put(MediaStore.Audio.Media.IS_NOTIFICATION, true);
                values.put(MediaStore.Audio.Media.IS_ALARM, true);
                values.put(MediaStore.Audio.Media.IS_MUSIC, true);

                ringToneUri = context.getContentResolver().insert(MediaStore.Audio.Media.getContentUriForPath(file.getAbsolutePath()), values);
            }

            if (null != ringToneUri) {
                mRingtoneUrlByFileName.put(filename, ringToneUri);
                return ringToneUri;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getRingToneUri():  Exception2 Msg=" + e.getLocalizedMessage());
        }

        return null;
    }

    /**
     * Retrieve a ringtone from an uri
     *
     * @param context     the context
     * @param ringToneUri the ringtone URI
     * @return the ringtone
     */
    private static Ringtone uriToRingTone(Context context, Uri ringToneUri) {
        if (null != ringToneUri) {
            try {
                return RingtoneManager.getRingtone(context, ringToneUri);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## uriToRingTone() failed " + e.getMessage());
            }
        }

        return null;
    }

    /**
     * Provide a ringtone from a resource and a filename.
     * The audio file must have a ANDROID_LOOP metatada set to true to loop the sound.
     *
     * @param context            the context
     * @param resId              The audio resource.
     * @param filename           the audio filename
     * @param defaultRingToneUri
     * @return a RingTone, null if the operation fails.
     */
    private static Ringtone getRingTone(Context context, int resId, String filename, Uri defaultRingToneUri) {
        Ringtone ringtone = uriToRingTone(context, getRingToneUri(context, resId, filename));

        if (null == ringtone) {
            ringtone = uriToRingTone(context, defaultRingToneUri);
        }

        Log.d(LOG_TAG, "getRingTone() : resId " + resId + " filename " + filename + " defaultRingToneUri " + defaultRingToneUri + " returns " + ringtone);

        return ringtone;
    }

    //==============================================================================================================
    // speakers management
    //==============================================================================================================

    // save the audio statuses
    private Integer mAudioMode = null;
    private Boolean mIsSpeakerOn = null;

    /**
     * Back up the current audio config.
     */
    private void backupAudioConfig() {
        if (null == mAudioMode) {
            AudioManager audioManager = getAudioManager();

            mAudioMode = audioManager.getMode();
            mIsSpeakerOn = audioManager.isSpeakerphoneOn();
        }
    }

    /**
     * Restore the audio config.
     */
    private void restoreAudioConfig() {
        // ensure that something has been saved
        if ((null != mAudioMode) && (null != mIsSpeakerOn)) {
            AudioManager audioManager = getAudioManager();

            if (mAudioMode != audioManager.getMode()) {
                audioManager.setMode(mAudioMode);
            }

            if (mIsSpeakerOn != audioManager.isSpeakerphoneOn()) {
                audioManager.setSpeakerphoneOn(mIsSpeakerOn);
            }

            // stop the bluetooth
            if (audioManager.isBluetoothScoOn()) {
                audioManager.stopBluetoothSco();
                audioManager.setBluetoothScoOn(false);
            }

            mAudioMode = null;
            mIsSpeakerOn = null;
        }
    }

    /**
     * Set the speakerphone ON or OFF.
     *
     * @param isOn true to enable the speaker (ON), false to disable it (OFF)
     */
    public void setCallSpeakerphoneOn(boolean isOn) {
        setSpeakerphoneOn(true, isOn);
    }

    /**
     * Save the current speaker status and the audio mode, before updating those
     * values.
     * The audio mode depends on if there is a call in progress.
     * If audio mode set to {@link AudioManager#MODE_IN_COMMUNICATION} and
     * a media player is in ON, the media player will reduce its audio level.
     *
     * @param isInCall    true when the speaker is updated during call.
     * @param isSpeakerOn true to turn on the speaker (false to turn it off)
     */
    private void setSpeakerphoneOn(boolean isInCall, boolean isSpeakerOn) {
        Log.d(LOG_TAG, "setCallSpeakerphoneOn " + isSpeakerOn);

        backupAudioConfig();

        try {
            AudioManager audioManager = getAudioManager();

            int audioMode = isInCall ? AudioManager.MODE_IN_COMMUNICATION : AudioManager.MODE_RINGTONE;

            if (audioManager.getMode() != audioMode) {
                audioManager.setMode(audioMode);
            }

            if (!isSpeakerOn) {
                try {
                    if (HeadsetConnectionReceiver.isBTHeadsetPlugged()) {
                        audioManager.startBluetoothSco();
                        audioManager.setBluetoothScoOn(true);
                    } else if (audioManager.isBluetoothScoOn()) {
                        audioManager.stopBluetoothSco();
                        audioManager.setBluetoothScoOn(false);
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## setSpeakerphoneOn() failed " + e.getMessage());
                }
            }

            if (isSpeakerOn != audioManager.isSpeakerphoneOn()) {
                audioManager.setSpeakerphoneOn(isSpeakerOn);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## setSpeakerphoneOn() failed " + e.getMessage());
            restoreAudioConfig();
        }
    }

    /**
     * Toggle the speaker
     */
    public void toggleSpeaker() {
        AudioManager audioManager = getAudioManager();
        audioManager.setSpeakerphoneOn(!audioManager.isSpeakerphoneOn());
    }
}