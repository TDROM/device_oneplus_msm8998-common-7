/*
 * Copyright (c) 2015 The CyanogenMod Project
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

package com.cyanogenmod.settings.doze;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ProximitySensor implements SensorEventListener {

    private static final boolean DEBUG = false;
    private static final String TAG = "ProximitySensor";

    private static final int POCKET_DELTA_NS = 1000 * 1000 * 1000;
    private static final String FPC_FILE = "/sys/devices/soc/soc:fpc_fpc1020/proximity_state";

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private Context mContext;

    private boolean mSawNear = false;
    private long mInPocketTime = 0;

    public ProximitySensor(Context context) {
        mContext = context;
        mSensorManager = mContext.getSystemService(SensorManager.class);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY,false);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        boolean isNear = event.values[0] < mSensor.getMaximumRange();
        if (mSawNear && !isNear) {
            if (shouldPulse(event.timestamp)) {
                Utils.launchDozePulse(mContext);
            }
        } else {
            mInPocketTime = event.timestamp;
        }
        mSawNear = isNear;
    }

    private boolean shouldPulse(long timestamp) {
        long delta = timestamp - mInPocketTime;

        if (Utils.handwaveGestureEnabled(mContext)
                    && Utils.pocketGestureEnabled(mContext)) {
            return true;
        } else if (Utils.handwaveGestureEnabled(mContext)
                    && !Utils.pocketGestureEnabled(mContext)) {
            return delta < POCKET_DELTA_NS;
        } else if (!Utils.handwaveGestureEnabled(mContext)
                    && Utils.pocketGestureEnabled(mContext)) {
            return delta >= POCKET_DELTA_NS;
        }
        return false;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        /* Empty */
    }

    protected void enable() {
        if (DEBUG) Log.d(TAG, "Enabling");
        mSensorManager.registerListener(this, mSensor,
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    protected void disable() {
        if (DEBUG) Log.d(TAG, "Disabling");
        mSensorManager.unregisterListener(this, mSensor);
    }

    private boolean isFileWritable(String fname) {
        return new File(fname).canWrite();
    }

    private boolean writeLine(String fname, String value) {
        try {
            FileWriter fw = new FileWriter(fname);
            try {
                fw.write(value);
            } finally {
                fw.close();
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }
}
