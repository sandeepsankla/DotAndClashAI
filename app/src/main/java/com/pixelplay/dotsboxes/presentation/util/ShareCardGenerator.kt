package com.pixelplay.dotsboxes.presentation.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import androidx.core.content.FileProvider
import com.pixelplay.dotsboxes.domain.model.GameState
import com.pixelplay.dotsboxes.domain.model.PlayerStats
import com.pixelplay.dotsboxes.domain.model.PlayerType
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

object ShareCardGenerator {

    /** Plain-text invite link for Hard-mode hint earning */
    fun shareAppInvite(context: Context) {
        val text = "🎮 Dot Clash AI — Can you beat the AI?\n" +
                   "Download now 👇\n" +
                   "https://play.google.com/store/apps/details?id=com.pixelplay.dotsboxes"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage("com.whatsapp")
        }
        val hasWhatsApp = context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
        if (hasWhatsApp) {
            context.startActivity(intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        } else {
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                }, "Invite a friend"
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(chooser)
        }
    }

    fun share(context: Context, gameState: GameState, stats: PlayerStats) {
        val bitmap = generateBitmap(gameState, stats)
        val uri = saveBitmap(context, bitmap)
        launchShare(context, uri)
    }

    private fun launchShare(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.whatsapp")
        }
        val hasWhatsApp = context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
        if (hasWhatsApp) {
            context.startActivity(intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        } else {
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share score card"
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(chooser)
        }
    }

    private fun generateBitmap(gameState: GameState, stats: PlayerStats): Bitmap {
        val W = 1080
        val H = 1080
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // Background gradient
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, W.toFloat(), H.toFloat(),
                intArrayOf(0xFF0D0D2B.toInt(), 0xFF1A0060.toInt(), 0xFF2B0070.toInt()),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
        })

        // Decorative rings of dots
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x18FFFFFF.toInt() }
        listOf(Triple(10, W * 0.20f, 12f), Triple(16, W * 0.36f, 8f), Triple(22, W * 0.50f, 5f))
            .forEach { (count, radius, r) ->
                repeat(count) { i ->
                    val angle = 2 * Math.PI / count * i
                    c.drawCircle(
                        W / 2f + radius * cos(angle).toFloat(),
                        H / 2f + radius * sin(angle).toFloat(),
                        r, dotPaint
                    )
                }
            }

        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x30FFFFFF.toInt(); strokeWidth = 2f
        }

        // App title
        c.drawText("DOT CLASH AI", W / 2f, 138f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(160f, 0f, 920f, 0f,
                intArrayOf(0xFFB39DFF.toInt(), 0xFF64B5F6.toInt()), null, Shader.TileMode.CLAMP)
            textSize = 72f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.10f
        })
        c.drawText("Strategy  ·  Speed  ·  Smarts", W / 2f, 182f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x70FFFFFF.toInt(); textSize = 28f; textAlign = Paint.Align.CENTER
        })
        c.drawLine(100f, 210f, (W - 100).toFloat(), 210f, divPaint)

        // Result emoji + text
        val winner = gameState.winner
        val isTie = winner == null
        val emoji = if (isTie) "🤝" else "🏆"
        val resultLabel = when {
            isTie -> "IT'S A TIE!"
            winner == PlayerType.ONE -> "${gameState.p1Name.take(12).uppercase()} WINS!"
            else -> "${gameState.p2Name.take(12).uppercase()} WINS!"
        }
        val resultColor = when {
            isTie -> 0xFFFFD54F.toInt()
            winner == PlayerType.ONE -> 0xFF64B5F6.toInt()
            else -> 0xFFFF8A65.toInt()
        }

        c.drawText(emoji, W / 2f, 370f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 128f; textAlign = Paint.Align.CENTER
        })
        c.drawText(resultLabel, W / 2f, 462f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = resultColor; textSize = 68f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })

        // Score card pill
        c.drawRoundRect(RectF(80f, 495f, (W - 80).toFloat(), 655f), 36f, 36f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF.toInt() })

        val bigNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 100f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        // P1 score
        bigNumPaint.color = 0xFF64B5F6.toInt()
        c.drawText("${gameState.p1Score}", W / 2f - 170f, 610f, bigNumPaint)
        // VS
        c.drawText("VS", W / 2f, 610f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x70FFFFFF.toInt(); textSize = 36f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        // P2 score
        bigNumPaint.color = 0xFFFF8A65.toInt()
        c.drawText("${gameState.p2Score}", W / 2f + 170f, 610f, bigNumPaint)

        // Player name labels
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f; textAlign = Paint.Align.CENTER
        }
        namePaint.color = 0xFF90CAF9.toInt()
        c.drawText(gameState.p1Name.take(14), W / 2f - 170f, 648f, namePaint)
        namePaint.color = 0xFFFFAB91.toInt()
        c.drawText(gameState.p2Name.take(14), W / 2f + 170f, 648f, namePaint)

        // Stats row
        c.drawLine(100f, 675f, (W - 100).toFloat(), 675f, divPaint)

        val statCols = listOf(
            "WINS" to "${stats.wins}",
            "LOSSES" to "${stats.losses}",
            "TIES" to "${stats.ties}",
            "STREAK" to "${stats.currentStreak}"
        )
        val colW = (W - 160f) / 4f
        val statNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt(); textSize = 64f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val statLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x80FFFFFF.toInt(); textSize = 26f; textAlign = Paint.Align.CENTER
        }
        statCols.forEachIndexed { i, (label, value) ->
            val x = 80f + colW * i + colW / 2f
            c.drawText(value, x, 770f, statNumPaint)
            c.drawText(label, x, 806f, statLabelPaint)
        }

        // Win rate badge
        c.drawLine(100f, 830f, (W - 100).toFloat(), 830f, divPaint)

        val winRateLabel = "Win Rate  ${stats.winRatePct}%   ·   Best Streak  ${stats.bestStreak}"
        c.drawText(winRateLabel, W / 2f, 878f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFB39DFF.toInt(); textSize = 32f; textAlign = Paint.Align.CENTER
        })

        // App store CTA
        c.drawText("Search \"Dot Clash AI\" on Google Play", W / 2f, 932f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF80CBC4.toInt(); textSize = 30f; textAlign = Paint.Align.CENTER
        })
        c.drawText("Challenge friends — can you beat the AI?", W / 2f, 972f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x50FFFFFF.toInt(); textSize = 26f; textAlign = Paint.Align.CENTER
        })

        return bmp
    }

    private fun saveBitmap(context: Context, bitmap: Bitmap): Uri {
        val file = File(context.cacheDir, "dotclash_share.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 95, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
