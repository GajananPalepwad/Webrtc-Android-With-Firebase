/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package com.gn4k.videocall.utils;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.preference.PreferenceManager;

import com.gn4k.videocall.utils.BluetoothManager;

import org.webrtc.ThreadUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


/**
 * AppRTCAudioManager manages all audio related parts of the AppRTC demo.
 */
@SuppressLint("MissingPermission")
public class RTCAudioManager {
    private static final String TAG = RTCAudioManager.class.getSimpleName();
    private static final String SPEAKERPHONE_AUTO = "auto";
    private static final String SPEAKERPHONE_TRUE = "true";
    private static final String SPEAKERPHONE_FALSE = "false";
    private final Context apprtcContext;
    // Contains speakerphone setting: auto, true or false
    private final String useSpeakerphone;
    // Handles all tasks related to Bluetooth headset devices.
    private final BluetoothManager bluetoothManager;
    private final android.media.AudioManager audioManager;
    private AudioManagerEvents audioManagerEvents;
    private AudioManagerState amState;
    private int savedAudioMode = android.media.AudioManager.MODE_INVALID;
    private boolean savedIsSpeakerPhoneOn = false;
    private boolean savedIsMicrophoneMute = false;
    private boolean hasWiredHeadset = false;
    // Default audio device; speaker phone for video calls or earpiece for audio
    // only calls.
    private AudioDevice defaultAudioDevice;
    // Contains the currently selected audio device.
    // This device is changed automatically using a certain scheme where e.g.
    // a wired headset "wins" over speaker phone. It is also possible for a
    // user to explicitly select a device (and overrid any predefined scheme).
    // See |userSelectedAudioDevice| for details.
    private AudioDevice selectedAudioDevice;
    // Contains the user-selected audio device which overrides the predefined
    // selection scheme.
    // TODO(henrika): always set to AudioDevice.NONE today. Add support for
    // explicit selection based on choice by userSelectedAudioDevice.
    private AudioDevice userSelectedAudioDevice;
    // Proximity sensor object. It measures the proximity of an object in cm
    // relative to the view screen of a device and can therefore be used to
    // assist device switching (close to ear <=> use headset earpiece if
    // available, far from ear <=> use speaker phone).
    private ProximitySensor proximitySensor;
    // Contains a list of available audio devices. A Set collection is used to
    // avoid duplicate elements.
    private Set<AudioDevice> audioDevices = new HashSet<>();
    // Broadcast receiver for wired headset intent broadcasts.
    private final BroadcastReceiver wiredHeadsetReceiver;
    // Callback method for changes in audio focus.
    private android.media.AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;

    private RTCAudioManager(Context context) {
        ThreadUtils.checkIsOnMainThread();
        apprtcContext = context;
        audioManager = ((android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
        bluetoothManager = BluetoothManager.create(context, this);
        wiredHeadsetReceiver = new WiredHeadsetReceiver();
        amState = AudioManagerState.UNINITIALIZED;

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        useSpeakerphone = sharedPreferences.getString("speakerphone_preference", "auto");
        if (useSpeakerphone.equals(SPEAKERPHONE_FALSE)) {
            defaultAudioDevice = AudioDevice.EARPIECE;
        } else {
            defaultAudioDevice = AudioDevice.SPEAKER_PHONE;
        }

        // Create and initialize the proximity sensor.
        // Tablet devices (e.g. Nexus 7) does not support proximity sensors.
        // Note that, the sensor will not be active until start() has been called.
        proximitySensor = ProximitySensor.create(context, new Runnable() {
            // This method will be called each time a state change is detected.
            // Example: user holds his hand over the device (closer than ~5 cm),
            // or removes his hand from the device.
            public void run() {
                onProximitySensorChangedState();
            }
        });

    }

    /**
     * Construction.
     */
    public static RTCAudioManager create(Context context) {
        return new RTCAudioManager(context);
    }

    /**
     * This method is called when the proximity sensor reports a state change,
     * e.g. from "NEAR to FAR" or from "FAR to NEAR".
     */
    private void onProximitySensorChangedState() {
        if (!useSpeakerphone.equals(SPEAKERPHONE_AUTO)) {
            return;
        }

        // The proximity sensor should only be activated when there are exactly two
        // available audio devices.
        if (audioDevices.size() == 2 && audioDevices.contains(AudioDevice.EARPIECE) && audioDevices.contains(AudioDevice.SPEAKER_PHONE)) {
            if (proximitySensor.sensorReportsNearState()) {
                // Sensor reports that a "handset is being held up to a person's ear",
                // or "something is covering the light sensor".
                setAudioDeviceInternal(AudioDevice.EARPIECE);
            } else {
                // Sensor reports that a "handset is removed from a person's ear", or
                // "the light sensor is no longer covered".
                setAudioDeviceInternal(AudioDevice.SPEAKER_PHONE);
            }
        }
    }

    public void start(AudioManagerEvents audioManagerEvents) {
        ThreadUtils.checkIsOnMainThread();
        if (amState == AudioManagerState.RUNNING) {
            return;
        }
        // TODO(henrika): perhaps call new method called preInitAudio() here if UNINITIALIZED.

        this.audioManagerEvents = audioManagerEvents;
        amState = AudioManagerState.RUNNING;

        // Store current audio state so we can restore it when stop() is called.
        savedAudioMode = audioManager.getMode();
        savedIsSpeakerPhoneOn = audioManager.isSpeakerphoneOn();
        savedIsMicrophoneMute = audioManager.isMicrophoneMute();
        hasWiredHeadset = hasWiredHeadset();

        // Create an AudioManager.OnAudioFocusChangeListener instance.
        audioFocusChangeListener = new android.media.AudioManager.OnAudioFocusChangeListener() {
            // Called on the listener to notify if the audio focus for this listener has been changed.
            // The |focusChange| value indicates whether the focus was gained, whether the focus was lost,
            // and whether that loss is transient, or whether the new focus holder will hold it for an
            // unknown amount of time.
            // TODO(henrika): possibly extend support of handling audio-focus changes. Only contains
            // logging for now.
            @Override
            public void onAudioFocusChange(int focusChange) {
                String typeOfChange = "AUDIOFOCUS_NOT_DEFINED";
                switch (focusChange) {
                    case android.media.AudioManager.AUDIOFOCUS_GAIN:
                        typeOfChange = "AUDIOFOCUS_GAIN";
                        break;
                    case android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                        typeOfChange = "AUDIOFOCUS_GAIN_TRANSIENT";
                        break;
                    case android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                        typeOfChange = "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE";
                        break;
                    case android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                        typeOfChange = "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK";
                        break;
                    case android.media.AudioManager.AUDIOFOCUS_LOSS:
                        typeOfChange = "AUDIOFOCUS_LOSS";
                        break;
                    case android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        typeOfChange = "AUDIOFOCUS_LOSS_TRANSIENT";
                        break;
                    case android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        typeOfChange = "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK";
                        break;
                    default:
                        typeOfChange = "AUDIOFOCUS_INVALID";
                        break;
                }
            }
        };

        // Request audio playout focus (without ducking) and install listener for changes in focus.
        int result = audioManager.requestAudioFocus(audioFocusChangeListener, android.media.AudioManager.STREAM_VOICE_CALL, android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

        // Start by setting MODE_IN_COMMUNICATION as default audio mode. It is
        // required to be in this mode when playout and/or recording starts for
        // best possible VoIP performance.
        audioManager.setMode(android.media.AudioManager.MODE_IN_COMMUNICATION);

        // Always disable microphone mute during a WebRTC call.
        setMicrophoneMute(false);

        // Set initial device states.
        userSelectedAudioDevice = AudioDevice.NONE;
        selectedAudioDevice = AudioDevice.NONE;
        audioDevices.clear();

        // Initialize and start Bluetooth if a BT device is available or initiate
        // detection of new (enabled) BT devices.
        bluetoothManager.start();

        // Do initial selection of audio device. This setting can later be changed
        // either by adding/removing a BT or wired headset or by covering/uncovering
        // the proximity sensor.
        updateAudioDeviceState();

        // Register receiver for broadcast intents related to adding/removing a
        // wired headset.
        registerReceiver(wiredHeadsetReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
    }

    @SuppressLint("WrongConstant")
    public void stop() {
        ThreadUtils.checkIsOnMainThread();
        if (amState != AudioManagerState.RUNNING) {
            return;
        }
        amState = AudioManagerState.UNINITIALIZED;

        unregisterReceiver(wiredHeadsetReceiver);

        bluetoothManager.stop();

        // Restore previously stored audio states.
        setSpeakerphoneOn(savedIsSpeakerPhoneOn);
        setMicrophoneMute(savedIsMicrophoneMute);
        audioManager.setMode(savedAudioMode);

        // Abandon audio focus. Gives the previous focus owner, if any, focus.
        audioManager.abandonAudioFocus(audioFocusChangeListener);
        audioFocusChangeListener = null;

        if (proximitySensor != null) {
            proximitySensor.stop();
            proximitySensor = null;
        }

        audioManagerEvents = null;
    }

    /**
     * Changes selection of the currently active audio device.
     */
    private void setAudioDeviceInternal(AudioDevice device) {
        switch (device) {
            case SPEAKER_PHONE:
                setSpeakerphoneOn(true);
                break;
            case EARPIECE:
            case BLUETOOTH:
            case WIRED_HEADSET:
                setSpeakerphoneOn(false);
                break;
            default:
                break;
        }
        selectedAudioDevice = device;
    }

    /**
     * Changes default audio device.
     * TODO(henrika): add usage of this method in the AppRTCMobile client.
     */
    public void setDefaultAudioDevice(AudioDevice defaultDevice) {
        ThreadUtils.checkIsOnMainThread();
        switch (defaultDevice) {
            case SPEAKER_PHONE:
                defaultAudioDevice = defaultDevice;
                break;
            case EARPIECE:
                if (hasEarpiece()) {
                    defaultAudioDevice = defaultDevice;
                } else {
                    defaultAudioDevice = AudioDevice.SPEAKER_PHONE;
                }
                break;
            default:
                break;
        }
        updateAudioDeviceState();
    }

    /**
     * Changes selection of the currently active audio device.
     */
    public void selectAudioDevice(AudioDevice device) {
        ThreadUtils.checkIsOnMainThread();
        if (!audioDevices.contains(device)) {
        }
        userSelectedAudioDevice = device;
        updateAudioDeviceState();
    }

    /**
     * Returns current set of available/selectable audio devices.
     */
    public Set<AudioDevice> getAudioDevices() {
        ThreadUtils.checkIsOnMainThread();
        return Collections.unmodifiableSet(new HashSet<>(audioDevices));
    }

    /**
     * Returns the currently selected audio device.
     */
    public AudioDevice getSelectedAudioDevice() {
        ThreadUtils.checkIsOnMainThread();
        return selectedAudioDevice;
    }

    /**
     * Helper method for receiver registration.
     */
    private void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        apprtcContext.registerReceiver(receiver, filter);
    }

    /**
     * Helper method for unregistration of an existing receiver.
     */
    private void unregisterReceiver(BroadcastReceiver receiver) {
        apprtcContext.unregisterReceiver(receiver);
    }

    /**
     * Sets the speaker phone mode.
     */
    private void setSpeakerphoneOn(boolean on) {
        boolean wasOn = audioManager.isSpeakerphoneOn();
        if (wasOn == on) {
            return;
        }
        audioManager.setSpeakerphoneOn(on);
    }

    /**
     * Sets the microphone mute state.
     */
    private void setMicrophoneMute(boolean on) {
        boolean wasMuted = audioManager.isMicrophoneMute();
        if (wasMuted == on) {
            return;
        }
        audioManager.setMicrophoneMute(on);
    }

    /**
     * Gets the current earpiece state.
     */
    private boolean hasEarpiece() {
        return apprtcContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    /**
     * Checks whether a wired headset is connected or not.
     * This is not a valid indication that audio playback is actually over
     * the wired headset as audio routing depends on other conditions. We
     * only use it as an early indicator (during initialization) of an attached
     * wired headset.
     */
    @Deprecated
    private boolean hasWiredHeadset() {
        @SuppressLint("WrongConstant") final AudioDeviceInfo[] devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_ALL);
        for (AudioDeviceInfo device : devices) {
            final int type = device.getType();
            if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                return true;
            } else if (type == AudioDeviceInfo.TYPE_USB_DEVICE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Updates list of possible audio devices and make new device selection.
     * TODO(henrika): add unit test to verify all state transitions.
     */
    public void updateAudioDeviceState() {
        ThreadUtils.checkIsOnMainThread();

        // Check if any Bluetooth headset is connected. The internal BT state will
        // change accordingly.
        // TODO(henrika): perhaps wrap required state into BT manager.
        if (bluetoothManager.getState() == BluetoothManager.State.HEADSET_AVAILABLE || bluetoothManager.getState() == BluetoothManager.State.HEADSET_UNAVAILABLE || bluetoothManager.getState() == BluetoothManager.State.SCO_DISCONNECTING) {
            bluetoothManager.updateDevice();
        }

        // Update the set of available audio devices.
        Set<AudioDevice> newAudioDevices = new HashSet<>();

        if (bluetoothManager.getState() == BluetoothManager.State.SCO_CONNECTED || bluetoothManager.getState() == BluetoothManager.State.SCO_CONNECTING || bluetoothManager.getState() == BluetoothManager.State.HEADSET_AVAILABLE) {
            newAudioDevices.add(AudioDevice.BLUETOOTH);
        }

        if (hasWiredHeadset) {
            // If a wired headset is connected, then it is the only possible option.
            newAudioDevices.add(AudioDevice.WIRED_HEADSET);
        } else {
            // No wired headset, hence the audio-device list can contain speaker
            // phone (on a tablet), or speaker phone and earpiece (on mobile phone).
            newAudioDevices.add(AudioDevice.SPEAKER_PHONE);
            if (hasEarpiece()) {
                newAudioDevices.add(AudioDevice.EARPIECE);
            }
        }
        // Store state which is set to true if the device list has changed.
        boolean audioDeviceSetUpdated = !audioDevices.equals(newAudioDevices);
        // Update the existing audio device set.
        audioDevices = newAudioDevices;
        // Correct user selected audio devices if needed.
        if (bluetoothManager.getState() == BluetoothManager.State.HEADSET_UNAVAILABLE && userSelectedAudioDevice == AudioDevice.BLUETOOTH) {
            // If BT is not available, it can't be the user selection.
            userSelectedAudioDevice = AudioDevice.NONE;
        }
        if (hasWiredHeadset && userSelectedAudioDevice == AudioDevice.SPEAKER_PHONE) {
            // If user selected speaker phone, but then plugged wired headset then make
            // wired headset as user selected device.
            userSelectedAudioDevice = AudioDevice.WIRED_HEADSET;
        }
        if (!hasWiredHeadset && userSelectedAudioDevice == AudioDevice.WIRED_HEADSET) {
            // If user selected wired headset, but then unplugged wired headset then make
            // speaker phone as user selected device.
            userSelectedAudioDevice = AudioDevice.SPEAKER_PHONE;
        }

        // Need to start Bluetooth if it is available and user either selected it explicitly or
        // user did not select any output device.
        boolean needBluetoothAudioStart = bluetoothManager.getState() == BluetoothManager.State.HEADSET_AVAILABLE && (userSelectedAudioDevice == AudioDevice.NONE || userSelectedAudioDevice == AudioDevice.BLUETOOTH);

        // Need to stop Bluetooth audio if user selected different device and
        // Bluetooth SCO connection is established or in the process.
        boolean needBluetoothAudioStop = (bluetoothManager.getState() == BluetoothManager.State.SCO_CONNECTED || bluetoothManager.getState() == BluetoothManager.State.SCO_CONNECTING) && (userSelectedAudioDevice != AudioDevice.NONE && userSelectedAudioDevice != AudioDevice.BLUETOOTH);

        if (bluetoothManager.getState() != BluetoothManager.State.HEADSET_AVAILABLE && bluetoothManager.getState() != BluetoothManager.State.SCO_CONNECTING) {
            bluetoothManager.getState();
        }

        // Start or stop Bluetooth SCO connection given states set earlier.
        if (needBluetoothAudioStop) {
            bluetoothManager.stopScoAudio();
            bluetoothManager.updateDevice();
        }

        if (needBluetoothAudioStart && !needBluetoothAudioStop) {
            // Attempt to start Bluetooth SCO audio (takes a few second to start).
            if (!bluetoothManager.startScoAudio()) {
                // Remove BLUETOOTH from list of available devices since SCO failed.
                audioDevices.remove(AudioDevice.BLUETOOTH);
                audioDeviceSetUpdated = true;
            }
        }

        // Update selected audio device.
        AudioDevice newAudioDevice;

        if (bluetoothManager.getState() == BluetoothManager.State.SCO_CONNECTED) {
            // If a Bluetooth is connected, then it should be used as output audio
            // device. Note that it is not sufficient that a headset is available;
            // an active SCO channel must also be up and running.
            newAudioDevice = AudioDevice.BLUETOOTH;
        } else if (hasWiredHeadset) {
            // If a wired headset is connected, but Bluetooth is not, then wired headset is used as
            // audio device.
            newAudioDevice = AudioDevice.WIRED_HEADSET;
        } else {
            // No wired headset and no Bluetooth, hence the audio-device list can contain speaker
            // phone (on a tablet), or speaker phone and earpiece (on mobile phone).
            // |defaultAudioDevice| contains either AudioDevice.SPEAKER_PHONE or AudioDevice.EARPIECE
            // depending on the user's selection.
            newAudioDevice = defaultAudioDevice;
        }
        // Switch to new device but only if there has been any changes.
        if (newAudioDevice != selectedAudioDevice || audioDeviceSetUpdated) {
            // Do the required device switch.
            setAudioDeviceInternal(newAudioDevice);

            if (audioManagerEvents != null) {
                // Notify a listening client that audio device has been changed.
                audioManagerEvents.onAudioDeviceChanged(selectedAudioDevice, audioDevices);
            }
        }
    }

    /**
     * AudioDevice is the names of possible audio devices that we currently
     * support.
     */
    public enum AudioDevice {
        SPEAKER_PHONE, WIRED_HEADSET, EARPIECE, BLUETOOTH, NONE
    }

    /**
     * AudioManager state.
     */
    public enum AudioManagerState {
        UNINITIALIZED, PREINITIALIZED, RUNNING,
    }

    /**
     * Selected audio device change event.
     */
    public interface AudioManagerEvents {
        // Callback fired once audio device is changed or list of available audio devices changed.
        void onAudioDeviceChanged(AudioDevice selectedAudioDevice, Set<AudioDevice> availableAudioDevices);
    }

    /* Receiver which handles changes in wired headset availability. */
    private class WiredHeadsetReceiver extends BroadcastReceiver {
        private static final int STATE_UNPLUGGED = 0;
        private static final int STATE_PLUGGED = 1;
        private static final int HAS_NO_MIC = 0;
        private static final int HAS_MIC = 1;

        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra("state", STATE_UNPLUGGED);
            int microphone = intent.getIntExtra("microphone", HAS_NO_MIC);
            String name = intent.getStringExtra("name");
            hasWiredHeadset = (state == STATE_PLUGGED);
            updateAudioDeviceState();
        }
    }
}
