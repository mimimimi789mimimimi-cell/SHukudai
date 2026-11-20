package com.example.jiyu

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// テーマ更新用JSONのURL（GitHub rawなど）。空のままなら通信しません。
const val UPDATE_URL = ""

@Entity(tableName = "themes")
data class Theme(
    @PrimaryKey val id: String,
    val genre: String,
    val emoji: String,
    val title: String,
    val explanation: String,
    val summary: String,
    val source: String,
    val shown: Boolean = false,
    val current: Boolean = false,
    val favorite: Boolean = false,
    val viewedAt: Long = 0L
)

@Dao
interface ThemeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(l: List<Theme>)
    @Query("SELECT * FROM themes WHERE current=1") fun current(): Flow<List<Theme>>
    @Query("SELECT * FROM themes WHERE favorite=1 ORDER BY title") fun favorites(): Flow<List<Theme>>
    @Query("SELECT * FROM themes WHERE viewedAt>0 ORDER BY viewedAt DESC") fun history(): Flow<List<Theme>>
    @Query("SELECT * FROM themes WHERE id=:id") fun byId(id: String): Flow<Theme?>
    @Query("SELECT * FROM themes WHERE shown=0") suspend fun unshown(): List<Theme>
    @Query("SELECT COUNT(*) FROM themes WHERE current=1") suspend fun currentCount(): Int
    @Query("UPDATE themes SET current=0") suspend fun clearCurrent()
    @Query("UPDATE themes SET shown=0 WHERE current=0") suspend fun resetShown()
    @Query("UPDATE themes SET current=1, shown=1 WHERE id IN (:ids)") suspend fun setCurrent(ids: List<String>)
    @Query("UPDATE themes SET favorite=:f WHERE id=:id") suspend fun setFav(id: String, f: Boolean)
    @Query("UPDATE themes SET viewedAt=:t WHERE id=:id") suspend fun setViewed(id: String, t: Long)
}

@Database(entities = [Theme::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): ThemeDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "jiyu.db").build().also { inst = it }
        }
    }
}

fun parseThemes(s: String): List<Theme> {
    val a = JSONObject(s).getJSONArray("themes")
    return (0 until a.length()).map {
        val o = a.getJSONObject(it)
        Theme(
            o.getString("id"), o.getString("genre"), o.optString("emoji", "📚"),
            o.getString("title"), o.getString("explanation"),
            o.optString("summary"), o.optString("source")
        )
    }
}

suspend fun newBatch(dao: ThemeDao) {
    dao.clearCurrent()
    var pool = dao.unshown()
    if (pool.size < 10) { dao.resetShown(); pool = dao.unshown() }
    val byGenre = pool.groupBy { it.genre }.mapValues { it.value.shuffled().toMutableList() }
    val keys = byGenre.keys.shuffled()
    val picked = mutableListOf<Theme>()
    while (picked.size < 10 && byGenre.values.any { it.isNotEmpty() }) {
        for (k in keys) {
            val l = byGenre[k]!!
            if (l.isNotEmpty() && picked.size < 10) picked.add(l.removeAt(0))
        }
    }
    dao.setCurrent(picked.map { it.id })
}

suspend fun seed(c: Context, dao: ThemeDao) {
    val s = c.assets.open("themes.json").bufferedReader().use { it.readText() }
    dao.insertAll(parseThemes(s))
    if (dao.currentCount() == 0) newBatch(dao)
}

fun onWifi(c: Context): Boolean {
    val cm = c.getSystemService(ConnectivityManager::class.java) ?: return false
    val n = cm.activeNetwork ?: return false
    val cap = cm.getNetworkCapabilities(n) ?: return false
    return cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}

fun updateFromWeb(c: Context, dao: ThemeDao) {
    if (UPDATE_URL.isBlank() || !onWifi(c)) return
    try {
        val con = URL(UPDATE_URL).openConnection() as HttpURLConnection
        con.connectTimeout = 8000; con.readTimeout = 8000
        val s = con.inputStream.bufferedReader().use { it.readText() }
        kotlinx.coroutines.runBlocking { dao.insertAll(parseThemes(s)) }
    } catch (_: Exception) { }
}
