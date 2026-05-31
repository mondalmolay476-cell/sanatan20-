package com.example.data

import kotlinx.coroutines.flow.Flow

class GameRepository(private val highScoreDao: HighScoreDao) {
    val topScores: Flow<List<HighScore>> = highScoreDao.getTopScores()

    suspend fun insertScore(score: HighScore) {
        highScoreDao.insertScore(score)
    }

    suspend fun getHighScore(): Int {
        return highScoreDao.getHighScore() ?: 0
    }

    suspend fun clearScores() {
        highScoreDao.clearAllScores()
    }
}
