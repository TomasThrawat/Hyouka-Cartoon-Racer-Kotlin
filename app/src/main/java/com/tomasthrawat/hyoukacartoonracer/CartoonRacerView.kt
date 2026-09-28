package com.tomasthrawat.hyoukacartoonracer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class CartoonRacerView(context: Context) : View(context) {

    private enum class GameState { READY, RACING, FINISHED }

    private data class Racer(
        val name: String,
        val body: Int,
        val stripe: Int,
        var lane: Int,
        var progress: Float,
        var speed: Float,
        var wobble: Float = 0f
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val roadPath = Path()
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var state = GameState.READY
    private var lastFrameMs = SystemClock.uptimeMillis()
    private var elapsed = 0f
    private var distance = 0f
    private var roadScroll = 0f
    private var targetLane = 1
    private var playerLane = 1f
    private var boostTimer = 0f
    private var finishTimer = 0f

    private val player = Racer("YOU", 0xFFFF5A5F.toInt(), 0xFFFFFFFF.toInt(), 1, 0f, 0f)
    private val ai = mutableListOf<Racer>()

    init {
        isFocusable = true
        resetRace()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        val dt = min(0.033f, max(0f, (now - lastFrameMs) / 1000f))
        lastFrameMs = now

        if (state == GameState.RACING) update(dt)
        drawWorld(canvas)
        postInvalidateOnAnimation()
    }

    private fun resetRace() {
        state = GameState.READY
        elapsed = 0f
        distance = 0f
        roadScroll = 0f
        boostTimer = 0f
        finishTimer = 0f
        targetLane = 1
        playerLane = 1f
        player.progress = 0f
        player.speed = 210f
        ai.clear()
        ai += Racer("MOMO", 0xFF4C9AFF.toInt(), 0xFFFFE66D.toInt(), 0, 0.12f, 195f)
        ai += Racer("KUMA", 0xFF50C878.toInt(), 0xFFFFFFFF.toInt(), 2, 0.09f, 202f)
        ai += Racer("NOVA", 0xFF9B6DFF.toInt(), 0xFFFF8CC6.toInt(), 1, 0.08f, 198f)
    }

    private fun startRace() {
        if (state == GameState.READY) {
            state = GameState.RACING
            lastFrameMs = SystemClock.uptimeMillis()
        } else if (state == GameState.FINISHED) {
            resetRace()
            state = GameState.RACING
            lastFrameMs = SystemClock.uptimeMillis()
        }
    }

    private fun update(dt: Float) {
        elapsed += dt
        val boostActive = boostTimer > 0f
        if (boostTimer > 0f) boostTimer -= dt

        val cruise = 230f
        val desiredSpeed = cruise + if (boostActive) 125f else 0f
        player.speed += (desiredSpeed - player.speed) * min(1f, dt * 4.5f)
        player.progress += player.speed * dt

        playerLane += (targetLane - playerLane) * min(1f, dt * 10f)
        roadScroll = (roadScroll + player.speed * dt) % 500f
        distance = player.progress

        ai.forEachIndexed { index, racer ->
            val variation = sin(elapsed * (0.9f + index * 0.18f) + index) * 7f
            val catchUp = if (racer.progress < player.progress - 80f) 1.04f else 0.98f
            racer.speed = (racer.speed + variation) * catchUp
            racer.speed = racer.speed.coerceIn(175f, 235f)
            racer.progress += racer.speed * dt
            racer.wobble = sin(elapsed * 1.7f + index) * 0.08f
        }

        handleCollisions()

        if (player.progress >= 3000f) {
            finishTimer += dt
            if (finishTimer > 0.25f) state = GameState.FINISHED
        }
    }

    private fun handleCollisions() {
        ai.forEach { racer ->
            val progressGap = abs(racer.progress - player.progress)
            val laneGap = abs(racer.lane.toFloat() - playerLane)
            if (progressGap < 75f && laneGap < 0.28f) {
                player.speed = max(170f, player.speed - 70f)
                playerLane += if (racer.lane < playerLane) 0.12f else -0.12f
                playerLane = playerLane.coerceIn(0f, 2f)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (state != GameState.RACING) {
                    startRace()
                    return true
                }
                handleButtonDown(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    private fun handleButtonDown(x: Float, y: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bottom = h - 30f
        val size = min(w, h) * 0.17f
        val left = RectF(28f, bottom - size, 28f + size, bottom)
        val right = RectF(42f + size, bottom - size, 42f + size * 2f, bottom)
        val boost = RectF(w - 40f - size * 1.15f, bottom - size * 0.92f, w - 40f, bottom)

        when {
            left.contains(x, y) -> targetLane = max(0, targetLane - 1)
            right.contains(x, y) -> targetLane = min(2, targetLane + 1)
            boost.contains(x, y) -> boostTimer = 1.25f
        }
    }

    private fun drawWorld(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        paint.style = Paint.Style.FILL
        paint.color = 0xFF9DE2FF.toInt()
        canvas.drawRect(0f, 0f, w, h, paint)

        drawSun(canvas, w * 0.82f, h * 0.16f, min(w, h) * 0.08f)
        drawClouds(canvas)
        drawHills(canvas, h * 0.35f)

        val horizon = h * 0.33f
        val roadTop = w * 0.25f
        val roadBottom = w * 1.05f
        val center = w / 2f

        paint.color = 0xFF66C66A.toInt()
        canvas.drawRect(0f, horizon, w, h, paint)

        roadPath.reset()
        roadPath.moveTo(center - roadTop, horizon)
        roadPath.lineTo(center + roadTop, horizon)
        roadPath.lineTo(center + roadBottom, h)
        roadPath.lineTo(center - roadBottom, h)
        roadPath.close()
        paint.color = 0xFF545B63.toInt()
        canvas.drawPath(roadPath, paint)

        drawRoadEdge(canvas, center - roadTop, center - roadBottom, horizon, h)
        drawRoadEdge(canvas, center + roadTop, center + roadBottom, horizon, h)
        drawLaneDashes(canvas, center, roadTop, roadBottom, horizon, h)
        drawScenery(canvas, horizon)
        drawRivals(canvas, center, horizon)
        drawPlayer(canvas, center, h)

        drawHud(canvas)
        drawControls(canvas)

        if (state == GameState.READY) drawStartOverlay(canvas)
        if (state == GameState.FINISHED) drawFinishOverlay(canvas)
    }

    private fun drawRoadEdge(canvas: Canvas, topX: Float, bottomX: Float, topY: Float, bottomY: Float) {
        val thicknessTop = 8f
        val thicknessBottom = 30f
        roadPath.reset()
        if (bottomX < topX) {
            roadPath.moveTo(topX, topY)
            roadPath.lineTo(topX + thicknessTop, topY)
            roadPath.lineTo(bottomX + thicknessBottom, bottomY)
            roadPath.lineTo(bottomX, bottomY)
        } else {
            roadPath.moveTo(topX - thicknessTop, topY)
            roadPath.lineTo(topX, topY)
            roadPath.lineTo(bottomX, bottomY)
            roadPath.lineTo(bottomX - thicknessBottom, bottomY)
        }
        roadPath.close()
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawPath(roadPath, paint)
    }

    private fun drawLaneDashes(
        canvas: Canvas,
        center: Float,
        roadTop: Float,
        roadBottom: Float,
        horizon: Float,
        bottomY: Float
    ) {
        paint.color = 0xFFD8DDE2.toInt()
        val laneOffsets = listOf(-0.33f, 0.33f)
        for (offset in laneOffsets) {
            for (i in 0..11) {
                val t0 = ((i * 0.11f) + (roadScroll / 700f)) % 1f
                val t1 = min(1f, t0 + 0.045f)
                val y0 = horizon + (bottomY - horizon) * t0
                val y1 = horizon + (bottomY - horizon) * t1
                val half0 = roadTop + (roadBottom - roadTop) * t0
                val x0 = center + half0 * offset
                val width = 6f + 18f * t0
                canvas.drawRect(x0 - width, y0, x0 + width, y1, paint)
                if (t1 >= 1f) break
            }
        }
    }

    private fun drawScenery(canvas: Canvas, horizon: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 0..9) {
            val phase = ((i * 0.17f + roadScroll / 520f) % 1f)
            val y = horizon + (h - horizon) * phase
            val roadHalf = w * (0.25f + 0.8f * phase)
            val side = if (i % 2 == 0) -1f else 1f
            val x = w / 2f + side * (roadHalf + 55f + (i % 3) * 24f)
            drawTree(canvas, x, y, 18f + 45f * phase)
            if (i % 3 == 0) drawFlower(canvas, x + side * 28f, y + 14f, 8f + 10f * phase)
        }
    }

    private fun drawTree(canvas: Canvas, x: Float, y: Float, size: Float) {
        paint.color = 0xFF8A5A3B.toInt()
        canvas.drawRoundRect(RectF(x - size * 0.16f, y - size * 0.08f, x + size * 0.16f, y + size * 0.8f), size * 0.12f, size * 0.12f, paint)
        paint.color = 0xFF2E9B59.toInt()
        canvas.drawCircle(x, y - size * 0.18f, size * 0.48f, paint)
        paint.color = 0xFF56C878.toInt()
        canvas.drawCircle(x - size * 0.23f, y - size * 0.35f, size * 0.34f, paint)
        paint.color = 0xFF3BBE68.toInt()
        canvas.drawCircle(x + size * 0.25f, y - size * 0.35f, size * 0.32f, paint)
    }

    private fun drawFlower(canvas: Canvas, x: Float, y: Float, size: Float) {
        paint.color = 0xFF3E9F58.toInt()
        canvas.drawRect(x - 2f, y, x + 2f, y + size * 2.3f, paint)
        paint.color = 0xFFFF6FA9.toInt()
        for (a in 0..4) {
            val rad = a * 1.2566f
            canvas.drawCircle(x + cos(rad) * size * 0.7f, y + sin(rad) * size * 0.7f, size * 0.42f, paint)
        }
        paint.color = 0xFFFFDD57.toInt()
        canvas.drawCircle(x, y, size * 0.4f, paint)
    }

    private fun drawSun(canvas: Canvas, x: Float, y: Float, radius: Float) {
        paint.color = 0xFFFFD95A.toInt()
        canvas.drawCircle(x, y, radius, paint)
        paint.color = 0x66FFFFFF
        paint.strokeWidth = 7f
        for (i in 0..7) {
            val a = i * 0.785f
            val x1 = x + cos(a) * radius * 1.45f
            val y1 = y + sin(a) * radius * 1.45f
            val x2 = x + cos(a) * radius * 1.85f
            val y2 = y + sin(a) * radius * 1.85f
            canvas.drawLine(x1, y1, x2, y2, paint)
        }
    }

    private fun drawClouds(canvas: Canvas) {
        drawCloud(canvas, width * 0.16f, height * 0.13f, 1.0f)
        drawCloud(canvas, width * 0.47f, height * 0.11f, 0.72f)
    }

    private fun drawCloud(canvas: Canvas, x: Float, y: Float, scale: Float) {
        cloudPaint.color = 0xEEFFFFFF.toInt()
        canvas.drawCircle(x, y, 23f * scale, cloudPaint)
        canvas.drawCircle(x + 28f * scale, y - 8f * scale, 31f * scale, cloudPaint)
        canvas.drawCircle(x + 60f * scale, y, 22f * scale, cloudPaint)
        canvas.drawRoundRect(RectF(x - 20f * scale, y, x + 80f * scale, y + 28f * scale), 15f, 15f, cloudPaint)
    }

    private fun drawHills(canvas: Canvas, baseY: Float) {
        paint.color = 0xFF78C878.toInt()
        roadPath.reset()
        roadPath.moveTo(0f, baseY)
        roadPath.quadTo(width * 0.12f, baseY - 100f, width * 0.27f, baseY)
        roadPath.quadTo(width * 0.45f, baseY - 135f, width * 0.61f, baseY)
        roadPath.quadTo(width * 0.8f, baseY - 90f, width, baseY)
        roadPath.lineTo(width, height)
        roadPath.lineTo(0f, height)
        roadPath.close()
        canvas.drawPath(roadPath, paint)
    }

    private fun laneX(center: Float, relative: Float, lane: Float): Float {
        val t = (1f - ((relative + 420f) / 840f)).coerceIn(0f, 1f)
        val half = width * (0.25f + 0.8f * t)
        return center + (lane - 1f) * half * 0.63f
    }

    private fun drawRivals(canvas: Canvas, center: Float, horizon: Float) {
        ai.sortedByDescending { it.progress }.forEach { racer ->
            val relative = racer.progress - player.progress
            if (relative in -350f..420f) {
                val t = (relative + 350f) / 770f
                val y = horizon + height * 0.60f * (1f - t)
                val scale = 0.45f + 0.9f * (1f - t).coerceIn(0f, 1f)
                val x = laneX(center, relative, racer.lane.toFloat() + racer.wobble)
                drawCar(canvas, x, y, scale, racer.body, racer.stripe, false, racer.name)
            }
        }
    }

    private fun drawPlayer(canvas: Canvas, center: Float, h: Float) {
        val y = h * 0.80f
        val x = center + (playerLane - 1f) * width * 0.17f
        drawCar(canvas, x, y, 1.38f, player.body, player.stripe, true, "YOU")
    }

    private fun drawCar(
        canvas: Canvas,
        x: Float,
        y: Float,
        scale: Float,
        bodyColor: Int,
        stripeColor: Int,
        playerCar: Boolean,
        label: String
    ) {
        val carW = 62f * scale
        val carH = 104f * scale
        paint.color = 0x44000000
        canvas.drawOval(RectF(x - carW * 0.62f, y + carH * 0.35f, x + carW * 0.62f, y + carH * 0.55f), paint)

        paint.color = bodyColor
        canvas.drawRoundRect(RectF(x - carW / 2, y - carH / 2, x + carW / 2, y + carH / 2), 17f * scale, 17f * scale, paint)

        paint.color = 0xFF2A3038.toInt()
        canvas.drawRoundRect(
            RectF(x - carW * 0.33f, y - carH * 0.28f, x + carW * 0.33f, y + carH * 0.03f),
            12f * scale, 12f * scale, paint
        )

        paint.color = stripeColor
        canvas.drawRoundRect(
            RectF(x - carW * 0.10f, y - carH * 0.49f, x + carW * 0.10f, y + carH * 0.49f),
            8f * scale, 8f * scale, paint
        )

        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(x - carW * 0.23f, y - carH * 0.34f, 6f * scale, paint)
        canvas.drawCircle(x + carW * 0.23f, y - carH * 0.34f, 6f * scale, paint)

        paint.color = 0xFFFFF1A8.toInt()
        canvas.drawCircle(x - carW * 0.27f, y + carH * 0.36f, 7f * scale, paint)
        canvas.drawCircle(x + carW * 0.27f, y + carH * 0.36f, 7f * scale, paint)

        if (playerCar) {
            paint.color = 0xFFFFE45E.toInt()
            paint.textSize = 16f * scale
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            canvas.drawText(label, x, y - carH * 0.64f, paint)
        }
    }

    private fun drawHud(canvas: Canvas) {
        val w = width.toFloat()
        paint.color = 0xCC172033.toInt()
        canvas.drawRoundRect(RectF(22f, 20f, 310f, 104f), 24f, 24f, paint)

        paint.color = 0xFFFFFFFF.toInt()
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = 23f
        canvas.drawText("LAP " + min(3, (distance / 1000f).toInt() + 1) + "/3", 42f, 53f, paint)
        canvas.drawText("POS " + playerPosition() + "/4", 42f, 84f, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(player.speed.toInt().toString() + " km/h", w - 28f, 48f, paint)
        canvas.drawText(elapsed.toInt().toString() + " s", w - 28f, 80f, paint)
    }

    private fun drawControls(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val size = min(w, h) * 0.17f
        val bottom = h - 30f

        drawButton(canvas, RectF(28f, bottom - size, 28f + size, bottom), "◀")
        drawButton(canvas, RectF(42f + size, bottom - size, 42f + size * 2f, bottom), "▶")
        drawButton(canvas, RectF(w - 40f - size * 1.15f, bottom - size * 0.92f, w - 40f, bottom), "BOOST")
    }

    private fun drawButton(canvas: Canvas, rect: RectF, text: String) {
        paint.color = 0xE6FFFFFF.toInt()
        canvas.drawRoundRect(rect, 20f, 20f, paint)
        paint.color = 0xFF182233.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = if (text == "BOOST") 18f else 30f
        canvas.drawText(text, rect.centerX(), rect.centerY() + paint.textSize * 0.34f, paint)
    }

    private fun drawStartOverlay(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.color = 0xAA0F1725.toInt()
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.color = 0xFFFFFFFF.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = min(w, h) * 0.12f
        canvas.drawText("HYOUKA CARTOON RACER", w / 2f, h * 0.36f, paint)

        paint.color = 0xFFFFD95A.toInt()
        paint.textSize = min(w, h) * 0.055f
        canvas.drawText("Tap anywhere to race", w / 2f, h * 0.48f, paint)

        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = min(w, h) * 0.036f
        canvas.drawText("Steer with ◀ ▶   •   BOOST for speed", w / 2f, h * 0.56f, paint)
    }

    private fun drawFinishOverlay(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.color = 0xB3141B2A.toInt()
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.color = 0xFFFFD95A.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = min(w, h) * 0.13f
        canvas.drawText("FINISH!", w / 2f, h * 0.40f, paint)

        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = min(w, h) * 0.055f
        canvas.drawText("Position " + playerPosition() + "/4  •  " + elapsed.toInt() + " seconds", w / 2f, h * 0.51f, paint)
        paint.textSize = min(w, h) * 0.045f
        canvas.drawText("Tap to race again", w / 2f, h * 0.60f, paint)
    }

    private fun playerPosition(): Int = 1 + ai.count { it.progress > player.progress }
}
