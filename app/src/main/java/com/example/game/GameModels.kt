package com.example.game

import androidx.compose.ui.graphics.Color

const val V_WIDTH = 1000f
const val V_HEIGHT = 600f
const val GROUND_Y = 500f
const val PLAYER_X = 150f
const val STANDING_HEIGHT = 80f
const val STANDING_WIDTH = 55f
const val SLIDING_HEIGHT = 45f
const val SLIDING_WIDTH = 75f

enum class PlayerSkin(val displayName: String, val primaryColor: Color, val secondaryColor: Color, val cost: Int) {
    NEON_BLUE("Cyber Blue", Color(0xFF00E1D9), Color(0xFF007AFF), 0),
    RETRO_PINK("Sunset Pink", Color(0xFFFF007F), Color(0xFFFF5E62), 100),
    TOXIC_GREEN("Acid Green", Color(0xFF39FF14), Color(0xFF00FF87), 250),
    GOLD_LEGEND("Aero Gold", Color(0xFFFFDF00), Color(0xFFFF9900), 500)
}

enum class ObstacleType {
    SPIKE,        // Ground hazard (requires single jump)
    LOW_WALL,     // Low barrier (requires single jump)
    HIGH_BARRIER, // High horizontal block (requires slide)
    TALL_WALL,    // Tall barrier (requires double jump)
    DOUBLE_SPIKE  // Wide hazard (requires double jump)
}

enum class PowerUpType {
    SHIELD,     // Absorbs 1 hit
    JETPACK,    // High-speed invincibility & auto-runs/floats
    MULTIPLIER  // 2x score multiplier
}

data class Obstacle(
    val id: Long,
    var x: Float,
    val width: Float,
    val height: Float,
    val type: ObstacleType,
    val y: Float, // Top margin position relative to coordinate scale
    var passed: Boolean = false
) {
    fun getCollisionLeft() = x
    fun getCollisionRight() = x + width
    fun getCollisionTop() = y
    fun getCollisionBottom() = y + height
}

data class Coin(
    val id: Long,
    var x: Float,
    val y: Float,
    val radius: Float = 15f,
    var collected: Boolean = false
) {
    fun getCollisionLeft() = x - radius
    fun getCollisionRight() = x + radius
    fun getCollisionTop() = y - radius
    fun getCollisionBottom() = y + radius
}

data class PowerUpItem(
    val id: Long,
    var x: Float,
    val y: Float,
    val type: PowerUpType,
    val size: Float = 35f,
    var collected: Boolean = false
) {
    fun getCollisionLeft() = x
    fun getCollisionRight() = x + size
    fun getCollisionTop() = y
    fun getCollisionBottom() = y + size
}

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    var alpha: Float = 1.0f,
    val lifeDecay: Float = 0.02f, // decrease in alpha per frame
    val size: Float = 6f
)

data class FloatingText(
    var x: Float,
    var y: Float,
    val text: String,
    val color: Color,
    var alpha: Float = 1.0f,
    val vy: Float = -2f,
    var life: Int = 40 // frames
)
