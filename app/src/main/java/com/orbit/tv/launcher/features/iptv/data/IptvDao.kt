package com.orbit.tv.launcher.features.iptv.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IptvDao {

    @Query("SELECT * FROM channels ORDER BY isFavorite DESC, number ASC")
    fun getAllChannelsFlow(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels ORDER BY isFavorite DESC, number ASC")
    suspend fun getAllChannels(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 ORDER BY number ASC")
    fun getFavoriteChannelsFlow(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE id = :channelId LIMIT 1")
    suspend fun getChannelById(channelId: String): ChannelEntity?

    @Query("DELETE FROM channels")
    suspend fun clearAllChannels()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE id = :channelId")
    suspend fun updateFavorite(channelId: String, isFavorite: Boolean)

    @Query("UPDATE channels SET lastWatchedTimestamp = :timestamp WHERE id = :channelId")
    suspend fun updateLastWatched(channelId: String, timestamp: Long)

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun getChannelCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<ProgramEntity>)

    @Query("SELECT * FROM programs WHERE channelId = :channelId ORDER BY startTime ASC")
    suspend fun getProgramsForChannel(channelId: String): List<ProgramEntity>

    @Query("DELETE FROM programs WHERE endTime < :cutoff")
    suspend fun clearOldPrograms(cutoff: Long)
}
