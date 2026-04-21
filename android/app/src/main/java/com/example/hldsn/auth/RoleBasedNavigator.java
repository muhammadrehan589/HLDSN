package com.example.hldsn.auth;

import android.content.Intent;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.admin_module.AdminDashboardActivity;
import com.example.hldsn.home.HomePageActivity;
import com.example.hldsn.login_module.LoginActivity;
import com.example.hldsn.ngo_module.NgoDashboardActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public final class RoleBasedNavigator {

    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_USER = "user";
    public static final String ROLE_VOLUNTEER = "volunteer";
    public static final String ROLE_NGO_ADMIN = "ngo_admin";

    private RoleBasedNavigator() {
    }

    public static void routeAuthenticatedUser(AppCompatActivity activity) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            routeTo(activity, LoginActivity.class);
            return;
        }
        routeByUid(activity, user.getUid());
    }

    public static void routeAfterLogin(AppCompatActivity activity, FirebaseUser user) {
        if (user == null) {
            routeTo(activity, LoginActivity.class);
            return;
        }
        routeByUid(activity, user.getUid());
    }

    private static void routeByUid(AppCompatActivity activity, String uid) {
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String normalizedRole = normalizeRole(documentSnapshot.getString("role"));
                    routeTo(activity, resolveDestination(normalizedRole));
                })
                .addOnFailureListener(error -> {
                    Toast.makeText(activity, "Could not load role, opening home", Toast.LENGTH_SHORT).show();
                    routeTo(activity, HomePageActivity.class);
                });
    }

    private static Class<?> resolveDestination(String role) {
        if (ROLE_ADMIN.equals(role)) {
            return AdminDashboardActivity.class;
        }
        if (ROLE_NGO_ADMIN.equals(role)) {
            return NgoDashboardActivity.class;
        }
        return HomePageActivity.class;
    }

    private static String normalizeRole(String role) {
        if (role == null) {
            return ROLE_USER;
        }
        return role.trim().toLowerCase(Locale.US);
    }

    private static void routeTo(AppCompatActivity activity, Class<?> destination) {
        Intent intent = new Intent(activity, destination);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
