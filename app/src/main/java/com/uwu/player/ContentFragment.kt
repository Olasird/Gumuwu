package com.uwu.player

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ContentFragment : Fragment() {

    // Vistas
    private lateinit var skeletonContainer: View
    private lateinit var contentContainer: View
    private lateinit var mainRecyclerView: RecyclerView
    private lateinit var tabLayout: TabLayout
    private lateinit var homeSectionsContainer: LinearLayout
    private lateinit var mainGridTitle: TextView
    private lateinit var nestedScrollView: NestedScrollView

    // Adaptadores
    private lateinit var contentAdapter: ContentAdapter
    private var contentList = mutableListOf<Content>()

    // Lógica
    private lateinit var firestore: FirebaseFirestore
    private var contentType: String? = null
    private var listIds: ArrayList<String>? = null
    private var homeDataLoadCount = 0
    private val TOTAL_HOME_LOADS = 4 // Estrenos, Populares, Seguir Viendo, Catálogo

    // Paginación
    private var lastVisible: DocumentSnapshot? = null
    private var isLoading = false
    private val pageSize = 21L

    // Categorías
    private val movieCategories = listOf("Acción", "Aventura", "Crimen", "Deporte", "Infantiles", "Anime", "Comedia", "Romance", "Terror", "Fantasía")
    private val seriesCategories = listOf("Aventura", "Misterio", "Crimen", "Romance", "Drama", "Fantasía", "Comedia", "Ciencia Ficción")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            contentType = it.getString("CONTENT_TYPE")
            listIds = it.getStringArrayList("LIST_IDS")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_content_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        firestore = FirebaseFirestore.getInstance()

        // Inicialización de Vistas
        skeletonContainer = view.findViewById(R.id.skeleton_container)
        contentContainer = view.findViewById(R.id.content_container_main)
        mainRecyclerView = view.findViewById(R.id.content_recycler_view)
        tabLayout = view.findViewById(R.id.tab_layout)
        homeSectionsContainer = view.findViewById(R.id.home_sections_container)
        mainGridTitle = view.findViewById(R.id.main_grid_title)
        nestedScrollView = view.findViewById(R.id.nested_scroll_view)

        showSkeleton(true) // Mostramos el esqueleto al iniciar

        setupMainRecyclerView()

        // Lógica de Carga
        if (listIds != null) {
            setupForUserList()
            fetchContentByIds(listIds!!) { showSkeleton(false) }
        } else if (contentType == "all") {
            HistoryManager.checkPendingHistory(requireContext())
            setupForHome()
            // Reinicia el contador de cargas para la pantalla de inicio
            homeDataLoadCount = 0
            fetchNewReleases(view)
            fetchTrending(view)
            fetchHistory(view)
            fetchPaginatedContent(null, true)
        } else {
            setupForCategoryList()
            mainGridTitle.visibility = View.GONE
            setupTabs()
        }
    }

    private fun showSkeleton(show: Boolean) {
        skeletonContainer.visibility = if (show) View.VISIBLE else View.GONE
        contentContainer.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun onHomeDataLoaded() {
        homeDataLoadCount++
        if (homeDataLoadCount >= TOTAL_HOME_LOADS) {
            showSkeleton(false)
        }
    }

    private fun setupForUserList() {
        tabLayout.visibility = View.GONE
        homeSectionsContainer.visibility = View.GONE
        mainGridTitle.visibility = View.GONE
    }

    private fun setupForHome() {
        tabLayout.visibility = View.GONE
        homeSectionsContainer.visibility = View.VISIBLE
    }

    private fun setupForCategoryList() {
        homeSectionsContainer.visibility = View.GONE
        mainGridTitle.visibility = View.VISIBLE
    }

    private fun setupMainRecyclerView() {
        contentAdapter = ContentAdapter(contentList)
        mainRecyclerView.layoutManager = GridLayoutManager(context, 3)
        mainRecyclerView.adapter = contentAdapter

        nestedScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, oldScrollY ->
            if (v.getChildAt(v.childCount - 1) != null && scrollY >= (v.getChildAt(v.childCount - 1).measuredHeight - v.measuredHeight) && scrollY > oldScrollY) {
                if (!isLoading && listIds == null && lastVisible != null) {
                    val selectedTab = tabLayout.getTabAt(tabLayout.selectedTabPosition)
                    val selectedCategory = if (contentType == "all" || selectedTab?.position == 0) null else selectedTab?.text.toString()
                    fetchPaginatedContent(selectedCategory, false)
                }
            }
        })
    }

    private fun fetchTrending(view: View) {
        val trendingRecyclerView: RecyclerView = view.findViewById(R.id.trending_recycler_view)
        trendingRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        firestore.collection("contenido").whereGreaterThan("popularOrder", 0).orderBy("popularOrder").limit(10).get()
            .addOnSuccessListener { result ->
                val list = result.map { doc -> doc.toObject(Content::class.java).copy(id = doc.id) }
                trendingRecyclerView.adapter = HorizontalContentAdapter(list)
                onHomeDataLoaded()
            }
            .addOnFailureListener { onHomeDataLoaded() }
    }

    private fun fetchNewReleases(view: View) {
        val newReleasesRecyclerView: RecyclerView = view.findViewById(R.id.new_releases_recycler_view)
        newReleasesRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        firestore.collection("contenido").orderBy("fechaPublicacion", Query.Direction.DESCENDING).limit(10).get()
            .addOnSuccessListener { result ->
                val list = result.map { doc -> doc.toObject(Content::class.java).copy(id = doc.id) }
                newReleasesRecyclerView.adapter = HorizontalContentAdapter(list)
                onHomeDataLoaded()
            }
            .addOnFailureListener { onHomeDataLoaded() }
    }

    private fun fetchHistory(view: View) {
        val historyRecyclerView: RecyclerView = view.findViewById(R.id.history_recycler_view)
        historyRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        val historyIds = HistoryManager.getHistory(requireContext())

        if (historyIds.isEmpty()) {
            view.findViewById<TextView>(R.id.history_title).visibility = View.GONE
            historyRecyclerView.visibility = View.GONE
            onHomeDataLoaded()
            return
        }

        firestore.collection("contenido").whereIn(FieldPath.documentId(), historyIds).get()
            .addOnSuccessListener { result ->
                val historyMap = result.map { it.toObject(Content::class.java).copy(id = it.id) }.associateBy { it.id }
                val sortedHistory = historyIds.mapNotNull { id -> historyMap[id] }

                view.findViewById<TextView>(R.id.history_title).visibility = View.VISIBLE
                historyRecyclerView.visibility = View.VISIBLE
                historyRecyclerView.adapter = HorizontalContentAdapter(sortedHistory)
                onHomeDataLoaded()
            }
            .addOnFailureListener { onHomeDataLoaded() }
    }

    private fun fetchPaginatedContent(category: String?, isNewQuery: Boolean) {
        if (isLoading) return
        isLoading = true

        if (isNewQuery) {
            contentList.clear()
            contentAdapter.notifyDataSetChanged()
            lastVisible = null
        }

        var query: Query = firestore.collection("contenido")
        if (contentType != "all") { query = query.whereEqualTo("tipo", contentType) }
        if (category != null) { query = query.whereArrayContains("categorias", category) }
        query = query.orderBy("fechaPublicacion", Query.Direction.DESCENDING).limit(pageSize)
        if (lastVisible != null) { query = query.startAfter(lastVisible!!) }

        query.get().addOnSuccessListener { documents ->
            if (!documents.isEmpty) {
                lastVisible = documents.documents[documents.size() - 1]
                val newContent = documents.map { doc -> doc.toObject(Content::class.java).copy(id = doc.id) }
                val currentSize = contentList.size
                contentList.addAll(newContent)
                contentAdapter.notifyItemRangeInserted(currentSize, newContent.size)
            } else {
                lastVisible = null
            }
            isLoading = false
            if (contentType == "all") onHomeDataLoaded() else showSkeleton(false)
        }.addOnFailureListener { exception ->
            Log.e("ContentFragment", "Error: ", exception)
            isLoading = false
            if (contentType == "all") onHomeDataLoaded() else showSkeleton(false)
        }
    }

    private fun setupTabs() {
        tabLayout.visibility = View.VISIBLE
        val categories = if (contentType == "pelicula") movieCategories else seriesCategories
        tabLayout.addTab(tabLayout.newTab().setText("Todos"))
        categories.forEach { tabLayout.addTab(tabLayout.newTab().setText(it)) }

        fetchPaginatedContent(null, true)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                showSkeleton(true)
                val selectedCategory = if (tab?.position == 0) null else tab?.text.toString()
                fetchPaginatedContent(selectedCategory, true)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun fetchContentByIds(ids: List<String>, onComplete: () -> Unit) {
        if (ids.isEmpty()) {
            Toast.makeText(context, "Esta lista está vacía.", Toast.LENGTH_SHORT).show()
            onComplete()
            return
        }
        firestore.collection("contenido").whereIn(FieldPath.documentId(), ids).get()
            .addOnSuccessListener { result ->
                val contentMap = result.map { it.toObject(Content::class.java).copy(id = it.id) }.associateBy { it.id }
                val sortedList = ids.mapNotNull { id -> contentMap[id] }
                contentAdapter = ContentAdapter(sortedList)
                mainRecyclerView.adapter = contentAdapter
                onComplete()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Error al cargar la lista.", Toast.LENGTH_SHORT).show()
                onComplete()
            }
    }

    companion object {
        @JvmStatic
        fun newInstance(contentType: String) = ContentFragment().apply {
            arguments = Bundle().apply { putString("CONTENT_TYPE", contentType) }
        }

        @JvmStatic
        fun newInstanceForList(ids: ArrayList<String>) = ContentFragment().apply {
            arguments = Bundle().apply { putStringArrayList("LIST_IDS", ids) }
        }
    }
}