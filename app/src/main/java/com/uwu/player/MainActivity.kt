package com.uwu.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var bottomNavigationView: BottomNavigationView
    private var isUpdateForced = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("isDarkMode", false)
        AppCompatDelegate.setDefaultNightMode(if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)

        setContentView(R.layout.activity_main)

        auth = Firebase.auth
        firestore = FirebaseFirestore.getInstance()

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        bottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigationView.setOnItemSelectedListener { item ->
            handleBottomNavigation(item.itemId)
        }

        checkForcedUpdate {
            if (savedInstanceState == null) {
                bottomNavigationView.selectedItemId = R.id.nav_home
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
        }
    }

    private fun handleBottomNavigation(itemId: Int): Boolean {
        if (isUpdateForced) {
            showForcedUpdateDialog()
            return false
        }

        val fragment = when (itemId) {
            R.id.nav_movies -> ContentFragment.newInstance("pelicula")
            R.id.nav_series -> ContentFragment.newInstance("serie")
            R.id.nav_home -> ContentFragment.newInstance("all")
            else -> return false
        }
        replaceFragment(fragment)
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    val intent = Intent(this@MainActivity, SearchActivity::class.java)
                    intent.putExtra("SEARCH_QUERY", query)
                    startActivity(intent)
                    searchView.clearFocus()
                    searchItem.collapseActionView()
                }
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean = false
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_user_options) {
            val anchorView = findViewById<View>(R.id.action_user_options)
            if (anchorView != null) {
                showUserOptionsMenu(anchorView)
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showUserOptionsMenu(anchorView: View) {
        val popup = PopupMenu(this, anchorView)
        popup.menuInflater.inflate(R.menu.user_options_menu, popup.menu)

        val user = auth.currentUser
        val isLoggedIn = user != null && !user.isAnonymous

        // Configura la visibilidad de los ítems del menú
        popup.menu.findItem(R.id.nav_login).isVisible = !isLoggedIn
        popup.menu.findItem(R.id.nav_logout).isVisible = isLoggedIn
        popup.menu.findItem(R.id.nav_favorites).isVisible = isLoggedIn
        popup.menu.findItem(R.id.nav_watchlist).isVisible = isLoggedIn
        popup.menu.findItem(R.id.nav_theme_toggle).title = if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) "Modo Claro" else "Modo Oscuro"

        popup.setOnMenuItemClickListener { menuItem ->
            handleUserOptionsNavigation(menuItem.itemId)
            true
        }

        // --- TRUCO PARA MOSTRAR ICONOS ---
        // Este código fuerza al PopupMenu a mostrar los iconos.
        try {
            val fieldMPopup = PopupMenu::class.java.getDeclaredField("mPopup")
            fieldMPopup.isAccessible = true
            val mPopup = fieldMPopup.get(popup)
            mPopup.javaClass
                .getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                .invoke(mPopup, true)
        } catch (e: Exception) {
            // Error al mostrar iconos, pero la app seguirá funcionando.
        }
        // --- FIN DEL TRUCO ---

        popup.show()
    }

    private fun handleUserOptionsNavigation(itemId: Int) {
        if (isUpdateForced && itemId != R.id.nav_update && itemId != R.id.nav_logout) {
            showForcedUpdateDialog()
            return
        }
        when (itemId) {
            R.id.nav_login -> startActivity(Intent(this, LoginActivity::class.java))
            R.id.nav_favorites -> fetchUserListAndShow("favoritos")
            R.id.nav_watchlist -> fetchUserListAndShow("watchlist")
            R.id.nav_update -> handleUpdateCheck()
            R.id.nav_theme_toggle -> toggleTheme()
            R.id.nav_logout -> {
                auth.signOut()
                auth.signInAnonymously()
                Toast.makeText(this, "Sesión cerrada.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleTheme() {
        val sharedPreferences = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        val isCurrentlyDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES

        with(sharedPreferences.edit()) {
            putBoolean("isDarkMode", !isCurrentlyDark)
            apply()
        }
        AppCompatDelegate.setDefaultNightMode(if (isCurrentlyDark) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES)
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun checkForcedUpdate(onComplete: (() -> Unit)? = null) {
        firestore.collection("app_info").document("latest").get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val minRequiredVersion = document.getLong("minRequiredVersionCode") ?: 1
                    val downloadUrl = document.getString("downloadUrl") ?: ""
                    try {
                        val packageInfo = packageManager.getPackageInfo(packageName, 0)
                        val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
                        if (currentVersionCode < minRequiredVersion) {
                            isUpdateForced = true
                            showForcedUpdateDialog(downloadUrl)
                        } else {
                            isUpdateForced = false
                            onComplete?.invoke()
                        }
                    } catch (e: Exception) {
                        isUpdateForced = false
                        onComplete?.invoke()
                    }
                } else {
                    isUpdateForced = false
                    onComplete?.invoke()
                }
            }.addOnFailureListener {
                isUpdateForced = false
                onComplete?.invoke()
            }
    }

    private fun showForcedUpdateDialog(downloadUrl: String = "") {
        firestore.collection("app_info").document("latest").get().addOnSuccessListener { document ->
            val url = document?.getString("downloadUrl") ?: downloadUrl
            AlertDialog.Builder(this)
                .setTitle("Actualización Requerida")
                .setMessage("Hay una nueva versión de la aplicación disponible. Para continuar, por favor, actualiza.")
                .setCancelable(false)
                .setPositiveButton("Actualizar Ahora") { _, _ ->
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        Toast.makeText(this, "No se pudo abrir el enlace de descarga.", Toast.LENGTH_SHORT).show()
                    }
                    finish()
                }
                .setNegativeButton("Cancelar") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun handleUpdateCheck() {
        Toast.makeText(this, "Verificando actualizaciones...", Toast.LENGTH_SHORT).show()
        firestore.collection("app_info").document("latest").get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val packageInfo = packageManager.getPackageInfo(packageName, 0)
                    val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
                    val latestVersionCode = document.getLong("versionCode") ?: 0
                    val downloadUrl = document.getString("downloadUrl")

                    if (latestVersionCode > currentVersionCode && !downloadUrl.isNullOrEmpty()) {
                        AlertDialog.Builder(this)
                            .setTitle("Actualización Disponible")
                            .setMessage("Hay una nueva versión disponible. ¿Deseas actualizar ahora?")
                            .setPositiveButton("Actualizar") { _, _ ->
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
                            }
                            .setNegativeButton("Más tarde", null)
                            .show()
                    } else {
                        Toast.makeText(this, "Ya tienes la última versión.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun fetchUserListAndShow(listName: String) {
        val userId = auth.currentUser?.uid
        if (userId == null || auth.currentUser!!.isAnonymous) {
            Toast.makeText(this, "Inicia sesión para ver tu lista.", Toast.LENGTH_SHORT).show()
            return
        }
        firestore.collection("usuarios").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val ids = document.get(listName) as? ArrayList<String>
                    if (!ids.isNullOrEmpty()) {
                        replaceFragment(ContentFragment.newInstanceForList(ids))
                        // --- LÍNEA CORREGIDA ---
                        // Ya no forzamos la vuelta a "Inicio"
                    } else {
                        Toast.makeText(this, "Tu lista de '$listName' está vacía.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Tu lista de '$listName' está vacía.", Toast.LENGTH_SHORT).show()
                }
            }
    }
}