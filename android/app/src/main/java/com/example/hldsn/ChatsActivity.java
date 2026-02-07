package com.example.hldsn;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.api.Authentication;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ChatsActivity extends AppCompatActivity {
    TextView communitybtn;
    FirebaseFirestore db;
    FirebaseAuth auth;



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
        db=FirebaseFirestore.getInstance();
        auth=FirebaseAuth.getInstance();

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
