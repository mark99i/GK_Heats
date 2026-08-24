package ru.mark99.gk_heats;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.HttpAuthHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.HashMap;

public class MainActivity extends Activity {
    WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        HashMap<String, String> headers = new HashMap<>();
        String basicAuthHeader = android.util.Base64.encodeToString(
                ("Lunaris" + ":" + "1234554321").getBytes(),
                android.util.Base64.NO_WRAP
        );
        headers.put("Authorization", "Basic " + basicAuthHeader);

        webView = findViewById(R.id.browser);
        webView.setWebViewClient(new MyWebViewClient ());
        webView.loadUrl("http://10.13.164.114/");
    }

    class MyWebViewClient extends WebViewClient {
        @Override
        public void onReceivedHttpAuthRequest(
                WebView view,
                HttpAuthHandler handler, String host, String realm
        ) {
            handler.proceed("Lunaris", "1234554321");
        }
    }
}