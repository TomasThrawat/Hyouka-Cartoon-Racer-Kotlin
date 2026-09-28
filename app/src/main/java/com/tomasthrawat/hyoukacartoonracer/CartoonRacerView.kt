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

    private enum class State { MENU, HOW_TO, COUNTDOWN, RACING, FINISHED }

    private data class Racer(
        val name: String,
        val body: Int,
        val stripe: Int,
        var lane: Int,
        var progress: Float,
        var speed: Float,
        var wobble: Float = 0f
    )

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val path = Path()
    private val white = 0xFFFFFFFF.toInt()

    private var state = State.MENU
    private var last = SystemClock.uptimeMillis()
    private var time = 0f
    private var distance = 0f
    private var roadScroll = 0f
    private var playerLane = 1f
    private var targetLane = 1
    private var boost = 0f
    private var countdown = 3.1f
    private var leftHeld = false
    private var rightHeld = false
    private var boostHeld = false

    private val player = Racer("YOU", 0xFFFF5A5F.toInt(), white, 1, 0f, 220f)
    private val rivals = mutableListOf<Racer>()

    init {
        resetRace()
        state = State.MENU
        isFocusable = true
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = min(0.033f, max(0f, (now - last) / 1000f))
        last = now

        when (state) {
            State.COUNTDOWN -> {
                countdown -= dt
                if (countdown <= 0f) state = State.RACING
            }
            State.RACING -> update(dt)
            else -> Unit
        }

        when (state) {
            State.MENU -> drawMenu(canvas)
            State.HOW_TO -> drawHowTo(canvas)
            State.COUNTDOWN, State.RACING -> {
                drawRace(canvas)
                if (state == State.COUNTDOWN) drawCountdown(canvas)
            }
            State.FINISHED -> {
                drawRace(canvas)
                drawFinish(canvas)
            }
        }
        postInvalidateOnAnimation()
    }

    private fun resetRace() {
        last = SystemClock.uptimeMillis()
        time = 0f
        distance = 0f
        roadScroll = 0f
        playerLane = 1f
        targetLane = 1
        boost = 0f
        countdown = 3.1f
        clearInput()
        player.progress = 0f
        player.speed = 220f
        rivals.clear()
        rivals += Racer("MOMO", 0xFF4C9AFF.toInt(), 0xFFFFE66D.toInt(), 0, 85f, 198f)
        rivals += Racer("KUMA", 0xFF50C878.toInt(), white, 2, 120f, 204f)
        rivals += Racer("NOVA", 0xFF9B6DFF.toInt(), 0xFFFF8CC6.toInt(), 1, 155f, 201f)
    }

    private fun startRace() {
        resetRace()
        state = State.COUNTDOWN
        last = SystemClock.uptimeMillis()
    }

    private fun update(dt: Float) {
        time += dt

        if (leftHeld) targetLane = max(0, targetLane - 1)
        if (rightHeld) targetLane = min(2, targetLane + 1)

        boost = if (boostHeld) 0.18f else max(0f, boost - dt)
        val targetSpeed = 225f + if (boost > 0f) 150f else 0f
        player.speed += (targetSpeed - player.speed) * min(1f, dt * 5f)
        player.progress += player.speed * dt
        distance = player.progress

        playerLane += (targetLane - playerLane) * min(1f, dt * 12f)
        roadScroll = (roadScroll + player.speed * dt) % 700f

        rivals.forEachIndexed { i, r ->
            val rhythm = sin(time * (0.85f + i * 0.2f) + i * 1.7f)
            val catchUp = if (r.progress < player.progress - 120f) 1.08f else 0.995f
            r.speed = ((r.speed + rhythm * 5.5f) * catchUp).coerceIn(178f, 240f)
            r.progress += r.speed * dt
            r.wobble = sin(time * 1.55f + i) * 0.055f
        }

        handleCarContacts()

        if (player.progress >= 3000f) {
            player.speed = 0f
            state = State.FINISHED
            clearInput()
        }
    }

    private fun handleCarContacts() {
        rivals.forEach { r ->
            val progressGap = abs(r.progress - player.progress)
            val laneGap = abs(r.lane.toFloat() - playerLane)
            if (progressGap < 72f && laneGap < 0.28f) {
                player.speed = max(150f, player.speed - 90f)
                playerLane = (playerLane + if (r.lane < playerLane) 0.22f else -0.22f).coerceIn(0f, 2f)
                targetLane = playerLane.toInt().coerceIn(0, 2)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                when (state) {
                    State.MENU -> handleMenuTap(x, y)
                    State.HOW_TO -> handleHowToTap(x, y)
                    State.COUNTDOWN, State.RACING -> handleRaceDown(x, y)
                    State.FINISHED -> {
                        if (finishPlayAgainRect().contains(x, y)) startRace()
                        else if (finishMenuRect().contains(x, y)) state = State.MENU
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (state == State.RACING) updateHeldControls(x, y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                clearInput()
                return true
            }
        }
        return true
    }

    private fun handleMenuTap(x: Float, y: Float) {
        when {
            menuPlayRect().contains(x, y) -> startRace()
            menuHowRect().contains(x, y) -> state = State.HOW_TO
        }
    }

    private fun handleHowToTap(x: Float, y: Float) {
        if (howBackRect().contains(x, y)) state = State.MENU
    }

    private fun handleRaceDown(x: Float, y: Float) {
        val h = height.toFloat()
        val left = leftControlRect()
        val right = rightControlRect()
        val nitro = boostRect()
        when {
            left.contains(x, y) -> leftHeld = true
            right.contains(x, y) -> rightHeld = true
            nitro.contains(x, y) -> boostHeld = true
            else -> {
                if (x < width * 0.42f) targetLane = max(0, targetLane - 1)
                else if (x > width * 0.58f) targetLane = min(2, targetLane + 1)
                else if (y > h * 0.58f) boostHeld = true
            }
        }
    }

    private fun updateHeldControls(x: Float, y: Float) {
        leftHeld = leftControlRect().contains(x, y)
        rightHeld = rightControlRect().contains(x, y)
        boostHeld = boostRect().contains(x, y) || (y > height * 0.72f && x > width * 0.58f)
    }

    private fun clearInput() {
        leftHeld = false
        rightHeld = false
        boostHeld = false
    }

    private fun drawRace(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val horizon = h * 0.34f
        val roadTop = w * 0.24f
        val roadBottom = w * 1.03f
        val center = w * 0.5f

        p.color = 0xFF9DE2FF.toInt()
        c.drawRect(0f, 0f, w, h, p)
        drawSun(c, w * 0.82f, h * 0.14f, min(w, h) * 0.07f)
        drawCloud(c, w * 0.15f, h * 0.13f, 1f)
        drawCloud(c, w * 0.49f, h * 0.10f, 0.68f)
        drawHills(c, horizon)

        p.color = 0xFF66C66A.toInt()
        c.drawRect(0f, horizon, w, h, p)

        path.reset()
        path.moveTo(center - roadTop, horizon)
        path.lineTo(center + roadTop, horizon)
        path.lineTo(center + roadBottom, h)
        path.lineTo(center - roadBottom, h)
        path.close()
        p.color = 0xFF555C64.toInt()
        c.drawPath(path, p)

        drawEdge(c, center - roadTop, center - roadBottom, horizon, h, true)
        drawEdge(c, center + roadTop, center + roadBottom, horizon, h, false)
        drawDashes(c, center, roadTop, roadBottom, horizon, h)
        drawTrees(c, horizon)
        drawRivals(c, center, horizon)

        val playerX = center + (playerLane - 1f) * w * 0.18f
        drawCar(c, playerX, h * 0.80f, 1.42f, player.body, player.stripe, true, "YOU")

        drawHud(c)
        drawControls(c)
    }

    private fun drawRivals(c: Canvas, center: Float, horizon: Float) {
        rivals.sortedByDescending { it.progress }.forEach { r ->
            val rel = r.progress - player.progress
            if (rel in -360f..430f) {
                val t = ((rel + 360f) / 790f).coerceIn(0f, 1f)
                val y = horizon + height * 0.61f * (1f - t)
                val sc = 0.40f + 0.98f * (1f - t)
                val half = width * (0.25f + 0.82f * (1f - t))
                val x = center + (r.lane.toFloat() - 1f + r.wobble) * half * 0.63f
                drawCar(c, x, y, sc, r.body, r.stripe, false, r.name)
            }
        }
    }

    private fun drawCar(c: Canvas, x: Float, y: Float, scale: Float, body: Int, stripe: Int, label: Boolean, name: String) {
        val cw = 64f * scale
        val ch = 108f * scale
        p.color = 0x44000000
        c.drawOval(RectF(x - cw * 0.65f, y + ch * 0.34f, x + cw * 0.65f, y + ch * 0.57f), p)
        p.color = body
        c.drawRoundRect(RectF(x - cw / 2f, y - ch / 2f, x + cw / 2f, y + ch / 2f), 18f * scale, 18f * scale, p)
        p.color = 0xFF2B313A.toInt()
        c.drawRoundRect(RectF(x - cw * 0.34f, y - ch * 0.27f, x + cw * 0.34f, y + ch * 0.02f), 13f * scale, 13f * scale, p)
        p.color = stripe
        c.drawRoundRect(RectF(x - cw * 0.11f, y - ch * 0.49f, x + cw * 0.11f, y + ch * 0.49f), 8f * scale, 8f * scale, p)
        p.color = white
        c.drawCircle(x - cw * 0.24f, y - ch * 0.35f, 6.5f * scale, p)
        c.drawCircle(x + cw * 0.24f, y - ch * 0.35f, 6.5f * scale, p)
        p.color = 0xFFFFF1A8.toInt()
        c.drawCircle(x - cw * 0.28f, y + ch * 0.36f, 7.5f * scale, p)
        c.drawCircle(x + cw * 0.28f, y + ch * 0.36f, 7.5f * scale, p)
        if (label) {
            p.color = 0xFFFFE45E.toInt()
            p.textAlign = Paint.Align.CENTER
            p.textSize = 17f * scale
            p.typeface = android.graphics.Typeface.DEFAULT_BOLD
            c.drawText(name, x, y - ch * 0.64f, p)
        }
    }

    private fun drawHud(c: Canvas) {
        val w = width.toFloat()
        p.color = 0xD51A2436.toInt()
        c.drawRoundRect(RectF(22f, 20f, 360f, 112f), 24f, 24f, p)
        p.color = white
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.LEFT
        p.textSize = 22f
        val lap = min(3, (distance / 1000f).toInt() + 1)
        c.drawText("LAP " + lap + "/3", 44f, 54f, p)
        c.drawText("POS " + position() + "/4", 44f, 87f, p)
        p.textAlign = Paint.Align.RIGHT
        c.drawText(player.speed.toInt().toString() + " km/h", w - 30f, 52f, p)
        c.drawText(time.toInt().toString() + " s", w - 30f, 86f, p)
    }

    private fun drawControls(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        drawButton(c, leftControlRect(), "LEFT", leftHeld)
        drawButton(c, rightControlRect(), "RIGHT", rightHeld)
        drawButton(c, boostRect(), "BOOST", boostHeld)
        p.color = 0xCC182233.toInt()
        p.textAlign = Paint.Align.CENTER
        p.textSize = 14f
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        c.drawText("HOLD OR TAP", w * 0.5f, h - 18f, p)
    }

    private fun drawButton(c: Canvas, rect: RectF, label: String, pressed: Boolean) {
        p.color = if (pressed) 0xFFFFD95A.toInt() else 0xF0FFFFFF.toInt()
        c.drawRoundRect(rect, 22f, 22f, p)
        p.color = 0xFF182233.toInt()
        p.textAlign = Paint.Align.CENTER
        p.textSize = if (label == "BOOST") 19f else 15f
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        c.drawText(label, rect.centerX(), rect.centerY() - 2f, p)
        p.textSize = 11f
        p.typeface = android.graphics.Typeface.DEFAULT
        c.drawText(if (label == "BOOST") "NITRO" else "STEER", rect.centerX(), rect.centerY() + 16f, p)
    }

    private fun drawMenu(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        p.color = 0xFF7ED2FF.toInt()
        c.drawRect(0f, 0f, w, h, p)
        drawSun(c, w * 0.84f, h * 0.18f, min(w, h) * 0.08f)
        drawCloud(c, w * 0.18f, h * 0.18f, 0.95f)
        drawCloud(c, w * 0.55f, h * 0.12f, 0.62f)
        drawHills(c, h * 0.48f)

        p.color = 0xFF2E8B57.toInt()
        c.drawRect(0f, h * 0.52f, w, h, p)

        val roadY = h * 0.61f
        path.reset()
        path.moveTo(w * 0.40f, roadY)
        path.lineTo(w * 0.60f, roadY)
        path.lineTo(w * 0.86f, h)
        path.lineTo(w * 0.14f, h)
        path.close()
        p.color = 0xFF555C64.toInt()
        c.drawPath(path, p)

        drawCar(c, w * 0.22f, h * 0.70f, 0.85f, 0xFF4C9AFF.toInt(), 0xFFFFE66D.toInt(), false, "MOMO")
        drawCar(c, w * 0.78f, h * 0.73f, 0.92f, 0xFF9B6DFF.toInt(), 0xFFFF8CC6.toInt(), false, "NOVA")

        p.textAlign = Paint.Align.CENTER
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.color = white
        p.textSize = min(w, h) * 0.095f
        c.drawText("HYOUKA", w * 0.5f, h * 0.22f, p)
        p.color = 0xFFFFD95A.toInt()
        p.textSize = min(w, h) * 0.10f
        c.drawText("CARTOON RACER", w * 0.5f, h * 0.31f, p)

        menuButton(c, menuPlayRect(), "PLAY RACE", 0xFFFFD95A.toInt())
        menuButton(c, menuHowRect(), "HOW TO PLAY", white)

        p.color = 0xCC182233.toInt()
        p.textSize = 15f
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        c.drawText("3 LAPS  •  4 RACERS  •  NITRO", w * 0.5f, h * 0.93f, p)
    }


    private fun drawHowTo(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.color = 0xFF152038.toInt()
        c.drawRect(0f, 0f, w, h, p)
        p.textAlign = Paint.Align.CENTER
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.color = 0xFFFFD95A.toInt()
        p.textSize = min(w, h) * 0.09f
        c.drawText("HOW TO PLAY", w * 0.5f, h * 0.18f, p)

        val card = RectF(w * 0.14f, h * 0.26f, w * 0.86f, h * 0.75f)
        p.color = 0xFF24314A.toInt()
        c.drawRoundRect(card, 32f, 32f, p)
        p.color = white
        p.textAlign = Paint.Align.LEFT
        p.textSize = 23f
        c.drawText("1. Tap PLAY RACE.", card.left + 36f, card.top + 65f, p)
        c.drawText("2. Wait for GO.", card.left + 36f, card.top + 122f, p)
        c.drawText("3. Use LEFT and RIGHT to change lanes.", card.left + 36f, card.top + 179f, p)
        c.drawText("4. Hold BOOST to use nitro.", card.left + 36f, card.top + 236f, p)
        c.drawText("5. Finish 3 laps and beat the rivals.", card.left + 36f, card.top + 293f, p)
        menuButton(c, howBackRect(), "BACK", 0xFFFFD95A.toInt())
    }

    private fun drawCountdown(c: Canvas) {
        val label = when {
            countdown > 2.1f -> "3"
            countdown > 1.1f -> "2"
            countdown > 0.1f -> "1"
            else -> "GO!"
        }
        p.color = 0xAA132038.toInt()
        c.drawCircle(width * 0.5f, height * 0.35f, min(width, height) * 0.13f, p)
        p.color = 0xFFFFD95A.toInt()
        p.textAlign = Paint.Align.CENTER
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.textSize = min(width, height) * 0.12f
        c.drawText(label, width * 0.5f, height * 0.39f, p)
    }

    private fun drawFinish(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.color = 0xC9141B2A.toInt()
        c.drawRect(0f, 0f, w, h, p)
        p.textAlign = Paint.Align.CENTER
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.color = 0xFFFFD95A.toInt()
        p.textSize = min(w, h) * 0.12f
        c.drawText("FINISH!", w * 0.5f, h * 0.30f, p)
        p.color = white
        p.textSize = min(w, h) * 0.052f
        c.drawText("POSITION " + position() + "/4", w * 0.5f, h * 0.41f, p)
        p.textSize = min(w, h) * 0.043f
        c.drawText("TIME " + time.toInt() + " SECONDS", w * 0.5f, h * 0.49f, p)
        menuButton(c, finishPlayAgainRect(), "RACE AGAIN", 0xFFFFD95A.toInt())
        menuButton(c, finishMenuRect(), "MAIN MENU", white)
    }

    private fun drawSun(c: Canvas, x: Float, y: Float, r: Float) {
        p.color = 0xFFFFD95A.toInt()
        c.drawCircle(x, y, r, p)
        p.color = 0x66FFFFFF
        p.strokeWidth = 6f
        for (i in 0..7) {
            val a = i * 0.785f
            c.drawLine(x + cos(a) * r * 1.45f, y + sin(a) * r * 1.45f, x + cos(a) * r * 1.85f, y + sin(a) * r * 1.85f, p)
        }
    }

    private fun drawCloud(c: Canvas, x: Float, y: Float, scale: Float) {
        p.color = 0xEEFFFFFF.toInt()
        c.drawCircle(x, y, 23f * scale, p)
        c.drawCircle(x + 28f * scale, y - 8f * scale, 31f * scale, p)
        c.drawCircle(x + 60f * scale, y, 22f * scale, p)
        c.drawRoundRect(RectF(x - 20f * scale, y, x + 80f * scale, y + 28f * scale), 15f, 15f, p)
    }

    private fun drawHills(c: Canvas, base: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.color = 0xFF78C878.toInt()
        path.reset()
        path.moveTo(0f, base)
        path.quadTo(w * 0.12f, base - 100f, w * 0.27f, base)
        path.quadTo(w * 0.45f, base - 135f, w * 0.61f, base)
        path.quadTo(w * 0.8f, base - 90f, w, base)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        c.drawPath(path, p)
    }

    private fun drawEdge(c: Canvas, tx: Float, bx: Float, ty: Float, by: Float, left: Boolean) {
        path.reset()
        if (left) {
            path.moveTo(tx, ty)
            path.lineTo(tx + 8f, ty)
            path.lineTo(bx + 30f, by)
            path.lineTo(bx, by)
        } else {
            path.moveTo(tx - 8f, ty)
            path.lineTo(tx, ty)
            path.lineTo(bx, by)
            path.lineTo(bx - 30f, by)
        }
        path.close()
        p.color = white
        c.drawPath(path, p)
    }

    private fun drawDashes(c: Canvas, center: Float, top: Float, bottom: Float, horizon: Float, by: Float) {
        p.color = 0xFFDCE0E5.toInt()
        for (off in floatArrayOf(-0.33f, 0.33f)) {
            for (i in 0..12) {
                val t = ((i * 0.10f) + roadScroll / 720f) % 1f
                val y = horizon + (by - horizon) * t
                val half = top + (bottom - top) * t
                val x = center + half * off
                val dash = 6f + 19f * t
                c.drawRoundRect(RectF(x - dash, y, x + dash, y + 10f + 16f * t), 6f, 6f, p)
            }
        }
    }

    private fun drawTrees(c: Canvas, horizon: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 0..11) {
            val t = ((i * 0.14f + roadScroll / 550f) % 1f)
            val y = horizon + (h - horizon) * t
            val half = w * (0.25f + 0.82f * t)
            val side = if (i % 2 == 0) -1f else 1f
            val x = w * 0.5f + side * (half + 55f + (i % 3) * 24f)
            drawTree(c, x, y, 18f + 46f * t)
        }
    }

    private fun drawTree(c: Canvas, x: Float, y: Float, s: Float) {
        p.color = 0xFF8A5A3B.toInt()
        c.drawRoundRect(RectF(x - s * 0.15f, y - s * 0.05f, x + s * 0.15f, y + s * 0.8f), s * 0.1f, s * 0.1f, p)
        p.color = 0xFF2E9B59.toInt()
        c.drawCircle(x, y - s * 0.25f, s * 0.5f, p)
        p.color = 0xFF56C878.toInt()
        c.drawCircle(x - s * 0.25f, y - s * 0.4f, s * 0.34f, p)
        p.color = 0xFF3BBE68.toInt()
        c.drawCircle(x + s * 0.24f, y - s * 0.38f, s * 0.31f, p)
    }

    private fun leftControlRect(): RectF {
        val s = min(width.toFloat(), height.toFloat()) * 0.20f
        val b = height.toFloat() - 28f
        return RectF(28f, b - s, 28f + s, b)
    }

    private fun rightControlRect(): RectF {
        val s = min(width.toFloat(), height.toFloat()) * 0.20f
        val b = height.toFloat() - 28f
        return RectF(44f + s, b - s, 44f + s * 2f, b)
    }

    private fun boostRect(): RectF {
        val s = min(width.toFloat(), height.toFloat()) * 0.20f
        val b = height.toFloat() - 28f
        return RectF(width - 42f - s * 1.18f, b - s, width - 42f, b)
    }

    private fun menuPlayRect(): RectF {
        val w = width.toFloat()
        val h = height.toFloat()
        return RectF(w * 0.32f, h * 0.37f, w * 0.68f, h * 0.49f)
    }

    private fun menuHowRect(): RectF {
        val w = width.toFloat()
        val h = height.toFloat()
        return RectF(w * 0.34f, h * 0.51f, w * 0.66f, h * 0.61f)
    }

    private fun howBackRect(): RectF {
        val w = width.toFloat()
        val h = height.toFloat()
        return RectF(w * 0.39f, h * 0.80f, w * 0.61f, h * 0.91f)
    }

    private fun finishPlayAgainRect(): RectF {
        val w = width.toFloat()
        val h = height.toFloat()
        return RectF(w * 0.30f, h * 0.66f, w * 0.70f, h * 0.77f)
    }

    private fun finishMenuRect(): RectF {
        val w = width.toFloat()
        val h = height.toFloat()
        return RectF(w * 0.33f, h * 0.80f, w * 0.67f, h * 0.90f)
    }

    private fun menuButton(c: Canvas, rect: RectF, label: String, fill: Int) {
        p.color = 0x55000000
        c.drawRoundRect(RectF(rect.left + 6f, rect.top + 8f, rect.right + 6f, rect.bottom + 8f), 28f, 28f, p)
        p.color = fill
        c.drawRoundRect(rect, 28f, 28f, p)
        p.color = 0xFF182233.toInt()
        p.textAlign = Paint.Align.CENTER
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.textSize = 21f
        c.drawText(label, rect.centerX(), rect.centerY() + 7f, p)
    }

    private fun position(): Int = 1 + rivals.count { it.progress > player.progress }
}
