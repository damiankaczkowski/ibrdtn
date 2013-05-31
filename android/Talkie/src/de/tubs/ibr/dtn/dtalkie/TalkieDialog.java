
package de.tubs.ibr.dtn.dtalkie;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import de.tubs.ibr.dtn.api.EID;
import de.tubs.ibr.dtn.api.GroupEndpoint;
import de.tubs.ibr.dtn.api.SingletonEndpoint;
import de.tubs.ibr.dtn.dtalkie.service.RecorderService;

public class TalkieDialog extends Activity {
    
    @SuppressWarnings("unused")
    private static final String TAG = "TalkieDialog";

    private int mMaxAmplitude = 0;
    private int mIdleCounter = 0;
    
    private Handler mHandler = null;
    
    private ImageButton mRecButton = null;
    private Button mCancelButton = null;
    
    private Boolean mRecordingCompleted = false;
    
    private RecorderService mService = null;
    private Boolean mBound = false;
    
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ((RecorderService.LocalBinder)service).getService();
            startRecording();
        }

        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recording_dialog);
        
        mRecButton = (ImageButton)findViewById(R.id.button_record);
        mRecButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // stop recording
                mRecordingCompleted = true;
                stopRecording();
            }
        });
        
        mCancelButton = (Button)findViewById(R.id.cancel);
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // abort recording
                stopRecording();
            }
        });
        
        mBound = false;
        mHandler = new Handler();
    }

    @Override
    protected void onDestroy() {
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        
        mHandler = null;
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        // we are going out of scope - stop recording
        stopRecording();
        
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        if (!mBound) {
            bindService(new Intent(this, RecorderService.class), mConnection, Context.BIND_AUTO_CREATE);
            mBound = true;
        }
    }
    
    private void startRecording() {
        // start amplitude update
        mHandler.postDelayed(mUpdateAmplitude, 100);
        
        EID destination = RecorderService.TALKIE_GROUP_EID;
        Boolean singleton = true;
        
        Bundle extras = getIntent().getExtras();
        
        if (extras != null) {
            if (extras.containsKey("destination")) {
                String dstr = extras.getString("destination");
                if (extras.containsKey("singleton")) {
                    singleton = extras.getBoolean("singleton");
                }
                if (singleton) {
                    destination = new SingletonEndpoint(dstr);
                } else {
                    destination = new GroupEndpoint(dstr);
                }
            }
        }
        
        if (mService != null) {
            mService.startRecording(destination);
        }
    }
    
    private void stopRecording() {
        // stop amplitude update
        mHandler.removeCallbacks(mUpdateAmplitude);
        
        // set indicator level to zero
        setIndicator(0.0f);
        
        if (mService != null) {
            if (mRecordingCompleted)
                mService.stopRecording();
            else
                mService.abortRecording();
        }
        
        mMaxAmplitude = 0;
        mIdleCounter = 0;
        
        TalkieDialog.this.finish();
    }
    
    @SuppressWarnings("deprecation")
    private void setIndicator(Float level) {
        LayerDrawable d = (LayerDrawable)getResources().getDrawable(R.drawable.rec_background);
        
        // 0 (large) .. 64 (small)
        int size = Float.valueOf(64 * (1 - level) * 0.9f).intValue();
        
        d.setLayerInset(0, size, size, size, size);
        mRecButton.setBackgroundDrawable(d);
    }
    
    private Runnable mUpdateAmplitude = new Runnable() {
        @Override
        public void run() {
            if (mService != null) {
                int curamp = mService.getMaxAmplitude();
                if (mMaxAmplitude < curamp) mMaxAmplitude = curamp;
                
                float level = 0.0f;
                        
                if (mMaxAmplitude > 0) {
                    level = Float.valueOf(curamp) / mMaxAmplitude;
                }
                
                if (level < 0.4f) {
                    mIdleCounter++;
                } else {
                    mIdleCounter = 0;
                }
                
                setIndicator(level);
                
                // if there is no sound for 2 seconds
                if (mIdleCounter >= 20) {
                    mRecordingCompleted = true;
                    stopRecording();
                }
            }
            mHandler.postDelayed(mUpdateAmplitude, 100);
        }
    };
}
