package com.martitakip.app

import android.app.Application
import com.martitakip.app.data.local.AppDatabase
import org.osmdroid.config.Configuration

/**
 * Process entry point.
 *
 * osmdroid's global [Configuration] is initialised here (before any Activity
 * or MapView exists) so the User-Agent required by the OSM tile policy is in
 * place for the very first tile request.
 */
class MartiTakipApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Configuration.getInstance().apply {
            load(this@MartiTakipApplication, getSharedPreferences("osmdroid", MODE_PRIVATE))
            // Required by https://operations.osmfoundation.org/policies/tiles/
            userAgentValue = BuildConfig.OSM_USER_AGENT
        }

        // Warm the database singleton on a background thread so the first tap
        // does not pay the SQLite open cost.
        Thread {
            runCatching { AppDatabase.getInstance(this).openHelper.writableDatabase }
        }.apply {
            name = "db-warmup"
            priority = Thread.MIN_PRIORITY
            start()
        }
    }
}
