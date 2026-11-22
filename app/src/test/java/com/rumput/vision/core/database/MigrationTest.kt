package com.rumput.vision.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Builds a real version-1 database from the exported schema (app/schemas/.../1.json), then opens it with
 * the current [RumputDatabase]. Room runs the auto-migration and validates the result against the
 * expected v2 schema, failing the open if they differ.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `v1 migrates to the current version, keeping server profiles`() = runBlocking {
        val dbFile = context.getDatabasePath("migration-test.db").apply { parentFile?.mkdirs(); delete() }
        createVersion1(dbFile)

        val db = Room.databaseBuilder(context, RumputDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()
        try {
            val profiles = db.serverProfileDao().observeAll().first()
            assertEquals(listOf("Rumah"), profiles.map { it.displayName })
            assertTrue(db.cameraDao().getForProfile("p1").isEmpty())
            assertEquals(CURRENT_VERSION, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    private fun createVersion1(file: File) {
        val schema = Json.parseToJsonElement(File(SCHEMA_DIR, "1.json").readText()).jsonObject.getValue("database").jsonObject
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
            schema.getValue("entities").jsonArray.forEach { entity ->
                val e = entity.jsonObject
                val table = e.getValue("tableName").jsonPrimitive.content
                sqlite.execSQL(e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
            schema.getValue("setupQueries").jsonArray.forEach { sqlite.execSQL(it.jsonPrimitive.content) }
            sqlite.execSQL(
                "INSERT INTO server_profiles (id, display_name, local_url, remote_url, auth_mode, username, " +
                    "endpoint_preference, is_active, created_at, updated_at) " +
                    "VALUES ('p1', 'Rumah', 'http://frigate.home.arpa', NULL, 'NONE', NULL, 'AUTOMATIC', 1, 1, 1)",
            )
            sqlite.version = 1
        }
    }

    private companion object {
        const val CURRENT_VERSION = 4

        // Unit tests run with the module directory as the working directory.
        val SCHEMA_DIR = File("schemas/com.rumput.vision.core.database.RumputDatabase")
    }
}
