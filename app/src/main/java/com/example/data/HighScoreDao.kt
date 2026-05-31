package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HighScoreDao {
    @Query("SELECT * FROM high_scores ORDER BY score DESC, distance DESC LIMIT 20")
    fun getTopScores(): Flow<List<HighScore>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScore(score: HighScore)

    @Query("SELECT MAX(score) FROM high_scores")
    suspend fun getHighScore(): Int?

    @Query("DELETE FROM high_scores")
    suspend fun clearAllScores()
}
