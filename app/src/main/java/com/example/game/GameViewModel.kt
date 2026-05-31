package com.example.game

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.HighScore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed class GamePhase {
    object MainMenu : GamePhase()
    object Playing : GamePhase()
    object Paused : GamePhase()
    object GameOver : GamePhase()
}

data class GameUIState(
    val phase: GamePhase = GamePhase.MainMenu,
    val playerY: Float = GROUND_Y - STANDING_HEIGHT,
    val playerHeight: Float = STANDING_HEIGHT,
    val playerWidth: Float = STANDING_WIDTH,
    val isSliding: Boolean = false,
    val isJumping: Boolean = false,
    val isDoubleJumping: Boolean = false,
    val activeSkin: PlayerSkin = PlayerSkin.NEON_BLUE,
    val unlockedSkins: Set<String> = setOf(PlayerSkin.NEON_BLUE.name),
    val currentDistance: Int = 0,
    val currentCoins: Int = 0,
    val currentScore: Int = 0,
    val highScore: Int = 0,
    val totalCoins: Int = 0,
    val currentSpeedX: Float = 10f,
    val multiplier: Int = 1,
    val shieldCount: Int = 0,
    val jetpackTimeLeftMs: Long = 0, // 0 means inactive
    val multiplierTimeLeftMs: Long = 0,
    val obstacles: List<Obstacle> = emptyList(),
    val coins: List<Coin> = emptyList(),
    val powerUps: List<PowerUpItem> = emptyList(),
    val particles: List<Particle> = emptyList(),
    val floatingTexts: List<FloatingText> = emptyList(),
    val topScores: List<HighScore> = emptyList(),
    val screenShake: Float = 0f
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: GameRepository
    private val sharedPrefs = application.getSharedPreferences("retro_runner_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(GameUIState())
    val uiState: StateFlow<GameUIState> = _uiState.asStateFlow()

    private var gameJob: Job? = null
    private val random = Random(System.currentTimeMillis())

    // Physics constants
    private val GRAVITY = 1.0f
    private val JUMP_FORCE = -17.5f
    private val DOUBLE_JUMP_FORCE = -14.5f
    private val BASE_SPEED = 9.5f
    private val MAX_SPEED = 24f

    // Running game loop states updated at high frequency (to avoid state flow thrashing inside update thread)
    private var playerY = GROUND_Y - STANDING_HEIGHT
    private var playerVY = 0f
    private var isSliding = false
    private var slideTimer = 0
    private var jumpCount = 0 // 0 = ground, 1 = single jump, 2 = double jump

    private var currentDistance = 0f
    private var lastDistanceCount = 0 // integer tracked distance
    private var matchCoins = 0
    private var currentScore = 0f
    private var currentSpeedX = BASE_SPEED
    private var shieldCount = 0
    private var jetpackMsRemaining = 0L
    private var multiplierMsRemaining = 0L

    private var obstacleIdCounter = 0L
    private var coinIdCounter = 0L
    private var powerUpIdCounter = 0L

    private var finalObstacles = mutableListOf<Obstacle>()
    private var finalCoins = mutableListOf<Coin>()
    private var finalPowerUps = mutableListOf<PowerUpItem>()
    private var finalParticles = mutableListOf<Particle>()
    private var finalFloatingTexts = mutableListOf<FloatingText>()

    private var nextObstacleX = 1200f
    private var nextCoinGroupX = 1300f
    private var nextPowerUpX = 2500f
    private var screenShake = 0f

    // SharedPreferences Keys
    private val KEY_COINS = "total_coins_v1"
    private val KEY_UNLOCKED_SKINS = "unlocked_skins_v1"
    private val KEY_SELECTED_SKIN = "selected_skin_v1"

    init {
        val database = AppDatabase.getDatabase(application)
        repository = GameRepository(database.highScoreDao())

        // Load SharedPreferences states
        val totalCoins = sharedPrefs.getInt(KEY_COINS, 0)
        val selectedSkinName = sharedPrefs.getString(KEY_SELECTED_SKIN, PlayerSkin.NEON_BLUE.name) ?: PlayerSkin.NEON_BLUE.name
        val selectedSkin = try {
            PlayerSkin.valueOf(selectedSkinName)
        } catch (e: Exception) {
            PlayerSkin.NEON_BLUE
        }
        val unlockedSet = sharedPrefs.getStringSet(KEY_UNLOCKED_SKINS, setOf(PlayerSkin.NEON_BLUE.name)) ?: setOf(PlayerSkin.NEON_BLUE.name)

        _uiState.value = _uiState.value.copy(
            totalCoins = totalCoins,
            activeSkin = selectedSkin,
            unlockedSkins = unlockedSet
        )

        // Observe High Scores reactively
        viewModelScope.launch {
            repository.topScores.collectLatest { scores ->
                val highest = repository.getHighScore()
                _uiState.value = _uiState.value.copy(
                    topScores = scores,
                    highScore = highest
                )
            }
        }
    }

    // Input handlers
    fun selectSkin(skin: PlayerSkin) {
        val state = _uiState.value
        if (state.unlockedSkins.contains(skin.name)) {
            sharedPrefs.edit().putString(KEY_SELECTED_SKIN, skin.name).apply()
            _uiState.value = _uiState.value.copy(activeSkin = skin)
        }
    }

    fun purchaseSkin(skin: PlayerSkin) {
        val state = _uiState.value
        if (!state.unlockedSkins.contains(skin.name) && state.totalCoins >= skin.cost) {
            val newCoins = state.totalCoins - skin.cost
            val newUnlocked = state.unlockedSkins.toMutableSet().apply { add(skin.name) }

            sharedPrefs.edit()
                .putInt(KEY_COINS, newCoins)
                .putStringSet(KEY_UNLOCKED_SKINS, newUnlocked)
                .putString(KEY_SELECTED_SKIN, skin.name)
                .apply()

            _uiState.value = _uiState.value.copy(
                totalCoins = newCoins,
                unlockedSkins = newUnlocked,
                activeSkin = skin
            )
        }
    }

    fun jump() {
        if (_uiState.value.phase != GamePhase.Playing || jetpackMsRemaining > 0) return

        if (jumpCount == 0) {
            // Stop slide if sliding
            isSliding = false
            slideTimer = 0

            playerVY = JUMP_FORCE
            jumpCount = 1
            // Spawn standard jumps dust
            spawnJumpSparks(6)
        } else if (jumpCount == 1) {
            playerVY = DOUBLE_JUMP_FORCE
            jumpCount = 2
            // Spawn colorful double jump rings
            spawnJumpSparks(12, Color(0xFFE040FB))
        }
    }

    fun slide(active: Boolean) {
        if (_uiState.value.phase != GamePhase.Playing || jetpackMsRemaining > 0) return

        if (active) {
            isSliding = true
            // If in mid-air, apply fast down force (dive slide!)
            if (jumpCount > 0) {
                playerVY = 15f
            }
            slideTimer = 35 // slides for ~35 frames (approx 600ms) unless released or ended
        } else {
            isSliding = false
            slideTimer = 0
        }
    }

    fun setPhase(phase: GamePhase) {
        when (phase) {
            GamePhase.Playing -> {
                if (_uiState.value.phase == GamePhase.MainMenu || _uiState.value.phase == GamePhase.GameOver) {
                    resetGameState()
                }
                _uiState.value = _uiState.value.copy(phase = GamePhase.Playing)
                startGameLoop()
            }
            GamePhase.Paused -> {
                _uiState.value = _uiState.value.copy(phase = GamePhase.Paused)
                stopGameLoop()
            }
            GamePhase.MainMenu -> {
                _uiState.value = _uiState.value.copy(phase = GamePhase.MainMenu)
                stopGameLoop()
            }
            GamePhase.GameOver -> {
                _uiState.value = _uiState.value.copy(phase = GamePhase.GameOver)
                stopGameLoop()
            }
        }
    }

    private fun resetGameState() {
        playerY = GROUND_Y - STANDING_HEIGHT
        playerVY = 0f
        isSliding = false
        slideTimer = 0
        jumpCount = 0

        currentDistance = 0f
        lastDistanceCount = 0
        matchCoins = 0
        currentScore = 0f
        currentSpeedX = BASE_SPEED
        shieldCount = 0
        jetpackMsRemaining = 0L
        multiplierMsRemaining = 0L

        finalObstacles.clear()
        finalCoins.clear()
        finalPowerUps.clear()
        finalParticles.clear()
        finalFloatingTexts.clear()

        nextObstacleX = 1400f
        nextCoinGroupX = 1200f
        nextPowerUpX = 2500f
        screenShake = 0f

        updateUIStateSnap()
    }

    private fun startGameLoop() {
        gameJob?.cancel()
        gameJob = viewModelScope.launch {
            var lastUpdate = System.currentTimeMillis()
            while (true) {
                val now = System.currentTimeMillis()
                val delta = (now - lastUpdate)
                lastUpdate = now

                if (_uiState.value.phase == GamePhase.Playing) {
                    gameTick(delta)
                }

                delay(16) // ~60fps loop
            }
        }
    }

    private fun stopGameLoop() {
        gameJob?.cancel()
        gameJob = null
    }

    private fun gameTick(deltaMs: Long) {
        val speedFactor = currentSpeedX

        // 1. Update Distance & General multipliers
        val baseSpeedMultiplier = if (jetpackMsRemaining > 0) 2.0f else 1.0f
        val tickDistance = (speedFactor * baseSpeedMultiplier * 0.05f)
        currentDistance += tickDistance

        // Compute Score: distance traveled + multiplier bonuses
        val multip = if (multiplierMsRemaining > 0) 2 else 1
        currentScore += tickDistance * 1.5f * multip

        val distanceInt = currentDistance.toInt()
        val scoreInt = currentScore.toInt()

        // 2. Manage PowerUp expiry timers
        if (jetpackMsRemaining > 0) {
            jetpackMsRemaining = (jetpackMsRemaining - deltaMs).coerceAtLeast(0)
            if (jetpackMsRemaining == 0L) {
                // Jetpack expired, fall back to standing height
                playerY = GROUND_Y - STANDING_HEIGHT
                playerVY = 0f
                jumpCount = 0
                addFloatingText(PLAYER_X, playerY - 30f, "JETPACK END", Color(0xFFFF5252))
            }
        }
        if (multiplierMsRemaining > 0) {
            multiplierMsRemaining = (multiplierMsRemaining - deltaMs).coerceAtLeast(0)
        }

        // 3. Gradual speed increase according to distance
        currentSpeedX = (BASE_SPEED + (distanceInt / 180f)).coerceAtMost(MAX_SPEED)

        // 4. Update Screen shake decay
        if (screenShake > 0f) {
            screenShake = (screenShake - 0.4f).coerceAtLeast(0f)
        }

        // 5. Update Runner's vertical physics
        val skin = _uiState.value.activeSkin
        val trailColor = when (skin) {
            PlayerSkin.NEON_BLUE -> Color(0xFF007AFF)
            PlayerSkin.RETRO_PINK -> Color(0xFFFF007F)
            PlayerSkin.TOXIC_GREEN -> Color(0xFF39FF14)
            PlayerSkin.GOLD_LEGEND -> Color(0xFFFFDF00)
        }

        if (jetpackMsRemaining > 0) {
            // Jetpack floating effect (sinusoidal motion hovering around ground)
            val hoverCenter = GROUND_Y - STANDING_HEIGHT - 120f
            playerY = hoverCenter + (Math.sin(currentDistance.toDouble() / 15f) * 45f).toFloat()

            // Continuous rich fiery trail particle effects
            if (random.nextFloat() < 0.6f) {
                finalParticles.add(
                    Particle(
                        x = PLAYER_X + 5f,
                        y = playerY + STANDING_HEIGHT - 10f,
                        vx = -currentSpeedX * 0.4f - random.nextFloat() * 4f,
                        vy = 3f + random.nextFloat() * 5f,
                        color = Color(0xFFFF5722),
                        size = 8f + random.nextFloat() * 6f,
                        lifeDecay = 0.05f
                    )
                )
                finalParticles.add(
                    Particle(
                        x = PLAYER_X + 5f,
                        y = playerY + STANDING_HEIGHT - 10f,
                        vx = -currentSpeedX * 0.4f - random.nextFloat() * 4f,
                        vy = 1f + random.nextFloat() * 3f,
                        color = Color(0xFFFFEB3B),
                        size = 5f + random.nextFloat() * 4f,
                        lifeDecay = 0.07f
                    )
                )
            }
        } else {
            // Normal gravity mechanics
            playerY += playerVY
            playerVY += GRAVITY

            val currentHeight = if (isSliding) SLIDING_HEIGHT else STANDING_HEIGHT
            val groundLimit = GROUND_Y - currentHeight

            if (playerY >= groundLimit) {
                playerY = groundLimit
                playerVY = 0f
                jumpCount = 0 // Reset Jumps
            }

            // Slide logic count-downs
            if (isSliding) {
                slideTimer--
                if (slideTimer <= 0) {
                    isSliding = false
                }
                // Spark trail beneath slide
                if (random.nextFloat() < 0.4f) {
                    finalParticles.add(
                        Particle(
                            x = PLAYER_X + random.nextInt(35).toFloat(),
                            y = GROUND_Y - 2f,
                            vx = -currentSpeedX * 0.5f - random.nextFloat() * 3f,
                            vy = -random.nextFloat() * 3f,
                            color = trailColor,
                            size = 4f + random.nextFloat() * 5f,
                            lifeDecay = 0.04f
                        )
                    )
                }
            } else {
                // Running standard trail indicator particles
                if (jumpCount == 0 && random.nextFloat() < 0.25f) {
                    finalParticles.add(
                        Particle(
                            x = PLAYER_X,
                            y = GROUND_Y - 5f,
                            vx = -currentSpeedX * 0.4f - random.nextFloat() * 2f,
                            vy = -1f - random.nextFloat() * 2f,
                            color = Color.LightGray.copy(alpha = 0.6f),
                            size = 5f + random.nextFloat() * 5f,
                            lifeDecay = 0.03f
                        )
                    )
                }
            }
        }

        // 6. Move obstacles and clean up offscreen items
        finalObstacles.forEach { obs -> obs.x -= (currentSpeedX * baseSpeedMultiplier) }
        finalObstacles.removeAll { obs -> obs.x < -100f }

        // 7. Move coins and clean up
        finalCoins.forEach { coin -> coin.x -= (currentSpeedX * baseSpeedMultiplier) }
        finalCoins.removeAll { coin -> coin.x < -50f }

        // 8. Move powerups and clean up
        finalPowerUps.forEach { pu -> pu.x -= (currentSpeedX * baseSpeedMultiplier) }
        finalPowerUps.removeAll { pu -> pu.x < -50f }

        // 9. Particles physics and decay
        finalParticles.forEach { p ->
            p.x += p.vx
            p.y += p.vy
            p.alpha -= p.lifeDecay
        }
        finalParticles.removeAll { p -> p.alpha <= 0f || p.y > GROUND_Y + 100f }

        // 10. Floating HUD texts updates
        finalFloatingTexts.forEach { t ->
            t.y += t.vy
            t.life--
            t.alpha = (t.life / 40f).coerceIn(0f, 1f)
        }
        finalFloatingTexts.removeAll { t -> t.life <= 0 }

        // 11. Procedural generator triggers
        val targetVWidth = 1000f
        nextObstacleX -= (currentSpeedX * baseSpeedMultiplier)
        nextCoinGroupX -= (currentSpeedX * baseSpeedMultiplier)
        nextPowerUpX -= (currentSpeedX * baseSpeedMultiplier)

        if (nextObstacleX <= targetVWidth) {
            spawnProceduralObstacle()
        }
        if (nextCoinGroupX <= targetVWidth) {
            spawnProceduralCoins()
        }
        if (nextPowerUpX <= targetVWidth) {
            spawnProceduralPowerUp()
        }

        // 12. Evaluate collisions
        checkCollisions()

        // 13. Update snapshot to the UI
        updateUIStateSnap()
    }

    private fun spawnProceduralObstacle() {
        val nextType = when (random.nextInt(100)) {
            in 0..24 -> ObstacleType.SPIKE
            in 25..49 -> ObstacleType.LOW_WALL
            in 50..74 -> ObstacleType.HIGH_BARRIER
            in 75..89 -> ObstacleType.TALL_WALL
            else -> ObstacleType.DOUBLE_SPIKE
        }

        val obsWidth = when (nextType) {
            ObstacleType.SPIKE -> 40f
            ObstacleType.LOW_WALL -> 45f
            ObstacleType.HIGH_BARRIER -> 48f
            ObstacleType.TALL_WALL -> 45f
            ObstacleType.DOUBLE_SPIKE -> 85f
        }

        val obsHeight = when (nextType) {
            ObstacleType.SPIKE -> 55f
            ObstacleType.LOW_WALL -> 55f
            ObstacleType.HIGH_BARRIER -> 45f
            ObstacleType.TALL_WALL -> 110f
            ObstacleType.DOUBLE_SPIKE -> 42f
        }

        val obsY = when (nextType) {
            ObstacleType.HIGH_BARRIER -> GROUND_Y - 145f // Floating gap beneath it to slide!
            else -> GROUND_Y - obsHeight // Sits directly on ground
        }

        val designX = 1050f
        finalObstacles.add(
            Obstacle(
                id = obstacleIdCounter++,
                x = designX,
                width = obsWidth,
                height = obsHeight,
                type = nextType,
                y = obsY
            )
        )

        // Set gap padding until the next obstacle can generate based on speed (increasing physical space)
        val safetyBuffer = 380f + (currentSpeedX * 18f) + (if (random.nextBoolean()) 120f else 0f)
        nextObstacleX = V_WIDTH + safetyBuffer
    }

    private fun spawnProceduralCoins() {
        // Build fun visual grouping of coins
        val coinY = GROUND_Y - 100f
        val startX = 1050f
        val shapeType = random.nextInt(4)

        when (shapeType) {
            0 -> { // Simple diagonal arch
                for (i in 0 until 4) {
                    val archOffset = when (i) {
                        0, 3 -> 0f
                        1, 2 -> -50f
                        else -> 0f
                    }
                    finalCoins.add(Coin(id = coinIdCounter++, x = startX + (i * 45f), y = coinY + archOffset))
                }
            }
            1 -> { // Sine wave form
                for (i in 0 until 5) {
                    val waveY = coinY - 40f + (Math.sin(i * 1.0) * 50f).toFloat()
                    finalCoins.add(Coin(id = coinIdCounter++, x = startX + (i * 50f), y = waveY))
                }
            }
            2 -> { // High line
                for (i in 0 until 3) {
                    finalCoins.add(Coin(id = coinIdCounter++, x = startX + (i * 45f), y = GROUND_Y - 165f))
                }
            }
            else -> { // Standard straight lower horizontal line
                for (i in 0 until 4) {
                    finalCoins.add(Coin(id = coinIdCounter++, x = startX + (i * 45f), y = GROUND_Y - 90f))
                }
            }
        }

        // Buffer space for coin spawning
        nextCoinGroupX = V_WIDTH + 240f + random.nextInt(300).toFloat()
    }

    private fun spawnProceduralPowerUp() {
        val nextType = when (random.nextInt(3)) {
            0 -> PowerUpType.SHIELD
            1 -> PowerUpType.JETPACK
            else -> PowerUpType.MULTIPLIER
        }

        // Float overhead
        val pY = GROUND_Y - 170f
        finalPowerUps.add(
            PowerUpItem(
                id = powerUpIdCounter++,
                x = 1050f,
                y = pY,
                type = nextType
            )
        )

        // Trigger occasionally after a long travel
        nextPowerUpX = V_WIDTH + 1200f + random.nextInt(1500).toFloat()
    }

    private fun checkCollisions() {
        val pLeft = PLAYER_X
        val pRight = PLAYER_X + (if (isSliding) SLIDING_WIDTH else STANDING_WIDTH)
        val pTop = playerY
        val pBottom = playerY + (if (isSliding) SLIDING_HEIGHT else STANDING_HEIGHT)

        // Jetpack mode causes automatic collision collection bypass (immune to obstacles entirely!)
        val isImmune = jetpackMsRemaining > 0

        // 1. Check obstacle collisions
        for (obs in finalObstacles) {
            if (obs.passed) continue

            // Check bounding box overlaps
            val oLeft = obs.getCollisionLeft()
            val oRight = obs.getCollisionRight()
            val oTop = obs.getCollisionTop()
            val oBottom = obs.getCollisionBottom()

            // Obstacle passed player safely
            if (oRight < pLeft && !obs.passed) {
                obs.passed = true
                val scoreBonus = when (obs.type) {
                    ObstacleType.TALL_WALL, ObstacleType.DOUBLE_SPIKE -> 50
                    else -> 20
                }
                val currentMultip = if (multiplierMsRemaining > 0) 2 else 1
                currentScore += scoreBonus * currentMultip
                addFloatingText(obs.x + obs.width / 2, obs.y - 20f, "+${scoreBonus * currentMultip}", Color(0xFF00FF87))
                continue
            }

            if (!isImmune && pRight > oLeft + 6f && pLeft < oRight - 6f && pBottom > oTop + 6f && pTop < oBottom - 6f) {
                // COLLISION DETECTED!
                obs.passed = true // prevent double hitting

                if (shieldCount > 0) {
                    // Shield active - absorb hit!
                    shieldCount--
                    screenShake = 12f
                    // Explode sparks showing visual feedback
                    spawnExplosionSparks(obs.x + obs.width / 2, obs.y + obs.height / 2, Color(0xFF00E1D9), 20)
                    addFloatingText(PLAYER_X, playerY - 30f, "SHIELD CHARGE LOST!", Color(0xFFFF9800))
                } else {
                    // CRASH GAME OVER!
                    triggerGameOver()
                    return
                }
            }
        }

        // 2. Check Coins
        for (coin in finalCoins) {
            if (coin.collected) continue

            val cLeft = coin.getCollisionLeft()
            val cRight = coin.getCollisionRight()
            val cTop = coin.getCollisionTop()
            val cBottom = coin.getCollisionBottom()

            if (pRight > cLeft && pLeft < cRight && pBottom > cTop && pTop < cBottom) {
                coin.collected = true
                val multip = if (multiplierMsRemaining > 0) 2 else 1
                val coinVal = 1 * multip
                matchCoins += coinVal
                currentScore += 10 * multip
                // Spark particles
                spawnExplosionSparks(coin.x, coin.y, Color(0xFFFFDF00), 5)
                addFloatingText(coin.x, coin.y - 12f, if (multip > 1) "COIN 2X" else "+1", Color(0xFFFFDF00))
            }
        }

        // 3. Check PowerUps
        for (pu in finalPowerUps) {
            if (pu.collected) continue

            val puRight = pu.getCollisionRight()
            val puLeft = pu.getCollisionLeft()
            val puTop = pu.getCollisionTop()
            val puBottom = pu.getCollisionBottom()

            if (pRight > puLeft && pLeft < puRight && pBottom > puTop && pTop < puBottom) {
                pu.collected = true
                spawnExplosionSparks(pu.x + pu.size/2, pu.y + pu.size/2, Color.White, 15)

                when (pu.type) {
                    PowerUpType.SHIELD -> {
                        shieldCount++
                        addFloatingText(pu.x, pu.y - 20f, "SHIELD ON!", Color(0xFF00E1D9))
                    }
                    PowerUpType.JETPACK -> {
                        jetpackMsRemaining = 6500L // 6.5s of jetpack
                        isSliding = false
                        slideTimer = 0
                        addFloatingText(pu.x, pu.y - 20f, "JETPACK SKYHAWK!", Color(0xFFFF5722))
                    }
                    PowerUpType.MULTIPLIER -> {
                        multiplierMsRemaining = 12000L // 12s of 2x Multiplier
                        addFloatingText(pu.x, pu.y - 20f, "2X BOOSTED SCORE!", Color(0xFFFF8F00))
                    }
                }
            }
        }
    }

    private fun triggerGameOver() {
        stopGameLoop()
        screenShake = 22f
        spawnExplosionSparks(PLAYER_X + STANDING_WIDTH / 2, playerY + STANDING_HEIGHT / 2, Color.Red, 35)

        // Update stored currency inside preferences
        val storedCoins = sharedPrefs.getInt(KEY_COINS, 0)
        val newTotal = storedCoins + matchCoins
        sharedPrefs.edit().putInt(KEY_COINS, newTotal).apply()

        // Sync local variables before state phase change
        _uiState.value = _uiState.value.copy(
            phase = GamePhase.GameOver,
            totalCoins = newTotal,
            currentCoins = matchCoins,
            currentScore = currentScore.toInt(),
            currentDistance = currentDistance.toInt()
        )
    }

    // High Score inserts
    fun saveHighScore(name: String) {
        val trimmed = name.trim().ifEmpty { "ANON" }
        viewModelScope.launch {
            repository.insertScore(
                HighScore(
                    playerName = trimmed,
                    score = _uiState.value.currentScore,
                    distance = _uiState.value.currentDistance
                )
            )
        }
    }

    fun clearHighScoreBoard() {
        viewModelScope.launch {
            repository.clearScores()
        }
    }

    // Juice Sparks helper functions
    private fun spawnJumpSparks(count: Int, specColor: Color? = null) {
        val activeSkin = _uiState.value.activeSkin
        val selectedColor = specColor ?: activeSkin.primaryColor

        for (i in 0 until count) {
            finalParticles.add(
                Particle(
                    x = PLAYER_X + (if (isSliding) SLIDING_WIDTH else STANDING_WIDTH) / 2,
                    y = playerY + (if (isSliding) SLIDING_HEIGHT else STANDING_HEIGHT) - 5f,
                    vx = -currentSpeedX * 0.3f + (random.nextFloat() * 8f - 4f),
                    vy = random.nextFloat() * 5f - 1f,
                    color = selectedColor.copy(alpha = 0.8f),
                    size = 5f + random.nextFloat() * 5f,
                    lifeDecay = 0.035f
                )
            )
        }
    }

    private fun spawnExplosionSparks(centerX: Float, centerY: Float, color: Color, count: Int) {
        for (i in 0 until count) {
            val angle = random.nextFloat() * 2 * Math.PI
            val speed = 2f + random.nextFloat() * 8f
            finalParticles.add(
                Particle(
                    x = centerX,
                    y = centerY,
                    vx = (Math.cos(angle) * speed).toFloat() - currentSpeedX * 0.2f,
                    vy = (Math.sin(angle) * speed).toFloat(),
                    color = color,
                    size = 5f + random.nextFloat() * 6f,
                    lifeDecay = 0.02f + random.nextFloat() * 0.03f
                )
            )
        }
    }

    private fun addFloatingText(x: Float, y: Float, text: String, color: Color) {
        finalFloatingTexts.add(
            FloatingText(
                x = x,
                y = y,
                text = text,
                color = color
            )
        )
    }

    private fun updateUIStateSnap() {
        _uiState.value = _uiState.value.copy(
            playerY = playerY,
            playerHeight = if (isSliding) SLIDING_HEIGHT else STANDING_HEIGHT,
            playerWidth = if (isSliding) SLIDING_WIDTH else STANDING_WIDTH,
            isSliding = isSliding,
            isJumping = jumpCount == 1,
            isDoubleJumping = jumpCount == 2,
            currentDistance = currentDistance.toInt(),
            currentCoins = matchCoins,
            currentScore = currentScore.toInt(),
            currentSpeedX = currentSpeedX,
            shieldCount = shieldCount,
            jetpackTimeLeftMs = jetpackMsRemaining,
            multiplierTimeLeftMs = multiplierMsRemaining,
            obstacles = finalObstacles.toList(),
            coins = finalCoins.toList(),
            powerUps = finalPowerUps.toList(),
            particles = finalParticles.toList(),
            floatingTexts = finalFloatingTexts.toList(),
            screenShake = screenShake
        )
    }
}
