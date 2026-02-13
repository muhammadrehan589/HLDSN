package com.example.hldsn.services.safety_tips;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.R;

public class SafetyTypeActivity extends AppCompatActivity {
    String incidenttype;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        incidenttype=getIntent().getStringExtra("incidenttype");
        switch(incidenttype){
            case "flood":
                setContentView(R.layout.flood_tips);
                break;
            case "earthquake":
                setContentView(R.layout.earthquake_tips);
                break;
            case "heatwave":
                setContentView(R.layout.heatwave_tips);
                break;
            case "landslide":
                setContentView(R.layout.landslides_tips);
                break;
            default:
                setContentView(R.layout.safety_tips);
                break;
        }

    }

}
