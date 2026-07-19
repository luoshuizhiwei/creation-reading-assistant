package local.creationReadingAssistant.mobile;

import android.os.Bundle;
import android.webkit.WebView;

import androidx.activity.OnBackPressedCallback;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    WebView.setWebContentsDebuggingEnabled(true);
    registerPlugin(NativeReaderPlugin.class);
    super.onCreate(savedInstanceState);
    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        if (getBridge() == null || getBridge().getWebView() == null) {
          setEnabled(false);
          MainActivity.super.onBackPressed();
          return;
        }
        WebView webView = getBridge().getWebView();
        webView.post(() -> webView.evaluateJavascript(
          "window.dispatchEvent(new Event('mobile-native-back'))",
          null
        ));
      }
    });
  }
}
