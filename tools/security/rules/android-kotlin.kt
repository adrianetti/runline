// Deliberately unsafe, non-executable fixtures for the local security rules.
// They contain no real keys or personal activity data.
import android.content.Context
import android.util.Log
import android.webkit.*
import java.security.cert.X509Certificate
import javax.crypto.Cipher
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.X509TrustManager

fun storageAndCrypto(context: Context, apiKey: String, settings: WebSettings) {
    // ruleid: android-insecure-cipher
    Cipher.getInstance("AES/ECB/PKCS5Padding")
    // ruleid: android-insecure-cipher
    Cipher.getInstance("AES")
    // ok: android-insecure-cipher
    Cipher.getInstance("AES/GCM/NoPadding")
    // ruleid: android-world-readable-storage
    context.openFileOutput("file", Context.MODE_WORLD_READABLE)
    // ok: android-world-readable-storage
    context.openFileOutput("file", Context.MODE_PRIVATE)
    // ruleid: android-permissive-hostname-verifier
    val unsafe = HostnameVerifier { host, session -> true }
    // ok: android-permissive-hostname-verifier
    val safe = HostnameVerifier { host, session -> javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session) }
    // ruleid: android-webview-universal-file-access
    settings.allowUniversalAccessFromFileURLs = true
    // ok: android-webview-universal-file-access
    settings.allowUniversalAccessFromFileURLs = false
    // ruleid: android-log-credential-variable
    Log.d("network", apiKey)
    // ruleid: android-log-credential-variable
    println(apiKey)
    // ok: android-log-credential-variable
    Log.d("network", "Request completed")
}
class UnsafeTrust : X509TrustManager {
    // ruleid: android-empty-certificate-validation
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) { }
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) { throw java.security.cert.CertificateException() }
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
class SafeTrust(private val delegate: X509TrustManager) : X509TrustManager {
    // ok: android-empty-certificate-validation
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) { delegate.checkServerTrusted(chain, authType) }
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) { delegate.checkClientTrusted(chain, authType) }
    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
}
class UnsafeWeb : WebViewClient() {
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
        // ruleid: android-webview-ignore-ssl-error
        handler.proceed()
    }
}
class SafeWeb : WebViewClient() {
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
        // ok: android-webview-ignore-ssl-error
        handler.cancel()
    }
}
