/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

import helium314.keyboard.event.HapticEvent;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.R;
import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.latin.settings.SettingsValues;

/**
 * This class gathers audio feedback and haptic feedback functions.
 * <p>
 * It offers a consistent and simple interface that allows LatinIME to forget about the
 * complexity of settings and the like.
 */
public final class AudioAndHapticFeedbackManager {
    private AudioManager mAudioManager;
    private Vibrator mVibrator;
    private SoundPool mSoundPool;
    private int mClickStandard, mClickSpace, mClickReturn, mClickDelete;

    private SettingsValues mSettingsValues;
    private boolean mSoundOn;
    private boolean mDoNotDisturb;

    private static final AudioAndHapticFeedbackManager sInstance =
            new AudioAndHapticFeedbackManager();

    public static AudioAndHapticFeedbackManager getInstance() {
        return sInstance;
    }

    private AudioAndHapticFeedbackManager() {
        // Intentional empty constructor for singleton.
    }

    public static void init(final Context context) {
        sInstance.initInternal(context);
    }

    private void initInternal(final Context context) {
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        // our own soft clicks instead of the system key sounds
        mSoundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        mClickStandard = mSoundPool.load(context, R.raw.click_standard, 1);
        mClickSpace = mSoundPool.load(context, R.raw.click_space, 1);
        mClickReturn = mSoundPool.load(context, R.raw.click_return, 1);
        mClickDelete = mSoundPool.load(context, R.raw.click_delete, 1);
    }

    /** A short, light tap: the strongest the phone does well is a crisp click primitive, the weakest a tick. */
    private void softTap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && mVibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            vibrate(VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f).compose());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
        } else {
            vibrate(12);
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private void vibrate(final VibrationEffect effect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mVibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH));
        } else {
            mVibrator.vibrate(effect);
        }
    }

    public void performHapticAndAudioFeedback(
        final int code,
        final View viewToPerformHapticFeedbackOn,
        final HapticEvent hapticEvent
    ) {
        performHapticFeedback(viewToPerformHapticFeedbackOn, hapticEvent);
        performAudioFeedback(code, hapticEvent);
    }

    public boolean hasVibrator() {
        return mVibrator != null && mVibrator.hasVibrator();
    }

    public void vibrate(final long milliseconds) {
        if (mVibrator == null || milliseconds <= 0) {
            return;
        }
        mVibrator.vibrate(milliseconds);
    }

    private boolean reevaluateIfSoundIsOn() {
        if (mSettingsValues == null || !mSettingsValues.mSoundOn || mAudioManager == null || mDoNotDisturb) {
            return false;
        }
        return mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL;
    }

    public void performAudioFeedback(final int code, final HapticEvent hapticEvent) {
        // if mAudioManager is null, we can't play a sound anyway, so return
        if (mAudioManager == null) {
            return;
        }
        if (!mSoundOn) {
            return;
        }
        if (hapticEvent != HapticEvent.KEY_PRESS) {
            return;
        }
        if (mSoundPool != null) {
            final int click = switch (code) {
                case KeyCode.DELETE -> mClickDelete;
                case Constants.CODE_ENTER -> mClickReturn;
                case Constants.CODE_SPACE -> mClickSpace;
                default -> mClickStandard;
            };
            // volume setting < 0 means "system default"
            final float volume = mSettingsValues.mKeypressSoundVolume < 0 ? 0.5f : mSettingsValues.mKeypressSoundVolume;
            if (mSoundPool.play(click, volume, volume, 1, 0, 1f) != 0) return;
        }
        final int sound = switch (code) {
            case KeyCode.DELETE -> AudioManager.FX_KEYPRESS_DELETE;
            case Constants.CODE_ENTER -> AudioManager.FX_KEYPRESS_RETURN;
            case Constants.CODE_SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR;
            default -> AudioManager.FX_KEYPRESS_STANDARD;
        };
        mAudioManager.playSoundEffect(sound, mSettingsValues.mKeypressSoundVolume);
    }

    public void performHapticFeedback(final View viewToPerformHapticFeedbackOn, final HapticEvent hapticEvent) {
        if (!mSettingsValues.mVibrateOn || (mDoNotDisturb && !mSettingsValues.mVibrateInDndMode)) {
            return;
        }
        if (hapticEvent == HapticEvent.NO_HAPTICS) {
            // Avoid surprises with the handling of HapticFeedbackConstants.NO_HAPTICS
            return;
        }
        if (hapticEvent.allowCustomDuration && mSettingsValues.mKeypressVibrationDuration >= 0) {
            vibrate(mSettingsValues.mKeypressVibrationDuration);
            return;
        }
        if (hapticEvent == HapticEvent.KEY_PRESS && hasVibrator()) {
            softTap();
            return;
        }
        // Go ahead with the system default
        if (viewToPerformHapticFeedbackOn != null) {
            viewToPerformHapticFeedbackOn.performHapticFeedback(
                    hapticEvent.feedbackConstant,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }

    public void onSettingsChanged(final SettingsValues settingsValues) {
        mSettingsValues = settingsValues;
        mSoundOn = reevaluateIfSoundIsOn();
    }

    public void onRingerModeChanged(boolean doNotDisturb) {
        mDoNotDisturb = doNotDisturb;
        mSoundOn = reevaluateIfSoundIsOn();
    }
}
