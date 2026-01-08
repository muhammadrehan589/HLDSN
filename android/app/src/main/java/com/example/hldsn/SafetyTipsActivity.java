package com.example.hldsn;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class SafetyTipsActivity extends AppCompatActivity {

    ImageView floodTips, earthquakeTips, heatwaveTips, landslideTips;

@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.safety_tips);
        initViews();
        initListner();
    }
    void initViews(){
    floodTips=findViewById(R.id.floodImage);
    earthquakeTips=findViewById(R.id.earthquakeImage);
    heatwaveTips=findViewById(R.id.heatwaveImage);
    landslideTips=findViewById(R.id.landslideImage);
    }
    void initListner(){
    floodTips.setOnClickListener(v->{
        Intent intent=new Intent(this, SafetyTypeActivity.class);
        intent.putExtra("incidenttype","flood");
        startActivity(intent);
    });
    earthquakeTips.setOnClickListener(v->{
        Intent intent=new Intent(this,SafetyTypeActivity.class);
        intent.putExtra("incidenttype","earthquake");
        startActivity(intent);
    });
    heatwaveTips.setOnClickListener(v->{
        Intent intent=new Intent(this,SafetyTypeActivity.class);
        intent.putExtra("incidenttype","heatwave");
        startActivity(intent);
    });
    landslideTips.setOnClickListener(v->{
        Intent intent=new Intent(this,SafetyTypeActivity.class);
        intent.putExtra("incidenttype","landslide");
        startActivity(intent);
    });

    }

}
