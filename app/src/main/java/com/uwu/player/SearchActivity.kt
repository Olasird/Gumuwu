package com.uwu.player

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class SearchActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        firestore = FirebaseFirestore.getInstance()
        val query = intent.getStringExtra("SEARCH_QUERY")

        setupToolbar(query)

        if (!query.isNullOrBlank()) {
            performSearch(query)
        }
    }

    private fun setupToolbar(query: String?) {
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Resultados para: '$query'"
    }

    private fun performSearch(query: String) {
        val recyclerView: RecyclerView = findViewById(R.id.search_results_recycler_view)
        recyclerView.layoutManager = GridLayoutManager(this, 3)

        firestore.collection("contenido").get()
            .addOnSuccessListener { documents ->
                val allContent = documents.map { doc ->
                    doc.toObject(Content::class.java).copy(id = doc.id)
                }

                // Filtramos en el cliente (en la app)
                val filteredList = allContent.filter { content ->
                    content.titulo.lowercase(Locale.getDefault())
                        .contains(query.lowercase(Locale.getDefault()))
                }

                recyclerView.adapter = ContentAdapter(filteredList)
            }
            .addOnFailureListener { exception ->
                Log.e("SearchActivity", "Error al buscar: ", exception)
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}