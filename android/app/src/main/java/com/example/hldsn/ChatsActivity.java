package com.example.hldsn;

import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class ChatsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chats);

        ImageView backIcon = findViewById(R.id.chatBackIcon);
        if (backIcon != null) {
            backIcon.setOnClickListener(v -> finish());
        }
    }
}
