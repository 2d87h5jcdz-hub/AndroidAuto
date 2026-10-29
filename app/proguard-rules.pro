# Mantém a ponte JavaScript do player (chamada por reflexão pelo WebView)
-keepclassmembers class dev.androidauto.ytcar.player.PlayerBridge {
    @android.webkit.JavascriptInterface <methods>;
}
