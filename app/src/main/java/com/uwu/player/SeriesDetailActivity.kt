package com.uwu.player

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.view.View
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class SeriesDetailActivity : AppCompatActivity() {

    // --- Vistas ---
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var favoriteButton: ImageButton
    private lateinit var watchlistButton: ImageButton
    private lateinit var seasonSpinner: Spinner
    private lateinit var episodesRecyclerView: RecyclerView

    // --- Datos ---
    private var isFavorite = false
    private var isInWatchlist = false
    private var allSeasons: List<Content> = listOf() // Lista para guardar todas las temporadas de la serie
    private var currentContent: Content? = null // La temporada que se está mostrando actualmente

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series_detail)

        firestore = FirebaseFirestore.getInstance()
        auth = Firebase.auth

        // Inicializar Vistas
        seasonSpinner = findViewById(R.id.season_spinner)
        episodesRecyclerView = findViewById(R.id.episodes_recycler_view)
        episodesRecyclerView.layoutManager = LinearLayoutManager(this)
        favoriteButton = findViewById(R.id.favorite_button)
        watchlistButton = findViewById(R.id.watchlist_button)

        // Recibimos el contenido (la temporada en la que se hizo clic)
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

        // Usamos el título general de la serie para la toolbar
        setupToolbar(receivedContent.titulo_serie)

        // El punto de partida: buscar todas las temporadas de esta serie
        fetchAllSeasons(receivedContent.titulo_serie)
    }

    override fun onResume() {
        super.onResume()
        // Cuando volvemos a la pantalla, re-verificamos el estado de favorito/watchlist
        currentContent?.let { checkUserLists(it.id) }
    }

    private fun setupToolbar(title: String) {
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = title
    }

    private fun fetchAllSeasons(seriesTitle: String) {
        firestore.collection("contenido")
            .whereEqualTo("titulo_serie", seriesTitle)
            .orderBy("temporada", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    finish() // Si no se encuentran temporadas, cerramos
                    return@addOnSuccessListener
                }
                allSeasons = result.map { doc -> doc.toObject(Content::class.java).copy(id = doc.id) }
                setupSpinner()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al cargar las temporadas.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupSpinner() {
        // Creamos una lista de nombres para el desplegable (ej. "Temporada 1", "Temporada 2")
        val seasonTitles = allSeasons.map { "Temporada ${it.temporada}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, seasonTitles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        seasonSpinner.adapter = adapter

        // Encontramos el índice de la temporada en la que el usuario hizo clic para seleccionarla por defecto
        val currentSeasonIndex = allSeasons.indexOfFirst { it.id == currentContent?.id }
        if (currentSeasonIndex != -1) {
            seasonSpinner.setSelection(currentSeasonIndex)
        }

        // El listener que se activa cada vez que el usuario elige una temporada
        seasonSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedSeason = allSeasons[position]
                currentContent = selectedSeason // Actualizamos la temporada actual
                updateUIWithContent(selectedSeason) // Actualizamos toda la info de la pantalla
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // Esta función central actualiza toda la pantalla con la info de una temporada específica
    private fun updateUIWithContent(content: Content) {
        findViewById<ImageView>(R.id.series_poster).load(content.portadaUrl)
        findViewById<TextView>(R.id.detail_title).text = content.titulo
        findViewById<TextView>(R.id.detail_description).text = content.descripcion
        findViewById<TextView>(R.id.detail_year).text = content.año.toString()
        findViewById<TextView>(R.id.detail_season).text = "Temporada: ${content.temporada}"
        findViewById<TextView>(R.id.detail_imdb_rating).text = content.rating_imdb
        findViewById<TextView>(R.id.detail_rotten_rating).text = content.rating_rotten

        // Configuramos los botones para la temporada actual
        favoriteButton.setOnClickListener { handleListUpdate("favoritos", !isFavorite) }
        watchlistButton.setOnClickListener { handleListUpdate("watchlist", !isInWatchlist) }

        // Buscamos los episodios, recomendaciones y estado de favoritos para la temporada actual
        fetchEpisodesForSeason(content.id)
        fetchRecommendations(content)
        checkUserLists(content.id)
    }

    private fun fetchEpisodesForSeason(seasonId: String) {
        firestore.collection("contenido").document(seasonId).collection("episodios")
            .orderBy("numeroEpisodio")
            .get()
            .addOnSuccessListener { result ->
                val episodes = result.toObjects(Episode::class.java)
                // Le pasamos el ID de la serie general para la navegación entre capítulos
                episodesRecyclerView.adapter = EpisodeAdapter(episodes, seasonId)
            }
    }

    private fun handleListUpdate(listName: String, shouldAdd: Boolean) {
        val user = auth.currentUser
        if (user == null || user.isAnonymous) {
            startActivity(Intent(this, LoginActivity::class.java))
            Toast.makeText(this, "Inicia sesión para guardar contenido", Toast.LENGTH_SHORT).show()
            return
        }

        val contentId = currentContent?.id ?: return
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
            // Si no está logueado, los botones deben estar en estado inactivo
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

    private fun fetchRecommendations(content: Content) {
        val recommendationsLabel: TextView = findViewById(R.id.recommendations_label)
        val recommendationsRecyclerView: RecyclerView = findViewById(R.id.recommendations_recycler_view)

        if (content.categorias.isEmpty()) return

        firestore.collection("contenido")
            .whereArrayContainsAny("categorias", content.categorias)
            .limit(10)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val contentList = result.map { doc -> doc.toObject(Content::class.java).copy(id = doc.id) }
                        .filter { it.id != content.id }
                        .take(6)

                    if (contentList.isNotEmpty()) {
                        recommendationsLabel.visibility = View.VISIBLE
                        recommendationsRecyclerView.visibility = View.VISIBLE
                        recommendationsRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                        recommendationsRecyclerView.adapter = ContentAdapter(contentList)
                    } else {
                        recommendationsLabel.visibility = View.GONE
                        recommendationsRecyclerView.visibility = View.GONE
                    }
                }
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}