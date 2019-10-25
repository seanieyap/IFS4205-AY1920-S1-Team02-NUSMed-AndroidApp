package com.example.nusmedapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;

public class UtilityFunctions {
    private static final String TAG = "DEBUG - UtilityFunction";

    public static boolean validateResponseAuth(Context ctx, String authHeader) throws Exception {
        boolean authenticated = false;

        String newJwt = authHeader.substring(7);
        Log.d(TAG, "validateResponseAuth() :: newJwt: " + newJwt);

        // Separate JWT into header, claims and signature
        String[] newJwtParts = newJwt.split("\\.");
        String claims = newJwtParts[0];
        String signature = newJwtParts[1];

        // Verify signature in JWT
        byte[] modulusBytes = Base64.decode(ctx.getString(R.string.m), Base64.DEFAULT);
        byte[] exponentBytes = Base64.decode(ctx.getString(R.string.e), Base64.DEFAULT);
        BigInteger modulus = new BigInteger(1, modulusBytes);
        BigInteger exponent = new BigInteger(1, exponentBytes);

        RSAPublicKeySpec rsaPubKey = new RSAPublicKeySpec(modulus, exponent);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PublicKey pubKey = kf.generatePublic(rsaPubKey);

        Signature signCheck = Signature.getInstance("SHA256withRSA");
        signCheck.initVerify(pubKey);
        signCheck.update(Base64.decode(claims, Base64.DEFAULT));
        authenticated = signCheck.verify(Base64.decode(signature, Base64.DEFAULT));

        return authenticated;
    }

    public static String getJwtFromHeader(String authHeader) {
        String newJwt = authHeader.substring(7);
        Log.d(TAG, "getJwt() :: newJwt: " + newJwt);

        return newJwt;
    }

    public static void storeJwtToPref(Context ctx, String newJwt) throws Exception {
        // Store JWT in EncryptedSharedPreferences
        String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

        SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                "secret_shared_prefs",
                masterKeyAlias,
                ctx,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("jwt", newJwt);
        editor.apply();
    }

    public static String getRolesFromJwt(String newJwt) throws Exception {
        // Separate JWT into header, claims and signature
        String[] newJwtParts = newJwt.split("\\.");
        String claims = newJwtParts[0];

        // Get roles from JWT
        byte[] claimsBytes = Base64.decode(claims, Base64.DEFAULT);
        String claimsString = new String(claimsBytes, "UTF-8");
        JSONObject jwtObj = new JSONObject(claimsString);

        return jwtObj.getString("Roles");
    }

    public static int updateJwt(Context ctx) {
        int responseCode = 500;

        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

            SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                    "secret_shared_prefs",
                    masterKeyAlias,
                    ctx,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            String deviceID = sharedPreferences.getString("deviceID", null);
            String jwt = sharedPreferences.getString("jwt", null);

            URL url = new
                    URL("https://ifs4205team2-1.comp.nus.edu.sg/api/account/updatejwt");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            String credentialsString = jwt + ":" + deviceID;
            Log.d(TAG, "updateJwt() :: credentialsString: " + credentialsString);
            String encodedCredentialsString = Base64.encodeToString(
                    credentialsString.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + encodedCredentialsString);
            Log.d(TAG, "updateJwt() :: Authorization: Bearer " + encodedCredentialsString);
            conn.connect();

            responseCode = conn.getResponseCode();
            Log.d(TAG, "updateJwt() :: responseCode: " + Integer.toString(responseCode));

            switch (responseCode) {
                case 200:
                    boolean validSig = UtilityFunctions.validateResponseAuth(ctx,
                            conn.getHeaderField("Authorization"));

                    if (validSig) {
                        String newJwt = UtilityFunctions.getJwtFromHeader(
                                conn.getHeaderField("Authorization"));
                        UtilityFunctions.storeJwtToPref(ctx, newJwt);
                    }

                    break;
                case 401:
                    break;
                default:
                    break;
            }

        } catch (Exception e) {
            Log.e(TAG, "An Exception occurred...", e);
            // Deal with timeout/ no internet connection
        }

        return responseCode;
    }

}
