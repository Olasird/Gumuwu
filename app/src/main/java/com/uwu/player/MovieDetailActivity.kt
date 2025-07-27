package com.uwu.player

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.webkit.WebView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase

class MovieDetailActivity : AppCompatActivity() {

    // --- Vistas ---
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var webView: WebView
    private lateinit var sourceTabs: TabLayout
    private lateinit var favoriteButton: ImageButton
    private lateinit var watchlistButton: ImageButton
    private lateinit var sagaSpinner: Spinner // <-- NUEVO
    // --- Datos ---
    private var currentContent: Content? = null
    private var isFavorite = false
    private var isInWatchlist = false
    private var sagaMovies: List<Content> = listOf() // <-- NUEVO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_movie_detail)

        firestore = FirebaseFirestore.getInstance()
        auth = Firebase.auth

        // Inicializar vistas
        webView = findViewById(R.id.video_webview)
        sourceTabs = findViewById(R.id.source_tabs)
        favoriteButton = findViewById(R.id.favorite_button)
        watchlistButton = findViewById(R.id.watchlist_button)
        sagaSpinner = findViewById(R.id.saga_spinner) // <-- NUEVO

        val receivedContent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("CONTENT_EXTRA", Content::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("CONTENT_EXTRA") as? Content
        }

        if (receivedContent == null) {
            finish()
            return
        }
        this.currentContent = receivedContent
        HistoryManager.startWatching(this, currentContent!!.id)

        // Comprueba si la película es parte de una saga
        if (!currentContent!!.saga_id.isNullOrBlank()) {
            fetchAllSagaMovies(currentContent!!.saga_id)
        }

        // Actualiza la UI con la información de la película inicial
        updateUIWithContent(currentContent!!)
    }


    override fun onDestroy() {
        super.onDestroy()
        // Procesa y guarda la sesión al salir
        HistoryManager.stopWatchingAndSave(this)
    }

    // Nueva función para centralizar la actualización de toda la pantalla
    private fun updateUIWithContent(content: Content) {
        setupToolbar(content.titulo)

        findViewById<TextView>(R.id.detail_title).text = content.titulo
        findViewById<TextView>(R.id.detail_year).text = content.año.toString()
        findViewById<TextView>(R.id.detail_description).text = content.descripcion
        findViewById<TextView>(R.id.detail_imdb_rating).text = content.rating_imdb
        findViewById<TextView>(R.id.detail_rotten_rating).text = content.rating_rotten

        findViewById<ImageButton>(R.id.report_button).setOnClickListener {
            val selectedTab = sourceTabs.getTabAt(sourceTabs.selectedTabPosition)
            val serverName = selectedTab?.text.toString()
            val url = selectedTab?.tag.toString()
            showReportDialog(serverName, url)
        }

        setupSourceTabs(content.sources)

        favoriteButton.setOnClickListener { handleListUpdate("favoritos", !isFavorite, content.id) }
        watchlistButton.setOnClickListener { handleListUpdate("watchlist", !isInWatchlist, content.id) }

        fetchRecommendations(content)
        checkUserLists(content.id)
    }

    private fun fetchAllSagaMovies(sagaId: String) {
        firestore.collection("contenido")
            .whereEqualTo("saga_id", sagaId)
            .orderBy("año", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { result ->
                if (result.size() > 1) {
                    sagaMovies = result.map { doc -> doc.toObject(Content::class.java).copy(id = doc.id) }
                    setupSagaSpinner()
                }
            }
    }

    private fun setupSagaSpinner() {
        sagaSpinner.visibility = View.VISIBLE
        val movieTitles = sagaMovies.mapIndexed { index, content ->
            "${index + 1}. ${content.titulo}"
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, movieTitles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sagaSpinner.adapter = adapter

        val currentMovieIndex = sagaMovies.indexOfFirst { it.id == currentContent?.id }
        if (currentMovieIndex != -1) {
            sagaSpinner.setSelection(currentMovieIndex, false)
        }

        sagaSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedMovie = sagaMovies[position]
                if (selectedMovie.id != currentContent?.id) {
                    currentContent = selectedMovie
                    updateUIWithContent(selectedMovie)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupToolbar(title: String) {
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = title
    }

    private fun handleListUpdate(listName: String, shouldAdd: Boolean, contentId: String) {
        val user = auth.currentUser
        if (user == null || user.isAnonymous) {
            startActivity(Intent(this, LoginActivity::class.java))
            Toast.makeText(this, "Inicia sesión para guardar contenido", Toast.LENGTH_SHORT).show()
            return
        }

        val userDocRef = firestore.collection("usuarios").document(user.uid)
        val updateAction = if (shouldAdd) FieldValue.arrayUnion(contentId) else FieldValue.arrayRemove(contentId)

        userDocRef.update(listName, updateAction).addOnSuccessListener {
            if (listName == "favoritos") {
                isFavorite = shouldAdd
                updateButtonState(favoriteButton, isFavorite, R.drawable.ic_favorite_filled, R.drawable.ic_favorite_border)
            } else {
                isInWatchlist = shouldAdd
                updateButtonState(watchlistButton, isInWatchlist, R.drawable.ic_bookmark_filled, R.drawable.ic_bookmark_border)
            }
        }
    }

    private fun checkUserLists(contentId: String) {
        val user = auth.currentUser
        if (user == null || user.isAnonymous) {
            updateButtonState(favoriteButton, false, R.drawable.ic_favorite_filled, R.drawable.ic_favorite_border)
            updateButtonState(watchlistButton, false, R.drawable.ic_bookmark_filled, R.drawable.ic_bookmark_border)
            return
        }

        firestore.collection("usuarios").document(user.uid).get().addOnSuccessListener { document ->
            if (document.exists()) {
                val favorites = document.get("favoritos") as? List<*>
                val watchlist = document.get("watchlist") as? List<*>
                isFavorite = favorites?.contains(contentId) == true
                isInWatchlist = watchlist?.contains(contentId) == true
                updateButtonState(favoriteButton, isFavorite, R.drawable.ic_favorite_filled, R.drawable.ic_favorite_border)
                updateButtonState(watchlistButton, isInWatchlist, R.drawable.ic_bookmark_filled, R.drawable.ic_bookmark_border)
            }
        }
    }

    private fun updateButtonState(button: ImageButton, isActive: Boolean, activeIcon: Int, inactiveIcon: Int) {
        button.setImageResource(if (isActive) activeIcon else inactiveIcon)
        val tintColor = if (isActive) R.color.red_500 else R.color.gray_500
        button.setColorFilter(ContextCompat.getColor(this, tintColor))
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
            webView.loadDataWithBaseURL("[https://www.youtube.com](https://www.youtube.com)", html, "text/html", "UTF-8", null)
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

    private fun showReportDialog(serverName: String, url: String) {
        // Obtenemos el ID de forma segura directamente del contenido actual.
        val contentId = currentContent?.id ?: return

        AlertDialog.Builder(this)
            .setTitle("Reportar Enlace")
            .setMessage("¿Estás seguro de que quieres reportar que el enlace del servidor '$serverName' no funciona?")
            .setPositiveButton("Sí, reportar") { _, _ ->
                val report = hashMapOf(
                    "contentId" to contentId,
                    "contentTitle" to (currentContent?.titulo ?: "unknown"),
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

    private fun fetchRecommendations(currentContent: Content) {
        val recommendationsLabel: TextView = findViewById(R.id.recommendations_label)
        val recommendationsRecyclerView: RecyclerView = findViewById(R.id.recommendations_recycler_view)

        if (currentContent.categorias.isEmpty()) return

        firestore.collection("contenido")
            .whereArrayContainsAny("categorias", currentContent.categorias)
            .limit(10)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val contentList = result.map { doc -> doc.toObject(Content::class.java).copy(id = doc.id) }
                        .filter { it.id != currentContent.id }
                        .take(6)

                    if (contentList.isNotEmpty()) {
                        recommendationsLabel.visibility = View.VISIBLE
                        recommendationsRecyclerView.visibility = View.VISIBLE

                        recommendationsRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                        recommendationsRecyclerView.adapter = ContentAdapter(contentList)
                    }
                }
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}