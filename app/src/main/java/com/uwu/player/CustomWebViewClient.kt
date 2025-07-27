package com.uwu.player

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import java.net.URI

class CustomWebViewClient : WebViewClient() {

    private var initialHost: String? = null

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (initialHost == null && url != null) {
            initialHost = getHost(url)
        }
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)

        // --- INICIO DE LA CORRECCIÓN ---
        // Ahora solo mostramos el error si es un problema de conexión Y si ocurrió en la página principal.
        if (error?.errorCode == ERROR_HOST_LOOKUP && request?.isForMainFrame == true) {
            // --- FIN DE LA CORRECCIÓN ---
            val customErrorHtml = """
                <html><body style='background-color:black;color:white;display:flex;justify-content:center;align-items:center;height:100%;'>
                <h2>Error de conexión</h2>
                </body></html>
            """.trimIndent()
            view?.loadData(customErrorHtml, "text/html", "UTF-8")
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url == null) return true

        val currentHost = getHost(url)

        if (initialHost == null || currentHost == initialHost) {
            initialHost = currentHost
            return false
        }

        return true // Bloquea la carga de la nueva URL en el WebView.
    }

    private fun getHost(url: String): String {
        return try {
            URI(url).host?.replace("www.", "") ?: url
        } catch (e: Exception) {
            url
        }
    }
}