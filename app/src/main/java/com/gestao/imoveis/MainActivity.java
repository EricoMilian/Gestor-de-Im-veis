package com.gestao.imoveis;

import android.content.Context;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private Executor executor;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new NativeBiometricBridge(this), "AndroidBiometric");
        webView.loadUrl("file:///android_asset/index.html");
        executor = ContextCompat.getMainExecutor(this);
    }

    public class NativeBiometricBridge {
        private final Context context;
        NativeBiometricBridge(Context c) { context = c; }

        @JavascriptInterface public void authenticate() {
            runOnUiThread(() -> authenticateNative());
        }

        @JavascriptInterface public boolean isAvailable() {
            return BiometricManager.from(context).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    == BiometricManager.BIOMETRIC_SUCCESS;
        }
    }

    private void authenticateNative() {
        BiometricManager bm = BiometricManager.from(this);
        int result = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        if (result != BiometricManager.BIOMETRIC_SUCCESS) {
            String msg = result == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
                    ? "Cadastre uma digital ou reconhecimento facial nas configurações do celular."
                    : "A biometria não está disponível neste aparelho.";
            webView.evaluateJavascript("mostrarToast(" + jsString(msg) + ")", null);
            return;
        }

        BiometricPrompt prompt = new BiometricPrompt(
                this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                webView.post(() -> webView.evaluateJavascript(
                        "window.onNativeBiometricSuccess && window.onNativeBiometricSuccess();", null));
            }

            @Override public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                webView.post(() -> webView.evaluateJavascript(
                        "mostrarToast(" + jsString(errString.toString()) + ")", null));
            }

            @Override public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                webView.post(() -> webView.evaluateJavascript(
                        "mostrarToast('Biometria não reconhecida. Tente novamente.');", null));
            }
        });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Gestão de Imóveis")
                .setSubtitle("Confirme sua digital ou reconhecimento facial")
                .setNegativeButtonText("Cancelar")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build();
        prompt.authenticate(info);
    }

    private static String jsString(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ") + "'";
    }
}
