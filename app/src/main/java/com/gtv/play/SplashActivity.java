package com.gtv.play;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView splashImage = findViewById(R.id.splash_image);
        String splashUrl = "https://dl.dropboxusercontent.com/scl/fi/e2e6ee6hvr2aw6pxzc00f/AIRetouch_20261007_192754382.jpg?rlkey=5zpffdw3r8kfxu4hjdm5q27v0&st=6qat7i69&dl=1";
        Glide.with(this)
            .load(splashUrl)
            .into(splashImage);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        }, 3000);
    }
}
