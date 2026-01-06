package com.example.hldsn;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ChatsActivity extends AppCompatActivity {
    TextView communitybtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chats);

        ImageView backIcon = findViewById(R.id.chatBackIcon);
        if (backIcon != null) {
            backIcon.setOnClickListener(v -> finish());
        }

        View communityTab = findViewById(R.id.communityTab);
        if (communityTab != null) {
            communityTab.setOnClickListener(v ->
                    startActivity(new Intent(ChatsActivity.this, ReportIncidentActivity.class)));
        }
        init();
        listners();
    }

    public void init(){
        communitybtn=findViewById(R.id.communitybtn);

    }
    public void listners(){
        communitybtn.setOnClickListener(v->{
            Intent intent=new Intent(this, DisplayReportActivity.class);
            startActivity(intent);
        });
    }




}
