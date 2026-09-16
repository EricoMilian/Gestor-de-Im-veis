package com.gestao.imoveis;

import android.content.Context;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import java.security.KeyStore;
import java.util.concurrent.Executor;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private static final String KEY_ALIAS = "gestao_imoveis_biometria_local_v1";
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
            BiometricManager bm = BiometricManager.from(context);
            return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
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
        try {
            Cipher cipher = getCipher();
            BiometricPrompt.CryptoObject crypto = new BiometricPrompt.CryptoObject(cipher);
            BiometricPrompt prompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult authResult) {
                    super.onAuthenticationSucceeded(authResult);
                    webView.post(() -> webView.evaluateJavascript("window.onNativeBiometricSuccess && window.onNativeBiometricSuccess();", null));
                }
                @Override public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    webView.post(() -> webView.evaluateJavascript("mostrarToast(" + jsString(errString.toString()) + ")", null));
                }
                @Override public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    webView.post(() -> webView.evaluateJavascript("mostrarToast('Biometria não reconhecida. Tente novamente.');", null));
                }
            });
            BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Gestão de Imóveis")
                    .setSubtitle("Confirme sua digital ou reconhecimento facial")
                    .setNegativeButtonText("Cancelar")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    .build();
            prompt.authenticate(info, crypto);
        } catch (Exception e) {
            createKey();
            webView.evaluateJavascript("mostrarToast('Prepare a biometria do celular e tente novamente.');", null);
        }
    }

    private Cipher getCipher() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(KEY_ALIAS)) createKey();
        SecretKey key = ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher;
    }

    private void createKey() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(true)
                    .build();
            kg.init(spec);
            kg.generateKey();
        } catch (Exception ignored) { }
    }

    private static String jsString(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ") + "'";
    }
}
