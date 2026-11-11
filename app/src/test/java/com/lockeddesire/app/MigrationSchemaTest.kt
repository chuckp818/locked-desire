package com.lockeddesire.app

import com.lockeddesire.app.data.AppDatabase
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Prüft die handgeschriebene Migration 1→2 gegen das Schema, das Room beim Kompilieren exportiert.
 * Weicht sie ab, würde Room bei Nutzern mit Version 1 die Migration ablehnen.
 */
class MigrationSchemaTest {
    @Test fun migrationMatchesExportedSchema() {
        val file = listOf(
            File("schemas/com.lockeddesire.app.data.AppDatabase/2.json"),
            File("app/schemas/com.lockeddesire.app.data.AppDatabase/2.json")
        ).firstOrNull { it.exists() } ?: throw AssertionError("Room-Schema 2.json fehlt")
        val entities = JSONObject(file.readText()).getJSONObject("database").getJSONArray("entities")
        var checked = 0
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            if (!table.startsWith("Hardcore")) continue
            val create = e.getString("createSql").replace("\${TABLE_NAME}", table)
            assertTrue("Tabelle $table weicht ab:\n$create", create in AppDatabase.MIGRATION_1_2_SQL)
            val idx = e.optJSONArray("indices")
            if (idx != null) for (j in 0 until idx.length()) {
                val sql = idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)
                assertTrue("Index weicht ab:\n$sql", sql in AppDatabase.MIGRATION_1_2_SQL)
            }
            checked++
        }
        assertTrue("Hardcore-Tabellen im Schema gefunden: $checked", checked == 3)
    }
}
