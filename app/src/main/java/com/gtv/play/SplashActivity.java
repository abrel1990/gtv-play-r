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
        String splashUrl = "https://dl.dropboxusercontent.com/scl/fi/2ggwtfsj0izusrjch21hk/photo_2025-04-23_20-53-38.jpg?rlkey=ww7hdei2rlea2m1q5mr6gyu9b&st=2jbss54u&dl=1";

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
