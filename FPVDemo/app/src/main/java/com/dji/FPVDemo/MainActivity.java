package com.dji.FPVDemo;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.TextureView.SurfaceTextureListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import dji.common.camera.CameraSystemState;
import dji.common.camera.DJICameraSettingsDef;
import dji.common.error.DJIError;
import dji.common.gimbal.DJIGimbalControllerMode;
import dji.common.gimbal.DJIGimbalRotateAngleMode;
import dji.common.gimbal.DJIGimbalRotateDirection;
import dji.common.gimbal.DJIGimbalSpeedRotation;
import dji.common.gimbal.DJIGimbalAngleRotation;
import dji.common.gimbal.DJIGimbalState;
import dji.common.gimbal.DJIGimbalWorkMode;
import dji.common.product.Model;
import dji.common.util.DJICommonCallbacks;
import dji.sdk.camera.DJICamera;
import dji.sdk.camera.DJICamera.CameraReceivedVideoDataCallback;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.base.DJIBaseProduct;
import dji.sdk.gimbal.DJIGimbal;

import static java.lang.Math.min;


public class MainActivity extends Activity implements SurfaceTextureListener,OnClickListener{

    private static final String TAG = MainActivity.class.getName();
    protected DJICamera.CameraReceivedVideoDataCallback mReceivedVideoDataCallBack = null;

    // Codec for video live view
    protected DJICodecManager mCodecManager = null;
    protected StringBuffer mStringBuffer;

    protected TextureView mVideoSurface = null;
    private Button mRecordVideoModeBtn;
    private Button mUpBtn,mRightBtn, mLeftBtn, mDownBtn, mResetBtn;
    private ToggleButton mRecordBtn;
    private TextView recordingTime;
    private SeekBar speedBar;

    private boolean recording = false;

    private DJIGimbalSpeedRotation mPitchSpeedRotation;
    private DJIGimbalSpeedRotation mYawSpeedRotation;

    private Timer mTimer;
    private GimbalRotateTimerTask mGimbalRotationTimerTask;

    private Timer mFixTimer;
    private GimbalFixRotation mGimbalFixRotation;
    private float fixYaw=0.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUI();

        // The callback for receiving the raw H264 video data for camera live view
        mReceivedVideoDataCallBack = new CameraReceivedVideoDataCallback() {

            @Override
            public void onResult(byte[] videoBuffer, int size) {
            try{
                if(mCodecManager != null){
                    // Send the raw H264 video data to codec manager for decoding
                    mCodecManager.sendDataToDecoder(videoBuffer, size);
                }else {
                    Log.e(TAG, "mCodecManager is null");
                }
            } catch (Exception e) {
                //showToast(e.getMessage());
                Log.e("TAG", e.getMessage());

            }
            }
        };

        DJICamera camera = FPVDemoApplication.getCameraInstance();

        if (camera != null) {

            camera.setDJICameraUpdatedSystemStateCallback(new DJICamera.CameraUpdatedSystemStateCallback() {
                @Override
                public void onResult(CameraSystemState cameraSystemState) {
                    if (null != cameraSystemState) {

                        int recordTime = cameraSystemState.getCurrentVideoRecordingTimeInSeconds();
                        int minutes = (recordTime % 3600) / 60;
                        int seconds = recordTime % 60;

                        final String timeString = String.format("%02d:%02d", minutes, seconds);
                        final boolean isVideoRecording = cameraSystemState.isRecording();

                        MainActivity.this.runOnUiThread(new Runnable() {

                            @Override
                            public void run() {

                                recordingTime.setText(timeString);

                                /*
                                 * Update recordingTime TextView visibility and mRecordBtn's check state
                                 */
                                if (isVideoRecording){
                                    recordingTime.setVisibility(View.VISIBLE);
                                }else
                                {
                                    recordingTime.setVisibility(View.INVISIBLE);
                                }
                            }
                        });
                    }
                }
            });

        }

    }

    protected void onProductChange() {
        initPreviewer();
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        initPreviewer();
        onProductChange();

        if(mVideoSurface == null) {
            Log.e(TAG, "mVideoSurface is null");
        }
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        uninitPreviewer();
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    public void onReturn(View view){
        Log.e(TAG, "onReturn");
        this.finish();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        uninitPreviewer();
        super.onDestroy();
    }

    private void initUI() {
        // init mVideoSurface
        mVideoSurface = (TextureView)findViewById(R.id.video_previewer_surface);

        recordingTime = (TextView) findViewById(R.id.timer);
        mRecordBtn = (ToggleButton) findViewById(R.id.btn_record);
        mRecordVideoModeBtn = (Button) findViewById(R.id.btn_record_video_mode);
        mRightBtn = (Button) findViewById(R.id.btnRight);
        mLeftBtn = (Button) findViewById(R.id.btnLeft);
        mUpBtn = (Button) findViewById(R.id.btnUp);
        mDownBtn = (Button) findViewById(R.id.btnDown);
        mResetBtn = (Button) findViewById(R.id.btnReset);

        speedBar=(SeekBar)findViewById(R.id.seekBar);
        if (null != mVideoSurface) {
            mVideoSurface.setSurfaceTextureListener(this);
        }


        mRecordBtn.setOnClickListener(this);

        mRecordVideoModeBtn.setOnClickListener(this);
        mResetBtn.setOnClickListener(this);
        mRightBtn.setOnClickListener(this);
        mLeftBtn.setOnClickListener(this);
        mUpBtn.setOnClickListener(this);
        mDownBtn.setOnClickListener(this);

        recordingTime.setVisibility(View.INVISIBLE);

        mRecordBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startRecord();
                } else {
                    stopRecord();
                }
            }
        });
    }

    private void initPreviewer() {

        DJIBaseProduct product = FPVDemoApplication.getProductInstance();

        if (product == null || !product.isConnected()) {
            showToast(getString(R.string.disconnected));
        } else {
            if (null != mVideoSurface) {
                mVideoSurface.setSurfaceTextureListener(this);
            }
            showToast(getString(R.string.disconnected));
            if (!product.getModel().equals(Model.UnknownAircraft)) {
                DJICamera camera = product.getCamera();
                if (camera != null){
                    // Set the callback
                    camera.setDJICameraReceivedVideoDataCallback(mReceivedVideoDataCallBack);
                }
            }
            mStringBuffer = new StringBuffer();
            FPVDemoApplication.getProductInstance().getGimbal().setGimbalStateUpdateCallback(
                    new DJIGimbal.GimbalStateUpdateCallback() {
                        @Override
                        public void onGimbalStateUpdate(DJIGimbal djiGimbal,
                                                        DJIGimbalState djiGimbalState) {
                            mStringBuffer.delete(0, mStringBuffer.length());

                            mStringBuffer.append(" ").append(djiGimbalState.getAttitudeInDegrees().pitch);
                            //mStringBuffer.append("RollInDegrees: ").
                            //      append(djiGimbalState.getAttitudeInDegrees().roll).append("\n");
                            //mStringBuffer.append("YawInDegrees: ").
                            //      append(djiGimbalState.getAttitudeInDegrees().yaw).append("\n");

                        }
                    }
            );
        }
    }

    private void uninitPreviewer() {
        DJICamera camera = FPVDemoApplication.getCameraInstance();
        if (camera != null){
            // Reset the callback
            FPVDemoApplication.getCameraInstance().setDJICameraReceivedVideoDataCallback(null);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureAvailable");
        if (mCodecManager == null) {
            mCodecManager = new DJICodecManager(this, surface, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureSizeChanged");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(TAG,"onSurfaceTextureDestroyed");
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager = null;
        }

        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_record_video_mode:{
                switchCameraMode(DJICameraSettingsDef.CameraMode.RecordVideo);
                break;
            }
            case R.id.btnReset: {
                resetCamera();
                break;
            }
            case R.id.btnUp: {
                moveUp();
                break;
            }
            case R.id.btnDown: {
                moveDown();
                break;
            }
            case R.id.btnLeft: {
                moveLeft();
                break;
            }
            case R.id.btnRight: {
                moveRight();
                break;
            }
            default:
                break;
        }
    }
    private void resetCamera() {

        DJIGimbal gimbal = FPVDemoApplication.getProductInstance().getGimbal();
        if (gimbal!=null) {

            gimbal.resetGimbal(
                    new DJICommonCallbacks.DJICompletionCallback() {
                        @Override
                        public void onResult(DJIError error) {}});
        }

    }
    private void moveUp() {

        if (!recording) {
            if (mTimer != null) {
                mGimbalRotationTimerTask.cancel();
                mTimer.cancel();
                mTimer.purge();
                mGimbalRotationTimerTask = null;
                mTimer = null;
                DJIGimbal gimbal = FPVDemoApplication.getProductInstance().getGimbal();
                if (gimbal!=null) {
                    DJIGimbalSpeedRotation mStopRotation = new DJIGimbalSpeedRotation(0, DJIGimbalRotateDirection.Clockwise);
                    gimbal.rotateGimbalBySpeed(mStopRotation,mStopRotation,mStopRotation,
                            new DJICommonCallbacks.DJICompletionCallback() {
                                @Override
                                public void onResult(DJIError error) {}});
                }
            }
            else
            {
                mTimer = new Timer();
                mPitchSpeedRotation = new DJIGimbalSpeedRotation(5,
                        DJIGimbalRotateDirection.Clockwise);
                mGimbalRotationTimerTask = new GimbalRotateTimerTask(mPitchSpeedRotation,null,null);
                mTimer.schedule(mGimbalRotationTimerTask, 0, 100);
            }
            DJIGimbal gimbal = FPVDemoApplication.getProductInstance().getGimbal();
            if (gimbal!=null) {
                gimbal.setGimbalWorkMode(DJIGimbalWorkMode.FreeMode, new DJICommonCallbacks.DJICompletionCallback() {
                    @Override
                    public void onResult(DJIError error) {
                    }
                });
            }
        }
        else{showToast("can't move while recording");}

    }

    private void moveDown() {
        if (!recording) {
            if (mTimer != null) {
                mGimbalRotationTimerTask.cancel();
                mTimer.cancel();
                mTimer.purge();
                mGimbalRotationTimerTask = null;
                mTimer = null;
                DJIGimbal gimbal = FPVDemoApplication.getProductInstance().getGimbal();
                if (gimbal!=null) {
                    DJIGimbalSpeedRotation mStopRotation = new DJIGimbalSpeedRotation(0, DJIGimbalRotateDirection.Clockwise);
                    gimbal.rotateGimbalBySpeed(mStopRotation,mStopRotation,mStopRotation,
                            new DJICommonCallbacks.DJICompletionCallback() {
                                @Override
                                public void onResult(DJIError error) {}});
                }
            }
            else
            {
                mTimer = new Timer();
                mPitchSpeedRotation = new DJIGimbalSpeedRotation(5,
                        DJIGimbalRotateDirection.CounterClockwise);
                mGimbalRotationTimerTask = new GimbalRotateTimerTask(mPitchSpeedRotation,null,null);
                mTimer.schedule(mGimbalRotationTimerTask, 0, 100);
            }
            DJIGimbal gimbal = FPVDemoApplication.getProductInstance().getGimbal();
            if (gimbal!=null) {
                gimbal.setGimbalWorkMode(DJIGimbalWorkMode.FreeMode, new DJICommonCallbacks.DJICompletionCallback() {
                    @Override
                    public void onResult(DJIError error) { }
                });
            }
        }
        else{showToast("can't move while recording");}

    }

    private void moveLeft() {
        if (!recording) {
            if (mTimer != null) {
                mGimbalRotationTimerTask.cancel();
                mTimer.cancel();
                mTimer.purge();
                mGimbalRotationTimerTask = null;
                mTimer = null;
                DJIGimbal gimbal = FPVDemoApplication.getProductInstance().getGimbal();
                if (gimbal!=null) {
                    DJIGimbalSpeedRotation mStopRotation = new DJIGimbalSpeedRotation(0, DJIGimbalRotateDirection.Clockwise);
                    gimbal.rotateGimbalBySpeed(mStopRotation,mStopRotation,mStopRotation,
                            new DJICommonCallbacks.DJICompletionCallback() {
                                @Override
                                public void onResult(DJIError error) {}});
                }
            }
            else
            {
                mTimer = new Timer();
                mYawSpeedRotation = new DJIGimbalSpeedRotation(5,DJIGimbalRotateDirection.CounterClockwise);
                mGimbalRotationTimerTask = new GimbalRotateTimerTask(null,null,mYawSpeedRotation);
                mTimer.schedule(mGimbalRotationTimerTask, 0, 100);
            }
            DJIGimbal gimbal = FPVDemoApplication.getProductInstance().getGimbal();
            if (gimbal!=null) {
                gimbal.setGimbalWorkMode(DJIGimbalWorkMode.FreeMode, new DJICommonCallbacks.DJICompletionCallback() {
                    @Override
                    public void onResult(DJIError error) {
                    }
                });
            }
        }
        else{showToast("can't move while recording");}


    }

    private void moveRight() {

        if (!recording) {
            if (mTimer != null) {
                mGimbalRotationTimerTask.cancel();
                mTimer.cancel();
                mTimer.purge();
                mGimbalRotationTimerTask = null;
                mTimer = null;

                DJIGimbal gimbal = FPVDemoApplication.getProductInstance().getGimbal();
                if (gimbal!=null) {
                    DJIGimbalSpeedRotation mStopRotation = new DJIGimbalSpeedRotation(0, DJIGimbalRotateDirection.Clockwise);
                    gimbal.rotateGimbalBySpeed(mStopRotation,mStopRotation,mStopRotation,
                            new DJICommonCallbacks.DJICompletionCallback() {
                                @Override
                                public void onResult(DJIError error) {}});
                }

            }
            else
            {
                mTimer = new Timer();
                mYawSpeedRotation = new DJIGimbalSpeedRotation(5,
                        DJIGimbalRotateDirection.Clockwise);
                mGimbalRotationTimerTask = new GimbalRotateTimerTask(null,null,mYawSpeedRotation);
                //mGimbalRotationTimerTask.run();
                mTimer.schedule(mGimbalRotationTimerTask, 0, 100);
            }

            DJIGimbal gimbal = FPVDemoApplication.getProductInstance().getGimbal();
            if (gimbal!=null) {
                gimbal.setGimbalWorkMode(DJIGimbalWorkMode.FreeMode, new DJICommonCallbacks.DJICompletionCallback() {
                    @Override
                    public void onResult(DJIError error) {
                    }
                });
            }
        }
        else{showToast("can't move while recording");}

    }

    private void switchCameraMode(DJICameraSettingsDef.CameraMode cameraMode){

        DJICamera camera = FPVDemoApplication.getCameraInstance();
        if (camera != null) {
            camera.setCameraMode(cameraMode, new DJICommonCallbacks.DJICompletionCallback() {
                @Override
                public void onResult(DJIError error) {

                    if (error == null) {
                        showToast("Switch Camera Mode Succeeded");
                    } else {
                        showToast(error.getDescription());
                    }
                }
            });
            }

    }



    // Method for starting recording
    private void startRecord(){

        DJICameraSettingsDef.CameraMode cameraMode = DJICameraSettingsDef.CameraMode.RecordVideo;
        final DJICamera camera = FPVDemoApplication.getCameraInstance();
        if (camera != null) {
            camera.startRecordVideo(new DJICommonCallbacks.DJICompletionCallback(){
                @Override
                public void onResult(DJIError error)
                {
                    if (error == null) {
                        showToast("Recording. Battery at " + String.valueOf(FPVDemoApplication.getBatteryPercent()) + "%");

                        fixYaw = FPVDemoApplication.getProductInstance().getGimbal().getAttitudeInDegrees().yaw;
                        mFixTimer = new Timer();
                        mGimbalFixRotation = new GimbalFixRotation(fixYaw);
                        //mGimbalRotationTimerTask.run();
                        mFixTimer.schedule(mGimbalFixRotation, 0, 100);
                        recording=true;
                        writeToLog();
                    }else {
                        showToast(error.getDescription());
                    }
                }
            }); // Execute the startRecordVideo API
        }
    }

    // Method for stopping recording
    private void stopRecord(){

        DJICamera camera = FPVDemoApplication.getCameraInstance();
        if (camera != null) {
            camera.stopRecordVideo(new DJICommonCallbacks.DJICompletionCallback(){

                @Override
                public void onResult(DJIError error)
                {
                    if(error == null) {
                        recording = false;
                        mGimbalFixRotation.cancel();
                        mFixTimer.cancel();
                        mFixTimer.purge();
                        mGimbalFixRotation = null;
                        mFixTimer = null;
                        showToast("Stopped. Battery at " + String.valueOf(FPVDemoApplication.getBatteryPercent()) + "%");
                    }else {
                        showToast(error.getDescription());
                    }
                }
            }); // Execute the stopRecordVideo API
        }

    }
    private void writeToLog(){

        try {

            DateFormat df = new SimpleDateFormat("yyyyMMdd");

            // Get the date today using Calendar object.
            Date today = Calendar.getInstance().getTime();

            String todayDate = df.format(today);

            String filename = Environment.getExternalStorageDirectory().getAbsolutePath() + "/osmoLog/" + todayDate + ".txt";
            String dirname = Environment.getExternalStorageDirectory().getAbsolutePath() + "/osmoLog";
            //String filename = "/sdcard/osmoLog/" + todayDate + ".txt";
            //showToast(filename);

            File dir = new File(dirname);
            if (!dir.exists() ) {
                try {
                    dir.mkdir();
                }
                catch(Exception ex){
                    showToast(ex.getMessage());
                }

            }


            File file = new File(filename);
            file.setReadable(true, false);
            file.setExecutable(true, false);
            file.setWritable(true, false);
            //File file = new File("/mnt/shared/osmoLogs/" + todayDate + ".txt");
            if (!file.exists()) {
                try {

                    file.createNewFile();
                }
                catch(IOException ex){
                    showToast(ex.getMessage());
                }


            }
            SimpleDateFormat dfTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String nowTime = dfTime.format(today);

            //DJIGimbalAttitude djiAttitude;
            //djiAttitude = FPVDemoApplication.getProductInstance().getGimbal().getAttitudeInDegrees();

            String outputText = nowTime + mStringBuffer.toString() + "\n" ;

            FileWriter fw = new FileWriter(file,true);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(outputText);
            bw.close();

//            try{
             //   Process su = Runtime.getRuntime().exec("rm /mnt/shared/osmoLogs/" + todayDate + ".txt");
              //  su.waitFor();
              //  su = Runtime.getRuntime().exec("cp " + filename + " /mnt/shared/osmoLogs/" + todayDate + ".txt");
             //   su.waitFor();

  //          }catch(IOException e){
    //            throw new Exception(e);
      //      }
        //    catch(InterruptedException e){
          //      throw new Exception(e);
            //}



        } catch (Exception e) {
            showToast(e.getMessage());

        }



    }



    class GimbalFixRotation extends TimerTask {
        float mYaw;

        GimbalFixRotation(float yaw) {
            super();
            this.mYaw = yaw;
        }
        @Override
        public void run() {
            DJIGimbal gimbal = FPVDemoApplication.getProductInstance().getGimbal();
          //  gimbal.setGimbalControllerMode(DJIGimbalControllerMode.Free,null);
            if (gimbal!=null) {
                DJIGimbalRotateAngleMode djiGimbalAngleMode = DJIGimbalRotateAngleMode.RelativeAngle;
                DJIGimbalAngleRotation djiGimbalAngleRotation_pitch = new DJIGimbalAngleRotation(false, 0.0f, DJIGimbalRotateDirection.Clockwise);
                DJIGimbalAngleRotation djiGimbalAngleRotation_roll = new DJIGimbalAngleRotation(false, 0.0f, DJIGimbalRotateDirection.Clockwise);

                float newYaw = FPVDemoApplication.getProductInstance().getGimbal().getAttitudeInDegrees().yaw;
                DJIGimbalAngleRotation djiGimbalAngleRotation_yaw = new DJIGimbalAngleRotation(true, this.mYaw-newYaw, DJIGimbalRotateDirection.Clockwise);
                //showToast("yaw start: " + String.valueOf(this.mYaw) +" yaw now: " + String.valueOf(newYaw) );
                int seekBarValue= speedBar.getProgress(); // get progress value from the Seek bar
                float speed = ((float)seekBarValue-50.0f)/200.0f;
                //float yawError = this.mYaw-newYaw;
                if (speed>0.00){
                    //float speed = min(yawError,5);
                mYawSpeedRotation = new DJIGimbalSpeedRotation(speed,
                        DJIGimbalRotateDirection.Clockwise);
                gimbal.rotateGimbalBySpeed(null, null, mYawSpeedRotation,
                        new DJICommonCallbacks.DJICompletionCallback() {

                            @Override
                            public void onResult(DJIError error) {

                            }
                        });}
                if (speed<-0.00){

                    mYawSpeedRotation = new DJIGimbalSpeedRotation(-1.0f*speed,
                            DJIGimbalRotateDirection.CounterClockwise);
                    gimbal.rotateGimbalBySpeed(null, null, mYawSpeedRotation,
                            new DJICommonCallbacks.DJICompletionCallback() {

                                @Override
                                public void onResult(DJIError error) {

                                }
                            });}
                //gimbal.rotateGimbalByAngle(djiGimbalAngleMode, djiGimbalAngleRotation_pitch, djiGimbalAngleRotation_roll, djiGimbalAngleRotation_yaw, new DJICommonCallbacks.DJICompletionCallback() {
                 //   @Override
                 //   public void onResult(DJIError error) {
                  //      if (error == null) {
                            // process success
                   ///     } else {
                      //      showToast(error.getDescription());
                        //}
                    //}
                //});
            }
        }

    }






    class GimbalRotateTimerTask extends TimerTask {
        DJIGimbalSpeedRotation mPitch;
        DJIGimbalSpeedRotation mRoll;
        DJIGimbalSpeedRotation mYaw;

        GimbalRotateTimerTask(DJIGimbalSpeedRotation pitch, DJIGimbalSpeedRotation roll, DJIGimbalSpeedRotation yaw) {
            super();
            this.mPitch = pitch;
            this.mRoll = roll;
            this.mYaw = yaw;
        }
        @Override
        public void run() {
            DJIGimbal gimbal = FPVDemoApplication.getProductInstance().getGimbal();
            if (gimbal!=null) {

                gimbal.rotateGimbalBySpeed(mPitch, mRoll, mYaw,
                        new DJICommonCallbacks.DJICompletionCallback() {

                            @Override
                            public void onResult(DJIError error) {

                            }
                        });
            }
        }

    }

}
