package com.shio.saikyo.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object Table {
//    @Entity
//    data class LocalMediaLocation(
//        
//    )

    @Entity
    data class VideoMetadata(
        val videoUri: String,
        @PrimaryKey val id: Int = videoUri.hashCode(),
        val subtitleUri: String,
        val name: String,
        val lengthMs: Long,
    )

    @Entity(
        primaryKeys = ["videoId", "subtitleIndex"]
    )
    data class Subtitle(
        val videoId: Int,
        val subtitleIndex: Int,
        val startMs: Long,
        val endMs: Long,
        val text: String
    )
}

data class VideoMetadata(
    val name: String,
    val lengthMs: Long,
)

class Subtitle(
//    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

data class MediaSubtitles(
    val videoName: String,
    val lengthMs: Long,
    val subs: List<Subtitle>
)

data class PlaybackState(
    val isPlaying: Boolean,
    val currTs: Long,
    val playbackSpeed: Float,
    val currSubtitleIndex: Int,
    val currSubtitleText: String?,
) {
    companion object {
        val UNINITIALIZED = PlaybackState(
            false,
            -1,
            -1f,
            currSubtitleIndex = -1,
            currSubtitleText = ""
        )
    }
}

@Dao
abstract class MediaData {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertVideo(video: Table.VideoMetadata)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSubtitles(subs: List<Table.Subtitle>)

    @Query(
        """
        select
            name,
            lengthMs
        from videometadata
        where id = :videoId
    """
    )
    abstract suspend fun getVideoMetadata(videoId: Int): VideoMetadata

    open suspend fun getVideoMetadata(videoUri: String): VideoMetadata = getVideoMetadata(videoUri.hashCode())

    @Query(
        """
        select
            startMs,
            endMs,
            text
        from subtitle
        where videoId = :videoId
    """
    )
    abstract suspend fun getSubtitlesFor(videoId: Int): List<Subtitle>

    @Transaction
    open suspend fun getSubtitlesFor(videoUri: String): MediaSubtitles {
        val videoId = videoUri.hashCode()

        val metadata = getVideoMetadata(videoId)
        val subs = getSubtitlesFor(videoId)

        return MediaSubtitles(
            metadata.name,
            metadata.lengthMs,
            subs
        )
    }
}

@Dao
abstract class SubtitleData {
    private val _state = MutableStateFlow(PlaybackState.UNINITIALIZED)

    open fun getPlaybackState() = _state.asStateFlow()

    open fun putPlaybackState(updater: (PlaybackState) -> PlaybackState) {
        _state.update(updater)
    }
}

@Database(
    entities = [
        Table.VideoMetadata::class, Table.Subtitle::class
    ],
    version = 1
)
abstract class MediaDb : RoomDatabase() {
    abstract fun mediaData(): MediaData

    abstract fun playbackData(): SubtitleData

    companion object {
        @Volatile
        private var instance: MediaDb? = null

        fun getDatabase(ctx: Context): MediaDb {

            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return instance ?: synchronized(this) {
                instance = Room.databaseBuilder(
                    ctx.applicationContext,
                    MediaDb::class.java,
                    "media.sqlite3"
                )
//                    .fallbackToDestructiveMigration() // TODO: proper migrations
                    .build()

                return@synchronized instance!!
            }
        }
    }
}