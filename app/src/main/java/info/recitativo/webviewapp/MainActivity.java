package info.recitativo.webviewapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {

    private EditText mEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // URL履歴をセット
        mEditText = (EditText)findViewById(R.id.editText);
        SharedPreferences urlSp = getSharedPreferences("url", MODE_PRIVATE);
        //mEditText.setText(urlSp.getString("url", "[URL for WebView content]"));
        mEditText.setText(urlSp.getString("url", "file:///android_asset/index.html"));

        // Permissions
        Boolean grantedCamera = ContextCompat.checkSelfPermission(
                this,android.Manifest.permission.CAMERA)== PackageManager.PERMISSION_GRANTED;
        Boolean grantedAudio = ContextCompat.checkSelfPermission(
                this,android.Manifest.permission.RECORD_AUDIO)== PackageManager.PERMISSION_GRANTED;
        if(!grantedAudio && !grantedCamera) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO,android.Manifest.permission.CAMERA}, 0);
        } else if(grantedAudio && !grantedCamera){
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, 0);
        } else if(!grantedAudio && grantedCamera){
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 0);
        }

    }

    public void onClickOpenWebViewButton(View v) {
        String url = String.valueOf(mEditText.getText());

        // URL履歴を保存
        SharedPreferences urlSp = getSharedPreferences("url", MODE_PRIVATE);
        urlSp.edit().putString("url", url).apply();

        // WebView2Activityへ遷移。IntentでURLを渡す。
        Intent intent = new Intent(MainActivity.this, WebViewActivity.class);
        intent.putExtra("url", url);
        startActivity(intent);
    }
}
