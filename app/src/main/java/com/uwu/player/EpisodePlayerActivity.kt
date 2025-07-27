package com.uwu.player

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.widget.ImageButton
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import java.io.Serializable


class EpisodePlayerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var sourceTabs: TabLayout
    private lateinit var episodes: List<Episode>
    private var currentEpisodeIndex: Int = 0
    private var seriesId: String? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_episode_player)
        auth = Firebase.auth
        firestore = FirebaseFirestore.getInstance()

        // CAMBIO: Usamos el método getSerializableExtra de forma segura y compatible.
        episodes = getSerializable(intent, "EPISODES_LIST") ?: emptyList()
        currentEpisodeIndex = intent.getIntExtra("CURRENT_EPISODE_INDEX", 0)
        this.seriesId = intent.getStringExtra("SERIES_ID")
        seriesId?.let { HistoryManager.startWatching(this, it) }
        if (episodes.isEmpty()) {
            finish()
            return
        }

        webView = findViewById(R.id.video_webview)
        sourceTabs = findViewById(R.id.source_tabs)

        setupEpisode(currentEpisodeIndex)
        setupNavButtons()
        findViewById<ImageButton>(R.id.report_button).setOnClickListener {
            val selectedTab = sourceTabs.getTabAt(sourceTabs.selectedTabPosition)
            val serverName = selectedTab?.text.toString()
            val url = selectedTab?.tag.toString()
            showReportDialog(serverName, url)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        // Procesa y guarda la sesión al salir
        HistoryManager.stopWatchingAndSave(this)
    }
    private fun setupEpisode(index: Int) {
        currentEpisodeIndex = index
        val episode = episodes[index]

        setupToolbar("${episode.numeroEpisodio} ${episode.titulo}")
        setupSourceTabs(episode.sources)
        updateNavButtons()
    }

    private fun setupToolbar(title: String) {
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = title
    }
    private fun showReportDialog(serverName: String, url: String) {
        AlertDialog.Builder(this)
            .setTitle("Reportar Enlace")
            .setMessage("¿Estás seguro de que quieres reportar que el enlace del servidor '$serverName' no funciona?")
            .setPositiveButton("Sí, reportar") { _, _ ->
                val report = hashMapOf(
                    "contentId" to (seriesId ?: "unknown"),
                    "contentTitle" to episodes[currentEpisodeIndex].titulo,
                    "serverName" to serverName,
                    "reportedUrl" to url,
                    "timestamp" to FieldValue.serverTimestamp()
                )
                firestore.collection("broken_links").add(report)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Enlace reportado. ¡Gracias!", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(sourceValue: String) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36"
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)

        webView.webViewClient = CustomWebViewClient()
        webView.webChromeClient = CustomWebChromeClient(this)

        val parts = sourceValue.split("::", limit = 2)
        val type = parts.getOrNull(0)
        val data = parts.getOrNull(1)

        if (type == "script" && data != null) {
            val html = """
                <!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>body, html { margin: 0; padding: 0; height: 100%; overflow: hidden; background-color: #000; }
                .video-container { position: absolute; top: 0; left: 0; width: 100%; height: 100%; }</style>
                </head><body><div class="video-container">$data</div></body></html>
            """.trimIndent()
            webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
        } else {
            val url = if (type == "url") data ?: "" else sourceValue
            webView.loadUrl(url)
        }
    }

    private fun setupSourceTabs(sources: Map<String, String>) {
        sourceTabs.removeAllTabs() // Limpia pestañas anteriores
        if (sources.isNotEmpty()) {
            sourceTabs.visibility = View.VISIBLE

            val numberRegex = Regex("^source(\\d+)_.*")
            val sortedSources = sources.entries.sortedBy { (key, _) ->
                val matchResult = numberRegex.find(key)
                matchResult?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
            }

            // 1. Primero, añadimos el listener.
            sourceTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    (tab?.tag as? String)?.let { sourceValue ->
                        setupWebView(sourceValue)
                    }
                }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })

            // 2. Luego, añadimos las pestañas. Esto NO activará el listener todavía.
            sortedSources.forEach { (key, url) ->
                val serverName = key.substringAfter('_').replaceFirstChar { it.uppercase() }
                sourceTabs.addTab(sourceTabs.newTab().setText(serverName).setTag(url), false) // El 'false' es importante
            }

            // 3. Finalmente, seleccionamos la primera pestaña. ESTO activará el listener
            //    y cargará el video, pero solo cuando todo esté listo.
            if (sourceTabs.tabCount > 0) {
                sourceTabs.getTabAt(0)?.select()
            }

        } else {
            sourceTabs.visibility = View.GONE
        }
    }

    private fun setupNavButtons() {
        findViewById<MaterialButton>(R.id.prev_episode_button).setOnClickListener {
            if (currentEpisodeIndex > 0) setupEpisode(currentEpisodeIndex - 1)
        }
        findViewById<MaterialButton>(R.id.next_episode_button).setOnClickListener {
            if (currentEpisodeIndex < episodes.size - 1) setupEpisode(currentEpisodeIndex + 1)
        }
        findViewById<MaterialButton>(R.id.back_to_series_button).setOnClickListener {
            finish()
        }
    }

    private fun updateNavButtons() {
        findViewById<MaterialButton>(R.id.prev_episode_button).isEnabled = currentEpisodeIndex > 0
        findViewById<MaterialButton>(R.id.next_episode_button).isEnabled = currentEpisodeIndex < episodes.size - 1
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // Helper simplificado para obtener la lista
    private fun <T : Serializable?> getSerializable(intent: Intent, key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Para APIs 33+, necesitamos especificar el tipo de clase.
            // Como es una lista, usamos ArrayList, que es la implementación concreta.
            intent.getSerializableExtra(key, ArrayList::class.java) as? T
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(key) as? T
        }
    }

}