package com.mymeditation.app.util

import android.content.Context
import android.os.Environment
import com.mymeditation.app.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object DatabaseExporter {

    suspend fun exportDatabase(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            val dbPath = db.openHelper.writableDatabase.path
                ?: return@withContext Result.failure(Exception("Database path not found"))

            val exportDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "MyMeditation"
            )
            if (!exportDir.exists()) exportDir.mkdirs()

            val exportFile = File(exportDir, "mymeditation_backup.db")
            FileInputStream(dbPath).use { input ->
                FileOutputStream(exportFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Also copy WAL and SHM if they exist
            listOf("-wal", "-shm").forEach { suffix ->
                val src = File(dbPath + suffix)
                if (src.exists()) {
                    FileInputStream(src).use { input ->
                        FileOutputStream(File(exportDir, "mymeditation_backup.db$suffix")).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            Result.success(exportFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importDatabase(context: Context, importPath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            db.close()

            val dbPath = db.openHelper.writableDatabase.path
                ?: return@withContext Result.failure(Exception("Database path not found"))

            val importFile = File(importPath)
            if (!importFile.exists()) {
                return@withContext Result.failure(Exception("Import file not found"))
            }

            FileInputStream(importFile).use { input ->
                FileOutputStream(File(dbPath)).use { output ->
                    input.copyTo(output)
                }
            }

            // Invalidate the singleton so it gets recreated
            val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
            field.isAccessible = true
            field.set(null, null)

            Result.success("Import successful")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
