/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.Layout;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    private String TAG = "WatchFaceReceiver";
    public String maxWeather;
    public String minWeather;
    public Double weatherStatus;


    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine
            implements
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        private GoogleApiClient googleApiClient;
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mMaxWeatherPaint;
        Paint mMinWeatherPaint;
        Paint mWeatherIcon;
        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;

        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            googleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mMaxWeatherPaint = new Paint();
            mMaxWeatherPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mMinWeatherPaint = new Paint();
            mMinWeatherPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mWeatherIcon = new Paint();
            mWeatherIcon.setColor(resources.getColor(R.color.white));
            mWeatherIcon.setTextAlign(Paint.Align.CENTER);



            mTime = new Time();
        }

        private void releaseGoogleApiClient(){
            if(googleApiClient != null && googleApiClient.isConnected()) {
                googleApiClient.disconnect();
            }
        }

        @Override
        public void onConnected(Bundle bundle) {
            Log.d(TAG, "connected GoogleAPI");
            Wearable.DataApi.addListener(googleApiClient,onDataChangedListener);
            Wearable.DataApi.getDataItems(googleApiClient).setResultCallback(onConnectionResultCallback);
        }

        private final DataApi.DataListener onDataChangedListener = new DataApi.DataListener(){
            @Override
            public void onDataChanged(DataEventBuffer dataEvents){
                for (DataEvent event : dataEvents){
                    if(event.getType() == DataEvent.TYPE_CHANGED){
                        DataItem item = event.getDataItem();
                        processConfigurationFor(item);
                    }
                }
                dataEvents.release();
                if (isVisible() && !isInAmbientMode()) {
                    invalidate();
                }
            }
        };

        private void processConfigurationFor(DataItem item) {
            if("/watch_face_request".equals(item.getUri().getPath())) {
                DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                // If we have max and min temp data from mobile, get those values
                if (!dataMap.isEmpty()){
                    maxWeather = dataMap.getString("MAX_WEATHER");
                    minWeather = dataMap.getString("MIN_WEATHER");
                    weatherStatus = dataMap.getDouble("STATUS");
                } else {
                    Log.v(TAG, "No data from mobile");
                }
            }
        }

        private final ResultCallback<DataItemBuffer> onConnectionResultCallback = new ResultCallback<DataItemBuffer>(){
          @Override
            public void onResult(DataItemBuffer dataItems) {
              for(DataItem item : dataItems){
                  processConfigurationFor(item);
              }
              dataItems.release();
              if (isVisible() && !isInAmbientMode()) {
                  invalidate();
              }
          }
        };



        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            releaseGoogleApiClient();
            super.onDestroy();
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.e(TAG, "suspended GoogleAPI");
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.e(TAG, "connectionFailed GoogleAPI: " + connectionResult.getErrorMessage());
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(Typeface.SERIF);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                googleApiClient.connect();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                releaseGoogleApiClient();
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float timeSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            float weatherSize = resources.getDimension(isRound
                    ? R.dimen.digital_weather_size_round : R.dimen.digital_weather_size);

            mTextPaint.setTextSize(timeSize);
            mMaxWeatherPaint.setTextSize(weatherSize);
            mMinWeatherPaint.setTextSize(weatherSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                    mMaxWeatherPaint.setAntiAlias(!inAmbientMode);
                    mMinWeatherPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = MyWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        private Bitmap decodeRes(int resource){
            return BitmapFactory.decodeResource(getApplicationContext().getResources(), resource);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            Bitmap weatherIcon;

            // Setting up the default icon while loading

            weatherIcon = decodeRes(R.drawable.ic_full_sad);


            // Draw the background.

            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            if(weatherStatus != null) {
                if (weatherStatus >= 200 && weatherStatus <= 232) {
                    weatherIcon = decodeRes(R.drawable.ic_storm);
                } else if (weatherStatus >= 300 && weatherStatus <= 321) {
                    weatherIcon = decodeRes(R.drawable.ic_light_rain);
                } else if (weatherStatus >= 500 && weatherStatus <= 504) {
                    weatherIcon = decodeRes(R.drawable.ic_rain);
                } else if (weatherStatus == 511) {
                    weatherIcon = decodeRes(R.drawable.ic_snow);
                } else if (weatherStatus >= 520 && weatherStatus <= 531) {
                    weatherIcon = decodeRes(R.drawable.ic_rain);
                } else if (weatherStatus >= 600 && weatherStatus <= 622) {
                    weatherIcon = decodeRes(R.drawable.ic_snow);
                } else if (weatherStatus >= 701 && weatherStatus <= 761) {
                    weatherIcon = decodeRes(R.drawable.ic_fog);
                } else if (weatherStatus == 761 || weatherStatus == 781) {
                    weatherIcon = decodeRes(R.drawable.ic_storm);
                } else if (weatherStatus == 800) {
                    weatherIcon = decodeRes(R.drawable.ic_clear);
                } else if (weatherStatus == 801) {
                    weatherIcon = decodeRes(R.drawable.ic_light_clouds);
                } else if (weatherStatus >= 802 && weatherStatus <= 804) {
                    weatherIcon = decodeRes(R.drawable.ic_cloudy);
                } else {
                    weatherIcon = null;
                }
            } else {
                Log.v(TAG, "weatherStatus is null");
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String text = mAmbient
                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);
            canvas.drawText(text, canvas.getWidth() / 2, canvas.getHeight() / 2 + ((mTextPaint.descent() + mTextPaint.ascent()) / 2), mTextPaint);
            if(maxWeather != null) {
                NumberFormat formatter = new DecimalFormat("00");
                Double maxWeatherDouble = Double.parseDouble(maxWeather);
                String maxWeatherConverted = formatter.format(maxWeatherDouble);
                canvas.drawText(getResources().getString(R.string.max_weather) + " " + maxWeatherConverted, canvas.getWidth() / 2, canvas.getHeight() / 2 + 30, mMaxWeatherPaint);
            } else {
                canvas.drawText(getResources().getString(R.string.weather_loading), canvas.getWidth() / 2, canvas.getHeight() / 2 + 30, mMaxWeatherPaint);
            }
            if(minWeather != null){
                NumberFormat formatter = new DecimalFormat("00");
                Double minWeatherDouble = Double.parseDouble(minWeather);
                String minWeatherConverted = formatter.format(minWeatherDouble);
                canvas.drawText(getResources().getString(R.string.min_weather) + " " + minWeatherConverted, canvas.getWidth() / 2, canvas.getHeight() / 2 + 60, mMinWeatherPaint);
                canvas.drawBitmap(weatherIcon, canvas.getWidth() / 2 - 20, canvas.getHeight() / 2 + 80, mWeatherIcon);
            } else {
                Log.v(TAG, "Min Weather is loading");
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
