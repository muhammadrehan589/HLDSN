package com.example.hldsn.auth;

import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.admin_module.AdminDashboardActivity;
import com.example.hldsn.home.HomePageActivity;
import com.example.hldsn.login_module.LoginActivity;
import com.example.hldsn.ngo_module.NgoDashboardActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

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
        // We use Source.SERVER to ensure we get the latest role from the backend.
        // This prevents issues where a recently promoted admin/ngo is still seen 
        // as a regular 'user' due to local Firestore caching.
        Log.d("RoleBasedNavigator", "Fetching role for UID: " + uid + " from SERVER");
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get(Source.SERVER)
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String role = documentSnapshot.getString("role");
                        Log.d("RoleBasedNavigator", "Role from SERVER: " + role);
                        String normalizedRole = normalizeRole(role);
                        routeTo(activity, resolveDestination(normalizedRole));
                    } else {
                        Log.w("RoleBasedNavigator", "Document not found on SERVER, trying CACHE");
                        fetchFromCache(activity, uid);
                    }
                })
                .addOnFailureListener(error -> {
                    Log.e("RoleBasedNavigator", "SERVER fetch failed: " + error.getMessage() + ", trying CACHE");
                    fetchFromCache(activity, uid);
                });
    }

    private static void fetchFromCache(AppCompatActivity activity, String uid) {
        Log.d("RoleBasedNavigator", "Fetching role from CACHE for UID: " + uid);
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get(Source.CACHE)
                .addOnSuccessListener(documentSnapshot -> {
                    String role = documentSnapshot.getString("role");
                    Log.d("RoleBasedNavigator", "Role from CACHE: " + role);
                    String normalizedRole = normalizeRole(role);
                    routeTo(activity, resolveDestination(normalizedRole));
                })
                .addOnFailureListener(error -> {
                    Log.e("RoleBasedNavigator", "CACHE fetch failed: " + error.getMessage());
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

        String normalized = role.trim().toLowerCase(Locale.US)
                .replace('-', '_')
                .replace(' ', '_');

        if ("ngoadmin".equals(normalized)) {
            return ROLE_NGO_ADMIN;
        }

        if ("admin".equals(normalized)) {
            return ROLE_ADMIN;
        }

        if ("volunteer".equals(normalized)) {
            return ROLE_VOLUNTEER;
        }

        if ("user".equals(normalized)) {
            return ROLE_USER;
        }

        return normalized;
    }

    private static void routeTo(AppCompatActivity activity, Class<?> destination) {
        Intent intent = new Intent(activity, destination);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
