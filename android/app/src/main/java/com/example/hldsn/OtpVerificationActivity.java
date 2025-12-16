package com.example.hldsn;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

public class OtpVerificationActivity extends AppCompatActivity {

    public static final String TAG = "OTP_VERIFICATION";

    EditText otpDigit1, otpDigit2, otpDigit3, otpDigit4, otpDigit5, otpDigit6;
    Button submitButton;
    TextView resendText;

    private FirebaseAuth auth;
    private String phoneNumber;
    private String verificationId;
    private PhoneAuthProvider.ForceResendingToken resendToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verification);

        otpDigit1 = findViewById(R.id.otpDigit1);
        otpDigit2 = findViewById(R.id.otpDigit2);
        otpDigit3 = findViewById(R.id.otpDigit3);
        otpDigit4 = findViewById(R.id.otpDigit4);
        otpDigit5 = findViewById(R.id.otpDigit5);
        otpDigit6 = findViewById(R.id.otpDigit6);
        submitButton = findViewById(R.id.submitButton);
        resendText = findViewById(R.id.resendText);

        auth = FirebaseAuth.getInstance();

        // Get phone number from intent (passed from login)
        phoneNumber = getIntent().getStringExtra("PHONE_NUMBER");
        Log.d(TAG, "Received phone number: " + phoneNumber);

        if (phoneNumber == null || phoneNumber.isEmpty()) {
            Log.e(TAG, "Phone number is empty! Cannot send OTP.");
            Toast.makeText(this, "Phone number is empty!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Send OTP when activity opens
        sendOtp(phoneNumber);

        // Submit OTP
        submitButton.setOnClickListener(v -> verifyOtp());

        // Resend OTP
        resendText.setOnClickListener(v -> {
            if (resendToken != null) {
                Log.d(TAG, "Resending OTP using resend token.");
                resendOtp(phoneNumber, resendToken);
            } else {
                Log.d(TAG, "Resending OTP without token.");
                sendOtp(phoneNumber);
            }
        });
    }

    private void sendOtp(String phone) {
        Log.d(TAG, "Sending OTP to: " + phone);
        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(auth)
                        .setPhoneNumber(phone)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(this)
                        .setCallbacks(callbacks)
                        .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
        startResendTimer(); // Disable resend for 60s
    }

    private void resendOtp(String phone, PhoneAuthProvider.ForceResendingToken token) {
        Log.d(TAG, "Resending OTP to: " + phone + " with token");
        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(auth)
                        .setPhoneNumber(phone)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(this)
                        .setCallbacks(callbacks)
                        .setForceResendingToken(token)
                        .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
        startResendTimer();
    }

    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks =
            new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                @Override
                public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                    Log.d(TAG, "onVerificationCompleted: auto-verification success");
                    auth.signInWithCredential(credential).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Phone Verified via auto verification");
                            Toast.makeText(OtpVerificationActivity.this, "Phone Verified!", Toast.LENGTH_SHORT).show();
                            openUserPortal();
                        } else {
                            Log.e(TAG, "Auto verification failed: " + task.getException());
                        }
                    });
                }

                @Override
                public void onVerificationFailed(@NonNull FirebaseException e) {
                    Log.e(TAG, "onVerificationFailed: " + e.getMessage(), e);
                    Toast.makeText(OtpVerificationActivity.this, "Verification Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }

                @Override
                public void onCodeSent(@NonNull String vid, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                    super.onCodeSent(vid, token);
                    verificationId = vid;
                    resendToken = token;
                    Log.d(TAG, "OTP sent successfully. VerificationId: " + vid);
                    Toast.makeText(OtpVerificationActivity.this, "OTP sent to your phone", Toast.LENGTH_SHORT).show();
                }
            };

    private void verifyOtp() {
        String enteredOtp = otpDigit1.getText().toString() +
                otpDigit2.getText().toString() +
                otpDigit3.getText().toString() +
                otpDigit4.getText().toString() +
                otpDigit5.getText().toString() +
                otpDigit6.getText().toString();

        Log.d(TAG, "Entered OTP: " + enteredOtp);

        if (enteredOtp.isEmpty() || enteredOtp.length() < 6) {
            Log.e(TAG, "OTP entered is invalid or empty");
            Toast.makeText(this, "Enter 6-digit OTP", Toast.LENGTH_SHORT).show();
            return;
        }

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, enteredOtp);
        auth.signInWithCredential(credential).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "OTP verified successfully");
                Toast.makeText(this, "Phone Verified!", Toast.LENGTH_SHORT).show();
                openUserPortal();
            } else {
                Log.e(TAG, "OTP verification failed: " + task.getException());
                Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startResendTimer() {
        resendText.setEnabled(false);
        new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                resendText.setText("Resend OTP in " + millisUntilFinished / 1000 + "s");
            }

            @Override
            public void onFinish() {
                resendText.setEnabled(true);
                resendText.setText("Resend OTP");
            }
        }.start();
    }

    private void openUserPortal() {
        Log.d(TAG, "Opening user portal");
        startActivity(new Intent(OtpVerificationActivity.this, Main.class));
        finish();
    }
}
