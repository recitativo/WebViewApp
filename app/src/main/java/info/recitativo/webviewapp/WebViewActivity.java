package info.recitativo.webviewapp;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class WebViewActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = WebViewActivity.class.getSimpleName();

    private WebView mWebView;
    private int mOriginalMode = Integer.MIN_VALUE;
    private HeadSetStateReceiver mBroadcastReceiver;
    private boolean isHandsFree; // JavaScript側から求められる状態
    private boolean mOriginalSpeakerphone = false;

    private SensorManager sensorManager;
    private Sensor proximity;
    private boolean isProximitySensorNearby = false;

    interface Callback {
        void onOK();
    }

    // JavaScriptから利用できるオブジェクトのクラス
    private class JSHandler {

        // スピーカーをハンズフリーモードに設定する（ビデオ通話の時、JSから呼ばれる）
        @JavascriptInterface
        public void setHandsFree(){
            isHandsFree = true;
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if(audioManager.isWiredHeadsetOn()){
                audioManager.setSpeakerphoneOn(false);
            }else{
                audioManager.setSpeakerphoneOn(true);
            }
        }

        // スピーカーを受話器での通話モードに設定する（音声通話の時、JSから呼ばれる）
        @JavascriptInterface
        public void setNormalPhone(){
            isHandsFree = false;
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audioManager.setSpeakerphoneOn(false);
        }

        // WebViewを破棄する
        @JavascriptInterface
        public void destroyWebView(){
            WebViewActivity.this.runOnUiThread(new Runnable() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void run() {
                    Log.d(TAG, "Called destroyWebView");
                    if(mWebView != null) {
                        mWebView.destroy();
                        mWebView = null;
                    } else {
                        Log.d(TAG, "mWebView is NULL!!");
                    }
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        // 常時点灯
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // センサーオブジェクトを取得
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");

        setContentView(R.layout.activity_web_view);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            //actionBar.setDisplayHomeAsUpEnabled(true);
            //actionBar.setTitle("WebViewApp");
            actionBar.hide();
        }

        //Register Receiver
        mBroadcastReceiver = new HeadSetStateReceiver();
        //registerReceiver(mBroadcastReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
        registerReceiver(mBroadcastReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

        // IntentからURL取り出し。
        String url = getIntent().getStringExtra("url");
        Log.d(TAG, "url:" + url);

        mWebView = (WebView)findViewById(R.id.webView);

        WebSettings settings = mWebView.getSettings();

        // JavaScriptを有効にする。
        settings.setJavaScriptEnabled(true);
        // LocalStorageを利用可能にする。
        settings.setDomStorageEnabled(true);

        // メディアの再生をユーザ操作なしで可能にする。
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Use WideViewport and Zoom out if there is no viewport defined
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Enable pinch to zoom without the zoom buttons
        settings.setBuiltInZoomControls(true);

        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
            // Hide the zoom controls for HONEYCOMB+
            settings.setDisplayZoomControls(false);
        }

        // Enable remote debugging via chrome://inspect
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        // WebViewClient設定。リンクした時にWebView内で遷移させるため。
        mWebView.setWebViewClient(new WebViewClient() {

            // オレオレ証明書起因のエラー回避
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

        });
        mWebView.setWebChromeClient(new WebChromeClient() {
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                WebViewActivity.this.runOnUiThread(new Runnable() {
                    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    public void run() {
                        request.grant(request.getResources());
                    }
                });
            }
        });

        // JS-Native Bridge
        mWebView.addJavascriptInterface(new JSHandler(), "Native");

        // Audio mode 保持
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mOriginalMode = audioManager.getMode();

        mOriginalSpeakerphone = audioManager.isSpeakerphoneOn();
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

        // 指定したURLを読み込み・表示させる。
        mWebView.loadUrl(url);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        sensorManager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");

        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");

        //WebSocketのClose処理などを実施
        if(mWebView != null) {
            mWebView.loadUrl("javascript:window.onStop();");
        }

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        // Audio mode 復元
        if(mOriginalMode!=Integer.MIN_VALUE){
            audioManager.setMode(mOriginalMode);
        }
        audioManager.setSpeakerphoneOn(mOriginalSpeakerphone);

        // unregister broadcast
        unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isProximitySensorNearby && keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        // Backキーをブラウザのバックボタンとして動作させる
        if ((mWebView != null) && (keyCode == KeyEvent.KEYCODE_BACK) && mWebView.canGoBack()) {
            mWebView.goBack();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            confirmLeaving(this, new Callback() {
               @Override
                public void onOK() {
                   finish();
               }
            });
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void confirmLeaving(final Context context, final Callback callback) {
        new AlertDialog.Builder(context)
                .setMessage("Are you OK to leave the web page?")
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (callback != null) {
                            callback.onOK();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        isProximitySensorNearby = isProximitySensorNearby(event);

        mWebView.setVisibility(isProximitySensorNearby ? View.GONE : View.VISIBLE);

        invalidateOptionsMenu();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public static Boolean isProximitySensorNearby(final SensorEvent event) {
        float threshold = 1.0f;

        final float distanceInCm = event.values[0];
        final float maxDistance = event.sensor.getMaximumRange();

        if (maxDistance <= threshold) {
            // Case binary 0/1 and short sensors
            threshold = maxDistance;
        }
        return distanceInCm < threshold;
    }

    public class HeadSetStateReceiver extends BroadcastReceiver {

        private String TAG = this.getClass().getSimpleName();

        public HeadSetStateReceiver() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null) {
                return;
            }

            switch (action) {
                case AudioManager.ACTION_AUDIO_BECOMING_NOISY:
                    AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    if(isHandsFree){
                        audioManager.setSpeakerphoneOn(true);
                    }else{
                        audioManager.setSpeakerphoneOn(false);
                    }
                    break;
                default:
                    break;
            }
        }
    }
}
