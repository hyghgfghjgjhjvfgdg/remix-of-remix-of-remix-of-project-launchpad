package com.moviemax.app;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Background membership reads use the signed-in user's token, encrypted with Android Keystore. */
final class Membership {
    private static SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if(!ks.containsAlias("moviemax-session")) {
            KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder("moviemax-session",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); g.generateKey();
        }
        return (SecretKey)ks.getKey("moviemax-session",null);
    }
    static synchronized void remember(Context c,String token) {
        if(token==null || token.isEmpty()) { Util.prefs(c).edit().remove("auth_cipher").remove("auth_iv").putLong("member_until",0).apply(); return; }
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
            Util.prefs(c).edit().putString("auth_cipher",Base64.encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP))
                .putString("auth_iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).apply();
        } catch(Exception ignored) { }
    }
    private static String request(String url,String body,String type,String bearer) throws Exception {
        HttpURLConnection con=(HttpURLConnection)new URL(url).openConnection(); con.setRequestMethod("POST");
        con.setConnectTimeout(10000);con.setReadTimeout(12000);con.setDoOutput(true);con.setRequestProperty("Content-Type",type);
        if(bearer!=null) con.setRequestProperty("Authorization","Bearer "+bearer);
        try {
            con.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            if(con.getResponseCode()!=200) throw new Exception("Membership unavailable");
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            byte[] buf=new byte[8192]; int n;
            try(java.io.InputStream in=con.getInputStream()) { while((n=in.read(buf))>0) out.write(buf,0,n); }
            return out.toString("UTF-8");
        } finally { con.disconnect(); }
    }
    static synchronized void refresh(Context c) {
        String encrypted=Util.prefs(c).getString("auth_cipher",""); if(encrypted.isEmpty()) return;
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(Util.prefs(c).getString("auth_iv",""),Base64.NO_WRAP)));
            String refresh=new String(cipher.doFinal(Base64.decode(encrypted,Base64.NO_WRAP)),StandardCharsets.UTF_8);
            String apiKey=android.net.Uri.parse(BuildConfig.ACTIVE_SITE_DOC).getQueryParameter("key");
            JSONObject session=new JSONObject(request("https://securetoken.googleapis.com/v1/token?key="+apiKey,"grant_type=refresh_token&refresh_token="+java.net.URLEncoder.encode(refresh,"UTF-8"),"application/x-www-form-urlencoded",null));
            String uid=session.getString("user_id");
            JSONObject query=new JSONObject().put("structuredQuery",new JSONObject()
                .put("from",new JSONArray().put(new JSONObject().put("collectionId","luo_subscriptions")))
                .put("where",new JSONObject().put("fieldFilter",new JSONObject().put("field",new JSONObject().put("fieldPath","user_id")).put("op","EQUAL").put("value",new JSONObject().put("stringValue",uid)))));
            String base=BuildConfig.ACTIVE_SITE_DOC.substring(0,BuildConfig.ACTIVE_SITE_DOC.indexOf("/app_settings/"));
            JSONArray rows=new JSONArray(request(base+":runQuery",query.toString(),"application/json",session.getString("id_token")));
            long until=0;
            for(int i=0;i<rows.length();i++) {
                JSONObject doc=rows.getJSONObject(i).optJSONObject("document"); if(doc==null) continue;
                JSONObject fields=doc.getJSONObject("fields"),status=fields.optJSONObject("status");
                if(status==null || !"active".equalsIgnoreCase(status.optString("stringValue"))) continue;
                JSONObject expiry=fields.optJSONObject("expires_at");
                if(expiry==null) { until=Math.max(until,System.currentTimeMillis()+86400000); continue; }
                String date=expiry.optString("timestampValue",expiry.optString("stringValue"));
                try { java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",java.util.Locale.US); format.setTimeZone(java.util.TimeZone.getTimeZone("UTC")); java.util.Date parsed=format.parse(date); if(parsed!=null) until=Math.max(until,parsed.getTime()); } catch(Exception ignored) { }
            }
            remember(c,session.getString("refresh_token")); Util.prefs(c).edit().putLong("member_until",until).apply();
        } catch(Exception ignored) { /* Keep the last verified expiry on network failures. */ }
    }
}
