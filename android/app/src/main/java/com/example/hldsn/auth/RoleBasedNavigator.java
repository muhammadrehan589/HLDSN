package com.example.hldsn.auth;

import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.hldsn.admin_module.AdminDashboardActivity;
import com.example.hldsn.home.HomePageActivity;
import com.example.hldsn.login_module.LoginActivity;
import com.example.hldsn.ngo_module.NgoDashboardActivity;
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

    /**
     * Routes to the appropriate dashboard based on the user's Firestore role.
     * No post-routing work is performed.
     */
    public static void routeAfterLogin(AppCompatActivity activity, FirebaseUser user) {
        routeAfterLogin(activity, user, null);
    }

    /**
     * Routes to the appropriate dashboard based on the user's Firestore role.
     *
     * @param afterRoleFetched Optional callback executed <b>after</b> the role has
     *                         been successfully read from Firestore but <b>before</b>
     *                         the activity transition. Use this to perform work that
     *                         writes to the same {@code users/{uid}} document (e.g.
     *                         mesh-identity sync) so the write does not create a
     *                         pending-write that poisons the role read on fresh installs.
     */
    public static void routeAfterLogin(AppCompatActivity activity, FirebaseUser user,
                                        @Nullable Runnable afterRoleFetched) {
        if (user == null) {
            routeTo(activity, LoginActivity.class);
            return;
        }
        routeByUid(activity, user.getUid(), afterRoleFetched);
    }

    private static void routeByUid(AppCompatActivity activity, String uid,
                                    @Nullable Runnable afterRoleFetched) {
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
                        if (role == null) {
                            Log.w("RoleBasedNavigator",
                                    "Document exists but 'role' field is null for UID: " + uid
                                            + ". Defaulting to user role.");
                        }
                        String normalizedRole = normalizeRole(role);
                        // Role has been read — safe to run the callback now, before
                        // we navigate away. Any Firestore writes it triggers will no
                        // longer race with this read.
                        runIfPresent(afterRoleFetched);
                        routeTo(activity, resolveDestination(normalizedRole));
                    } else {
                        Log.w("RoleBasedNavigator", "Document not found on SERVER, trying CACHE");
                        fetchFromCache(activity, uid, afterRoleFetched);
                    }
                })
                .addOnFailureListener(error -> {
                    Log.e("RoleBasedNavigator", "SERVER fetch failed: " + error.getMessage() + ", trying CACHE");
                    fetchFromCache(activity, uid, afterRoleFetched);
                });
    }

    private static void fetchFromCache(AppCompatActivity activity, String uid,
                                        @Nullable Runnable afterRoleFetched) {
        Log.d("RoleBasedNavigator", "Fetching role from CACHE for UID: " + uid);
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get(Source.CACHE)
                .addOnSuccessListener(documentSnapshot -> {
                    String role = documentSnapshot.getString("role");
                    Log.d("RoleBasedNavigator", "Role from CACHE: " + role);
                    String normalizedRole = normalizeRole(role);
                    runIfPresent(afterRoleFetched);
                    routeTo(activity, resolveDestination(normalizedRole));
                })
                .addOnFailureListener(error -> {
                    Log.e("RoleBasedNavigator", "CACHE fetch failed: " + error.getMessage());
                    Toast.makeText(activity, "Could not load role, opening home", Toast.LENGTH_SHORT).show();
                    runIfPresent(afterRoleFetched);
                    routeTo(activity, HomePageActivity.class);
                });
    }

    private static void runIfPresent(@Nullable Runnable callback) {
        if (callback != null) {
            callback.run();
        }
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
