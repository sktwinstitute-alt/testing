package com.seriousstudy.app

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.seriousstudy.app.databinding.ActivityAppBlocklistBinding
import com.seriousstudy.app.databinding.ItemAppBlockBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppItem(
    val appName: String,
    val packageName: String,
    val icon: Drawable,
    var isBlocked: Boolean
)

class AppBlocklistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppBlocklistBinding
    private val allApps = mutableListOf<AppItem>()
    private val displayedApps = mutableListOf<AppItem>()
    private lateinit var adapter: AppBlockAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppBlocklistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        adapter = AppBlockAdapter(displayedApps) { appItem, isChecked ->
            appItem.isBlocked = isChecked
            updateCount()
        }

        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter

        binding.etSearchApp.addTextChangedListener { text ->
            val query = text?.toString()?.trim() ?: ""
            filterApps(query)
        }

        binding.btnSaveBlocklist.setOnClickListener {
            saveBlocklist()
        }

        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvApps.visibility = View.GONE

        lifecycleScope.launch {
            val pm = packageManager
            val currentBlocked = FocusStateManager.getLocalBlockedApps(this@AppBlocklistActivity)

            val loadedList = withContext(Dispatchers.Default) {
                val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val ownPkg = packageName
                val list = mutableListOf<AppItem>()

                for (appInfo in installed) {
                    val pkg = appInfo.packageName
                    if (pkg == ownPkg) continue

                    // Filter to launchable or non-critical user-facing applications
                    val launchIntent = pm.getLaunchIntentForPackage(pkg)
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

                    // Include if it has a launch intent or is not purely a base system app
                    if (launchIntent != null || (!isSystemApp || isUpdatedSystemApp)) {
                        try {
                            val name = pm.getApplicationLabel(appInfo).toString()
                            val icon = pm.getApplicationIcon(appInfo)
                            val isBlocked = currentBlocked.contains(pkg)
                            list.add(AppItem(name, pkg, icon, isBlocked))
                        } catch (_: Exception) {
                        }
                    }
                }

                // Sort: blocked apps first, then alphabetical by name
                list.sortWith(compareByDescending<AppItem> { it.isBlocked }.thenBy { it.appName.lowercase() })
                list
            }

            allApps.clear()
            allApps.addAll(loadedList)
            displayedApps.clear()
            displayedApps.addAll(loadedList)

            binding.progressBar.visibility = View.GONE
            binding.rvApps.visibility = View.VISIBLE
            adapter.notifyDataSetChanged()
            updateCount()
        }
    }

    private fun filterApps(query: String) {
        displayedApps.clear()
        if (query.isEmpty()) {
            displayedApps.addAll(allApps)
        } else {
            val q = query.lowercase()
            displayedApps.addAll(allApps.filter {
                it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            })
        }
        adapter.notifyDataSetChanged()
    }

    private fun updateCount() {
        val count = allApps.count { it.isBlocked }
        binding.tvSelectedCount.text = "$count selected"
    }

    private fun saveBlocklist() {
        val selectedPackages = allApps.filter { it.isBlocked }.map { it.packageName }.toSet()
        FocusStateManager.setLocalBlockedApps(this, selectedPackages)
        Toast.makeText(this, "Blocklist updated (${selectedPackages.size} apps)", Toast.LENGTH_SHORT).show()
        finish()
    }

    inner class AppBlockAdapter(
        private val items: List<AppItem>,
        private val onCheckChanged: (AppItem, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppBlockAdapter.AppViewHolder>() {

        inner class AppViewHolder(val itemBinding: ItemAppBlockBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val v = ItemAppBlockBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return AppViewHolder(v)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val item = items[position]
            with(holder.itemBinding) {
                tvAppName.text = item.appName
                tvPackageName.text = item.packageName
                ivAppIcon.setImageDrawable(item.icon)

                cbBlockApp.setOnCheckedChangeListener(null)
                cbBlockApp.isChecked = item.isBlocked

                cbBlockApp.setOnCheckedChangeListener { _, isChecked ->
                    onCheckChanged(item, isChecked)
                }

                root.setOnClickListener {
                    cbBlockApp.isChecked = !cbBlockApp.isChecked
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
