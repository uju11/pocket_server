package com.remotemedia.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * M1.4 — Per-user watch state: position, duration, played flag, updated_at.
 * Backed by SQLite (watch_state.db).  Thread-safe via SQLiteOpenHelper.
 */
object WatchStateManager {

    data class WatchState(
        val user: String,
        val itemId: String,
        val positionSec: Long,
        val durationSec: Long,
        val played: Boolean,
        val updatedAt: Long   // epoch millis
    )

    private lateinit var db: SQLiteDatabase

    fun init(context: Context) {
        db = WatchStateDbHelper(context).writableDatabase
    }

    // ── Public API ────────────────────────────────────────────────

    fun upsert(state: WatchState) {
        val cv = ContentValues().apply {
            put("user", state.user)
            put("item_id", state.itemId)
            put("position_sec", state.positionSec)
            put("duration_sec", state.durationSec)
            put("played", if (state.played) 1 else 0)
            put("updated_at", state.updatedAt)
        }
        db.insertWithOnConflict("watch_state", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun get(user: String, itemId: String): WatchState? {
        db.query("watch_state", null, "user=? AND item_id=?", arrayOf(user, itemId),
            null, null, null).use { c ->
            if (!c.moveToFirst()) return null
            return WatchState(
                user = c.getString(c.getColumnIndexOrThrow("user")),
                itemId = c.getString(c.getColumnIndexOrThrow("item_id")),
                positionSec = c.getLong(c.getColumnIndexOrThrow("position_sec")),
                durationSec = c.getLong(c.getColumnIndexOrThrow("duration_sec")),
                played = c.getInt(c.getColumnIndexOrThrow("played")) != 0,
                updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
            )
        }
    }

    /** All states for [user], ordered by most recently updated. */
    fun listForUser(user: String): List<WatchState> {
        val results = mutableListOf<WatchState>()
        db.query("watch_state", null, "user=?", arrayOf(user),
            null, null, "updated_at DESC").use { c ->
            while (c.moveToNext()) {
                results += WatchState(
                    user = c.getString(c.getColumnIndexOrThrow("user")),
                    itemId = c.getString(c.getColumnIndexOrThrow("item_id")),
                    positionSec = c.getLong(c.getColumnIndexOrThrow("position_sec")),
                    durationSec = c.getLong(c.getColumnIndexOrThrow("duration_sec")),
                    played = c.getInt(c.getColumnIndexOrThrow("played")) != 0,
                    updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
                )
            }
        }
        return results
    }

    // ── DB helper ─────────────────────────────────────────────────

    private class WatchStateDbHelper(ctx: Context) :
        SQLiteOpenHelper(ctx, "watch_state.db", null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE watch_state (
                    user        TEXT NOT NULL,
                    item_id     TEXT NOT NULL,
                    position_sec INTEGER NOT NULL DEFAULT 0,
                    duration_sec INTEGER NOT NULL DEFAULT 0,
                    played      INTEGER NOT NULL DEFAULT 0,
                    updated_at  INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (user, item_id)
                )
            """.trimIndent())
        }

        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
            db.execSQL("DROP TABLE IF EXISTS watch_state")
            onCreate(db)
        }
    }
}
