package ru.mark99.gk_heats;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.webkit.HttpAuthHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewDatabase;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class WebViewActivity extends Activity {
    private final static String TAG = "WebView";

    private String host;
    private String username;
    private String password;

    WebView webView;

    public static void openWeb(Context ctx, String host, String username, String password) {
        Intent intent = new Intent(ctx, WebViewActivity.class);
        intent.putExtra("host", host);
        intent.putExtra("username", username);
        intent.putExtra("password", password);
        ctx.startActivity(intent);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Bundle arguments = getIntent().getExtras();
        if (arguments == null) {
            Log.e(TAG, "arguments is null, final activity");
            finish();
            return;
        }

        host = arguments.getString("host");
        username = arguments.getString("username");
        password = arguments.getString("password");

        Log.d(TAG, "[" + host + "] opening page");

        webView = findViewById(R.id.browser);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewActivity.MyWebViewClient());
        webView.loadUrl("http://" + host + "/");
    }

    class MyWebViewClient extends WebViewClient {
        @Override
        public void onReceivedHttpAuthRequest(
                WebView view,
                HttpAuthHandler handler, String host, String realm
        ) {
            Log.d(TAG, "[" + WebViewActivity.this.host + "] processing auth");
            WebViewDatabase.getInstance(getApplicationContext())
                    .setHttpAuthUsernamePassword(host, realm, username, password);
            handler.proceed(username, password);
        }
    }

}