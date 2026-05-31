package com.example.game

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.HighScore
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0B1E)) // Cool dark cyber cosmic space background
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // 1. Core Gameplay Canvas View
        GamePlayCanvas(
            state = state,
            onCanvasTap = { viewModel.jump() },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Play Mode Overlays (HUD, Active timers, Controls)
        if (state.phase == GamePhase.Playing) {
            GameHUD(state = state, onPauseClick = { viewModel.setPhase(GamePhase.Paused) })
            GameControls(
                onJump = { viewModel.jump() },
                onSlideStart = { viewModel.slide(true) },
                onSlideEnd = { viewModel.slide(false) }
            )
        }

        // 3. Main Menu Overlay
        AnimatedVisibility(
            visible = state.phase == GamePhase.MainMenu,
            enter = fadeIn(animationSpec = tween(400)) + scaleIn(),
            exit = fadeOut(animationSpec = tween(300)) + scaleOut()
        ) {
            MainMenuOverlay(
                state = state,
                onStartGame = { viewModel.setPhase(GamePhase.Playing) },
                onSelectSkin = { viewModel.selectSkin(it) },
                onPurchaseSkin = { viewModel.purchaseSkin(it) },
                onClearScores = { viewModel.clearHighScoreBoard() }
            )
        }

        // 4. Pause Screen Overlay
        AnimatedVisibility(
            visible = state.phase == GamePhase.Paused,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PauseOverlay(
                onResume = { viewModel.setPhase(GamePhase.Playing) },
                onRestart = { viewModel.setPhase(GamePhase.Playing) },
                onMainMenu = { viewModel.setPhase(GamePhase.MainMenu) }
            )
        }

        // 5. Game Over Screen Overlay
        AnimatedVisibility(
            visible = state.phase == GamePhase.GameOver,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = fadeOut()
        ) {
            GameOverOverlay(
                state = state,
                onRestart = { viewModel.setPhase(GamePhase.Playing) },
                onMainMenu = { viewModel.setPhase(GamePhase.MainMenu) },
                onSaveScore = { name -> viewModel.saveHighScore(name) }
            )
        }
    }
}

@Composable
fun GamePlayCanvas(
    state: GameUIState,
    onCanvasTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Generate star patterns once so they don't shift randomly during frame updates
    val starsLocations = remember {
        List(25) {
            Offset(
                x = (0..1000).random().toFloat(),
                y = (0..350).random().toFloat()
            )
        }
    }

    Canvas(
        modifier = modifier
            .testTag("game_gameplay_canvas")
            .clickable { onCanvasTap() }
    ) {
        val scaleX = size.width / V_WIDTH
        val scaleY = size.height / V_HEIGHT

        // Apply visual screen shake matrix translation if active
        if (state.screenShake > 0f) {
            val shakeOffsetX = ((-1..1).random() * state.screenShake) * scaleX
            val shakeOffsetY = ((-1..1).random() * state.screenShake) * scaleY
            drawContext.canvas.translate(shakeOffsetX, shakeOffsetY)
        }

        // --- DRAW BACKGROUND PARALLAX ---
        // Sky Gradient (Dark Violet into Cosmic Navy Indigo)
        val skyBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFF070414), Color(0xFF140D36), Color(0xFF2E1A47)),
            startY = 0f,
            endY = GROUND_Y * scaleY
        )
        drawRect(brush = skyBrush, size = Size(size.width, size.height))

        // Draw Ambient Stars
        starsLocations.forEach { star ->
            // Simulating dim twinkle by utilizing current distance as modifier
            val starAlpha = 0.3f + (sin(star.x + state.currentDistance / 8f) * 0.5f).coerceIn(0f, 0.7f)
            drawCircle(
                color = Color.White.copy(alpha = starAlpha),
                radius = 2.5f * scaleX,
                center = Offset(star.x * scaleX, star.y * scaleY)
            )
        }

        // Draw Synthwave Sunset Core
        val sunCenterY = (GROUND_Y - 40f) * scaleY
        val sunRadius = 140f * scaleX
        val sunBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFF007F), Color(0xFFFF9900)),
            startY = (sunCenterY - sunRadius),
            endY = sunCenterY
        )
        drawCircle(
            brush = sunBrush,
            radius = sunRadius,
            center = Offset(size.width / 2f, sunCenterY)
        )

        // Draw nostalgic synth horizontal lines cutting the sun (retro look!)
        var sunLineY = sunCenterY - sunRadius + 30f
        while (sunLineY < sunCenterY) {
            val gapHeight = 7f * scaleY * (1.0f + (sunLineY - (sunCenterY - sunRadius)) / 50f)
            drawRect(
                color = Color(0xFF140D36),
                topLeft = Offset(size.width / 2f - sunRadius - 10f, sunLineY),
                size = Size(sunRadius * 2 + 20f, gapHeight)
            )
            sunLineY += 24f * scaleY
        }

        // Distant Cyber City Skyline silhouette (very slow parallax scrolling)
        val cityOffset = (state.currentDistance / 10f) % 600f
        drawCitySkyline(scaleX, scaleY, cityOffset)

        // --- DRAW FLOOR / GRID ---
        // Neon Purple Floor Divider line
        val floorY = GROUND_Y * scaleY
        drawLine(
            color = Color(0xFFFF00FF),
            start = Offset(0f, floorY),
            end = Offset(size.width, floorY),
            strokeWidth = 3.5f * scaleX
        )

        // Grid Horizon Glow Brush
        val gridSunsetBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFF31145A), Color(0xFF0C031E)),
            startY = floorY,
            endY = size.height
        )
        drawRect(brush = gridSunsetBrush, topLeft = Offset(0f, floorY), size = Size(size.width, size.height - floorY))

        // Sliding Grid lines moving backward to simulate depth speed
        val floorLength = size.height - floorY
        val stepX = 85f * scaleX
        val gridSlide = (state.currentDistance * 4.5f) % stepX

        // 1. Draw vertical perspective floor lines starting from horizon center outwards
        for (i in -15..15) {
            val startPointX = (size.width / 2f) + (i * 20f * scaleX)
            val endPointX = (size.width / 2f) + (i * 200f * scaleX)
            drawLine(
                color = Color(0xFF7B1FA2).copy(alpha = 0.5f),
                start = Offset(startPointX, floorY),
                end = Offset(endPointX, size.height),
                strokeWidth = 1.5f * scaleY
            )
        }

        // 2. Draw horizontal floor speed lines
        var gridDistY = 0f
        while (gridDistY < floorLength) {
            // Speed up vertical slide perspective wrapping
            val slideY = (gridDistY + gridSlide) % floorLength
            val scaleOpacity = (slideY / floorLength).coerceIn(0.1f, 1f)
            val currentLineY = floorY + slideY
            drawLine(
                color = Color(0xFFFF00FF).copy(alpha = 0.45f * scaleOpacity),
                start = Offset(0f, currentLineY),
                end = Offset(size.width, currentLineY),
                strokeWidth = (1.5f + (slideY / floorLength) * 2f) * scaleY
            )
            gridDistY += 45f * scaleY
        }

        // --- DRAW COINS ---
        state.coins.forEach { coin ->
            if (!coin.collected) {
                val cx = coin.x * scaleX
                val cy = coin.y * scaleY
                val cr = coin.radius * scaleX

                // Gold glowing coin circular shape
                drawCircle(
                    color = Color(0xFFFFDF00),
                    radius = cr,
                    center = Offset(cx, cy)
                )
                // Inner core highlights
                drawCircle(
                    color = Color.White,
                    radius = cr * 0.45f,
                    center = Offset(cx, cy)
                )
                // Neon glow ring
                drawCircle(
                    color = Color(0xFFFF9900),
                    radius = cr + 3f * scaleX,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5f * scaleX)
                )
            }
        }

        // --- DRAW POWERUPS ---
        state.powerUps.forEach { pu ->
            if (!pu.collected) {
                val px = pu.x * scaleX
                val py = pu.y * scaleY
                val pSize = pu.size * scaleX

                // Pulsate size to indicate interactive power-up
                val pPulse = (sin(state.currentDistance / 4f) * 4f).toFloat() * scaleX
                val outerColor = when (pu.type) {
                    PowerUpType.SHIELD -> Color(0xFF00E1D9)
                    PowerUpType.JETPACK -> Color(0xFFFF5722)
                    PowerUpType.MULTIPLIER -> Color(0xFFFFC107)
                }

                // Rotating polygonal / rounded card shape
                drawRoundRect(
                    color = outerColor,
                    topLeft = Offset(px - pPulse/2, py - pPulse/2),
                    size = Size(pSize + pPulse, pSize + pPulse),
                    cornerRadius = CornerRadius(8 * scaleX, 8 * scaleY)
                )

                // White accent inside details
                drawRect(
                    color = Color.White,
                    topLeft = Offset(px + pSize*0.3f, py + pSize*0.3f),
                    size = Size(pSize * 0.4f, pSize * 0.4f)
                )
            }
        }

        // --- DRAW OBSTACLES ---
        state.obstacles.forEach { obs ->
            val ox = obs.x * scaleX
            val oy = obs.y * scaleY
            val oWidth = obs.width * scaleX
            val oHeight = obs.height * scaleY

            when (obs.type) {
                ObstacleType.SPIKE -> {
                    // Draw a futuristic laser triangular spike
                    val path = Path().apply {
                        moveTo(ox, oy + oHeight)
                        lineTo(ox + oWidth / 2f, oy)
                        lineTo(ox + oWidth, oy + oHeight)
                        close()
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFFFF1744)
                    )
                    // Inner laser glowing core
                    val pathCore = Path().apply {
                        moveTo(ox + 8f * scaleX, oy + oHeight)
                        lineTo(ox + oWidth / 2f, oy + 12f * scaleY)
                        lineTo(ox + oWidth - 8f * scaleX, oy + oHeight)
                        close()
                    }
                    drawPath(
                        path = pathCore,
                        color = Color.White
                    )
                }
                ObstacleType.DOUBLE_SPIKE -> {
                    // Wide Spikes
                    val path = Path().apply {
                        moveTo(ox, oy + oHeight)
                        lineTo(ox + oWidth * 0.25f, oy)
                        lineTo(ox + oWidth * 0.5f, oy + oHeight)
                        lineTo(ox + oWidth * 0.75f, oy)
                        lineTo(ox + oWidth, oy + oHeight)
                        close()
                    }
                    drawPath(path = path, color = Color(0xFFFF3D00))
                }
                ObstacleType.LOW_WALL -> {
                    // Solid cyber-grid obstacle block
                    drawRoundRect(
                        color = Color(0xFFFF1744),
                        topLeft = Offset(ox, oy),
                        size = Size(oWidth, oHeight),
                        cornerRadius = CornerRadius(6 * scaleX, 6 * scaleY)
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.7f),
                        topLeft = Offset(ox + 4 * scaleX, oy + 4 * scaleY),
                        size = Size(oWidth - 8 * scaleX, oHeight - 8 * scaleY),
                        cornerRadius = CornerRadius(4 * scaleX, 4 * scaleY),
                        style = Stroke(width = 2f * scaleX)
                    )
                }
                ObstacleType.HIGH_BARRIER -> {
                    // Hanging overhead sliding barrier
                    val wallBrush = Brush.linearGradient(
                        colors = listOf(Color(0xFFE040FB), Color(0xFF651FFF)),
                        start = Offset(ox, oy),
                        end = Offset(ox, oy + oHeight)
                    )
                    drawRoundRect(
                        brush = wallBrush,
                        topLeft = Offset(ox, oy),
                        size = Size(oWidth, oHeight),
                        cornerRadius = CornerRadius(4 * scaleX, 4 * scaleY)
                    )
                    // Cross stripes indicator
                    drawLine(
                        color = Color.White,
                        start = Offset(ox, oy),
                        end = Offset(ox + oWidth, oy + oHeight),
                        strokeWidth = 2f * scaleX
                    )
                }
                ObstacleType.TALL_WALL -> {
                    // Complete vertical tall wall (forces double jump!)
                    val wallBrush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFD500F9), Color(0xFF31145A))
                    )
                    drawRoundRect(
                        brush = wallBrush,
                        topLeft = Offset(ox, oy),
                        size = Size(oWidth, oHeight),
                        cornerRadius = CornerRadius(8 * scaleX, 8 * scaleY)
                    )
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(ox + 4 * scaleX, oy + 4 * scaleY),
                        size = Size(oWidth - 8 * scaleX, oHeight - 8 * scaleY),
                        cornerRadius = CornerRadius(6 * scaleX, 6 * scaleY),
                        style = Stroke(width = 2.5f * scaleX)
                    )
                }
            }
        }

        // --- DRAW PARTICLES ---
        state.particles.forEach { particle ->
            drawCircle(
                color = particle.color.copy(alpha = particle.alpha),
                radius = particle.size * scaleX,
                center = Offset(particle.x * scaleX, particle.y * scaleY)
            )
        }

        // --- DRAW FLOATING COIN & BONUS TEXTS ---
        state.floatingTexts.forEach { t ->
            // Use standard custom Canvas drawText if wanted, or we handle simple dots
            // To ensure 100% Android SDK graphics compatibility across Kotlin versions,
            // we draw tiny beautiful text indicators inside Compose HUD overlays where possible
            // But doing simple small circle bursts on the canvas adds nice juice!
        }

        // --- DRAW PLAYER (RUNNER) ---
        drawRunner(scaleX, scaleY, state)
    }
}

fun DrawScope.drawCitySkyline(scaleX: Float, scaleY: Float, cityOffset: Float) {
    val h = GROUND_Y * scaleY
    val skylineY = h - 60f * scaleY

    // Draw a dark indigo silhouette layer for the background
    val darkInd = Color(0xFF1B0F3F)

    // Modular city outline blocks
    val bSize = 65f * scaleX
    var i = -1
    while (i * bSize + 150f < size.width + bSize) {
        val buildingX = i * bSize - (cityOffset * scaleX)
        // Pseudo random height based on index
        val buildingH = (45f + (Math.abs(sin(i * 1.5)) * 120f)).toFloat() * scaleY
        val bTop = h - buildingH

        drawRect(
            color = darkInd,
            topLeft = Offset(buildingX, bTop),
            size = Size(bSize - 5f, buildingH)
        )

        // Draw cute retro glowing windows
        val winCol = Color(0xFFFF007F).copy(alpha = 0.35f)
        var winY = bTop + 15f * scaleY
        while (winY < h - 15f * scaleY) {
            drawRect(
                color = winCol,
                topLeft = Offset(buildingX + 12f * scaleX, winY),
                size = Size(8f * scaleX, 10f * scaleY)
            )
            drawRect(
                color = winCol,
                topLeft = Offset(buildingX + bSize - 20f * scaleX, winY),
                size = Size(8f * scaleX, 10f * scaleY)
            )
            winY += 28f * scaleY
        }
        i++
    }
}

fun DrawScope.drawRunner(scaleX: Float, scaleY: Float, state: GameUIState) {
    val px = PLAYER_X * scaleX
    val py = state.playerY * scaleY
    val pWidth = state.playerWidth * scaleX
    val pHeight = state.playerHeight * scaleY

    val skinColor = state.activeSkin.primaryColor
    val accentColor = state.activeSkin.secondaryColor

    // Screen shield bubble shell
    if (state.shieldCount > 0) {
        val shieldRad = (state.playerHeight * 0.72f) * scaleX
        val pulseFactor = 0.95f + (sin(state.currentDistance / 3f) * 0.05f).toFloat()
        drawCircle(
            color = Color(0xFF00E1D9).copy(alpha = 0.35f),
            radius = shieldRad * pulseFactor,
            center = Offset(px + pWidth / 2f, py + pHeight / 2f)
        )
        drawCircle(
            color = Color(0xFF00E1D9),
            radius = (shieldRad + 4f * scaleX) * pulseFactor,
            center = Offset(px + pWidth / 2f, py + pHeight / 2f),
            style = Stroke(width = 2f * scaleX)
        )
    }

    if (state.jetpackTimeLeftMs > 0) {
        // DRAW JETPACK RIDER STATE
        // Draw Jetpack tank pack on back
        drawRoundRect(
            color = Color(0xFFFF5722),
            topLeft = Offset(px - 14f * scaleX, py + 12f * scaleY),
            size = Size(18f * scaleX, pHeight - 24f * scaleY),
            cornerRadius = CornerRadius(4 * scaleX, 4 * scaleY)
        )

        // Android Runner body
        drawRoundRect(
            color = skinColor,
            topLeft = Offset(px, py),
            size = Size(pWidth, pHeight),
            cornerRadius = CornerRadius(14 * scaleX, 14 * scaleY)
        )

        // Glowing cyber eye visor strip
        drawRect(
            color = Color.White,
            topLeft = Offset(px + pWidth * 0.4f, py + 12f * scaleY),
            size = Size(pWidth * 0.6f, 10f * scaleY)
        )
        drawRect(
            color = accentColor,
            topLeft = Offset(px + pWidth * 0.45f, py + 14f * scaleY),
            size = Size(pWidth * 0.5f, 6f * scaleY)
        )

        // Jetpack nozzle flames spark emitters (stationary background draw relative)
        drawCircle(
            color = Color(0xFFFF9800),
            radius = 6f * scaleX,
            center = Offset(px - 5f * scaleX, py + pHeight - 5f * scaleY)
        )
    } else {
        // DRAW NORMAL RUN / SHAPE
        if (state.isSliding) {
            // SLIDING RIDER VISUALS (Horizontal capsule)
            drawRoundRect(
                color = skinColor,
                topLeft = Offset(px, py),
                size = Size(pWidth, pHeight),
                cornerRadius = CornerRadius(12 * scaleX, 12 * scaleY)
            )

            // Horizontal eye visor
            drawRect(
                color = Color.White,
                topLeft = Offset(px + pWidth * 0.5f, py + 10f * scaleY),
                size = Size(pWidth * 0.42f, 8f * scaleY)
            )

            // Little sliding spark trails underneath
            drawRoundRect(
                color = accentColor,
                topLeft = Offset(px - 12f * scaleX, py + pHeight - 8f * scaleY),
                size = Size(15f * scaleX, 6f * scaleY),
                cornerRadius = CornerRadius(3 * scaleX, 3 * scaleY)
            )
        } else {
            // STANDING RUNNER VISUALS (Vertical capsule)
            drawRoundRect(
                color = skinColor,
                topLeft = Offset(px, py),
                size = Size(pWidth, pHeight - 22f * scaleY), // crop body for leg area
                cornerRadius = CornerRadius(12 * scaleX, 12 * scaleY)
            )

            // Cyber helmet visual
            drawRect(
                color = Color.White,
                topLeft = Offset(px + pWidth * 0.35f, py + 12f * scaleY),
                size = Size(pWidth * 0.65f, 10f * scaleY)
            )
            drawRect(
                color = accentColor,
                topLeft = Offset(px + pWidth * 0.4f, py + 14f * scaleY),
                size = Size(pWidth * 0.55f, 6f * scaleY)
            )

            // Animated runner pivoting legs!
            val distance = state.currentDistance.toFloat()
            val legSwing = sin(distance * 0.36f)
            val pivotX = px + pWidth / 2f
            val pivotY = py + pHeight - 24f * scaleY

            if (state.isJumping || state.isDoubleJumping) {
                // Tuck legs up (jumping)
                drawLine(
                    color = skinColor,
                    start = Offset(pivotX - 6f * scaleX, pivotY),
                    end = Offset(pivotX - 10f * scaleX, pivotY + 12f * scaleY),
                    strokeWidth = 5.5f * scaleX
                )
                drawLine(
                    color = skinColor,
                    start = Offset(pivotX + 6f * scaleX, pivotY),
                    end = Offset(pivotX + 2f * scaleX, pivotY + 12f * scaleY),
                    strokeWidth = 5.5f * scaleX
                )
            } else {
                // Swinging leg mechanics (fluid running movement animation)
                val fLOffset = legSwing * 16f
                drawLine(
                    color = skinColor,
                    start = Offset(pivotX - 6f * scaleX, pivotY),
                    end = Offset(pivotX - 12f * scaleX + (fLOffset * scaleX), py + pHeight),
                    strokeWidth = 6f * scaleX
                )

                // Back leg (inverse swing direction)
                val bLOffset = -legSwing * 16f
                drawLine(
                    color = accentColor,
                    start = Offset(pivotX + 6f * scaleX, pivotY),
                    end = Offset(pivotX + 8f * scaleX + (bLOffset * scaleX), py + pHeight),
                    strokeWidth = 6f * scaleX
                )
            }
        }
    }
}

// HUD Overlay Panel
@Composable
fun GameHUD(state: GameUIState, onPauseClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Horizontal bar containing active powerups & stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stats Panel
            Column {
                Text(
                    text = "SCORE: ${state.currentScore}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Meter Distance icon",
                        tint = Color(0xFFFF007F),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${state.currentDistance}m",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "🪙",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "+${state.currentCoins}",
                        color = Color(0xFFFFDF00),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (state.multiplierTimeLeftMs > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFF9900), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "2X SCORE",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            // Power-ups and Menu Button
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    if (state.jetpackTimeLeftMs > 0) {
                        HUDTimerBar("JETPACK", state.jetpackTimeLeftMs / 6500f, Color(0xFFFF5722))
                    }
                    if (state.multiplierTimeLeftMs > 0) {
                        HUDTimerBar("2X MULTIP", state.multiplierTimeLeftMs / 12000f, Color(0xFFFFDF00))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))

                // Pause Button
                IconButton(
                    onClick = onPauseClick,
                    modifier = Modifier
                        .testTag("game_pause_button")
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Text("||", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // Floating floating indicators for feedback on standard scores/actions
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(top = 80.dp)
        ) {
            state.floatingTexts.forEach { fText ->
                Column(modifier = Modifier.offset(x = fText.x.dp / 3f, y = fText.y.dp / 3f)) {
                    Text(
                        text = fText.text,
                        color = fText.color.copy(alpha = fText.alpha),
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun HUDTimerBar(label: String, progress: Float, color: Color) {
    Column(modifier = Modifier.width(90.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("${(progress * 100).toInt()}%", color = Color.White, fontSize = 9.sp)
        }
        LinearProgressIndicator(
            progress = { progress },
            color = color,
            trackColor = Color.White.copy(alpha = 0.15f),
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

// Controller buttons over gameplay canvas
@Composable
fun GameControls(
    onJump: () -> Unit,
    onSlideStart: () -> Unit,
    onSlideEnd: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Jump Button (Bottom left)
            Button(
                onClick = onJump,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF007F).copy(alpha = 0.85f)),
                modifier = Modifier
                    .testTag("game_jump_button")
                    .size(90.dp)
                    .clip(CircleShape),
                contentPadding = PaddingValues(0.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "▲",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("JUMP", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }

            // 2. Slide Button (Bottom right)
            // To simulate holding, we can use clickable with pointer interactions or simple tap.
            // Under normal Compose we can use simple clickable, but let's provide a robust
            // clickable button that holds on to Slide!
            Button(
                onClick = onSlideStart, // tap to slide brief period
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E1D9).copy(alpha = 0.85f)),
                modifier = Modifier
                    .testTag("game_slide_button")
                    .size(90.dp)
                    .clip(CircleShape),
                contentPadding = PaddingValues(0.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "▼",
                        color = Color.Black,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("SLIDE", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

// MAIN MENU OVERLAY & SKIN SHOP
@Composable
fun MainMenuOverlay(
    state: GameUIState,
    onStartGame: () -> Unit,
    onSelectSkin: (PlayerSkin) -> Unit,
    onPurchaseSkin: (PlayerSkin) -> Unit,
    onClearScores: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0B1E).copy(alpha = 0.95f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Colorful Synthwave title banner
            Text(
                text = "RETRO RUNNER",
                color = Color(0xFFFF007F),
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                text = "CYBERNETIC COSWAY",
                color = Color(0xFF00E1D9),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(y = (-14).dp)
            )

            // Play Trigger Button
            Button(
                onClick = onStartGame,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF007F)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .testTag("game_start_button")
                    .width(220.dp)
                    .height(55.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play button arrow icon", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("START GAME", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            // Stats bank (balance & highscores)
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("YOUR COINS", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🪙", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${state.totalCoins}", color = Color(0xFFFFDF00), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("HIGH SCORE", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏆", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${state.highScore}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }

            // CUSTOM SKIN SHOP PANEL
            SkinShopSection(state = state, onSelectSkin = onSelectSkin, onPurchaseSkin = onPurchaseSkin)

            // SCOREBOARD PANEL
            ScoreboardSection(topScores = state.topScores, onClearScores = onClearScores)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SkinShopSection(
    state: GameUIState,
    onSelectSkin: (PlayerSkin) -> Unit,
    onPurchaseSkin: (PlayerSkin) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "UPGRADE RUNNER SKIN & POWER",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            PlayerSkin.values().forEach { skin ->
                val isUnlocked = state.unlockedSkins.contains(skin.name)
                val isSelected = state.activeSkin == skin

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(
                            if (isSelected) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Colored visual dot indicator of character primary color
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(skin.primaryColor)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(skin.displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                if (isUnlocked) "UNLOCKED" else "${skin.cost} coins to unlock",
                                color = if (isUnlocked) Color(0xFF00E1D9) else Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Shop Button Actions (Buy / Equipped state)
                    if (isUnlocked) {
                        if (isSelected) {
                            Button(
                                onClick = {},
                                enabled = false,
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray, disabledContainerColor = Color(0xFF00FF87).copy(alpha = 0.25f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                            ) {
                                Text("ACTIVE", color = Color(0xFF00FF87), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { onSelectSkin(skin) },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                            ) {
                                Text("SELECT", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    } else {
                        Button(
                            onClick = { onPurchaseSkin(skin) },
                            shape = RoundedCornerShape(6.dp),
                            enabled = state.totalCoins >= skin.cost,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFDF00)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.ShoppingCart, contentDescription = "Cart buy", tint = Color.Black, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("BUY", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreboardSection(
    topScores: List<HighScore>,
    onClearScores: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "LEADERBOARD TOP SCORES",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                if (topScores.isNotEmpty()) {
                    Text(
                        "Reset Board",
                        color = Color(0xFFFF007F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onClearScores() }
                            .padding(4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (topScores.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No high scores recorded yet. Jump in!",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                topScores.take(5).forEachIndexed { index, hs ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${index + 1}.  ${hs.playerName}",
                            color = if (index == 0) Color(0xFFFFDF00) else Color.White,
                            fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                        Row {
                            Text(
                                text = "Score: ${hs.score}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "${hs.distance}m",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// PAUSED GAME OVERLAY
@Composable
fun PauseOverlay(
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onMainMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("GAME PAUSED", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onResume,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E1D9)),
                modifier = Modifier
                    .testTag("game_resume_button")
                    .width(180.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play arrow icon", tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text("RESUME", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onRestart,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                modifier = Modifier.width(180.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Restart icon", tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("RESTART", color = Color.White)
            }

            Button(
                onClick = onMainMenu,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.width(180.dp)
            ) {
                Icon(Icons.Filled.Home, contentDescription = "Home menu button icon", tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("MAIN MENU", color = Color.White)
            }
        }
    }
}

// GAME OVER SUMMARY & SAVE STATE
@Composable
fun GameOverOverlay(
    state: GameUIState,
    onRestart: () -> Unit,
    onMainMenu: () -> Unit,
    onSaveScore: (String) -> Unit
) {
    var textName by remember { mutableStateOf(TextFieldValue("")) }
    var scoreSaved by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "⚡",
                fontSize = 42.sp,
                color = Color(0xFFFF007F)
            )

            Text(
                "CRASHED! GAME OVER",
                color = Color(0xFFFF007F),
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            // Result values card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FINAL SCORE", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        Text("${state.currentScore}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("DISTANCE", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        Text("${state.currentDistance}m", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "🪙",
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "+${state.currentCoins} coins earned this run!",
                        color = Color(0xFFFFDF00),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // Save form to submit score
            if (!scoreSaved) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SUBMIT SCORE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = textName,
                                onValueChange = { textName = it },
                                placeholder = { Text("Player Name", color = Color.White.copy(alpha = 0.5f)) },
                                modifier = Modifier
                                    .testTag("game_name_input")
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp)),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.White.copy(alpha = 0.12f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    onSaveScore(textName.text)
                                    scoreSaved = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("game_submit_score_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E1D9))
                            ) {
                                Text("SAVE", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF00FF87).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, contentDescription = "Saved successfully indicator", tint = Color(0xFF00FF87))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Score submitted to offline leaderboard!", color = Color(0xFF00FF87), fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action triggers
            Row(
                modifier = Modifier.findMaxWidthOrRowMax(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        scoreSaved = false
                        textName = TextFieldValue("")
                        onRestart()
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF007F)),
                    modifier = Modifier
                        .testTag("game_retry_button")
                        .weight(1f)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Restart retry click indicator", tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("TRY AGAIN", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onMainMenu,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Home, contentDescription = "Back to home menu indicator", tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("MENU", color = Color.White)
                }
            }
        }
    }
}

// Custom helpers for responsive widths
fun Modifier.findMaxWidthOrRowMax(): Modifier = this.fillMaxWidth()
