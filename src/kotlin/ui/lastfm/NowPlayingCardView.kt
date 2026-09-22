package xie.fa.gram.ui.lastfm

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import org.telegram.messenger.browser.Browser
import xie.fa.gram.helpers.lastfm.LastFmApiClient
import xie.fa.gram.helpers.lastfm.LastFmPalette
import xie.fa.gram.helpers.lastfm.LastFmPaletteHelper
import xie.fa.gram.helpers.lastfm.LastFmTrack
import xie.fa.gram.helpers.theme.M3SectionsHelper

@SuppressLint("ViewConstructor")
class NowPlayingCardView(context: Context) : FrameLayout(context) {

    companion object {
        private const val POLL_INTERVAL_MS = 15_000L

        private const val SHAPE1_PATH_DATA =
            "M136.697 9.84752C137.237 9.31752 137.508 9.0475 137.738 8.8275C150.248 -2.9425 169.748 -2.9425 182.258 8.8275C182.488 9.0475 182.758 9.31752 183.298 9.84752C183.628 10.1575 183.787 10.3174 183.937 10.4674C191.947 18.1074 203.278 21.1375 214.028 18.5275C214.238 18.4775 214.458 18.4175 214.898 18.3075C215.628 18.1175 215.998 18.0274 216.308 17.9474C233.018 14.0074 249.918 23.7574 254.858 40.2074C254.948 40.5174 255.048 40.8775 255.258 41.6075C255.378 42.0475 255.438 42.2674 255.498 42.4774C258.608 53.0874 266.908 61.3874 277.518 64.4974C277.728 64.5574 277.947 64.6174 278.387 64.7374C279.117 64.9474 279.478 65.0473 279.788 65.1373C296.238 70.0773 305.988 86.9774 302.048 103.687C301.968 103.997 301.878 104.368 301.688 105.098C301.578 105.538 301.518 105.757 301.468 105.967C298.858 116.717 301.888 128.047 309.528 136.057C309.678 136.207 309.837 136.367 310.147 136.697C310.677 137.237 310.947 137.507 311.167 137.737C322.937 150.247 322.937 169.747 311.167 182.257C310.947 182.487 310.677 182.757 310.147 183.297C309.837 183.627 309.678 183.787 309.528 183.937C301.888 191.947 298.858 203.277 301.468 214.027C301.518 214.237 301.578 214.457 301.688 214.897C301.878 215.627 301.968 215.997 302.048 216.307C305.988 233.017 296.238 249.918 279.788 254.858C279.478 254.948 279.117 255.047 278.387 255.257C277.947 255.377 277.728 255.437 277.518 255.497C266.908 258.607 258.608 266.907 255.498 277.517C255.438 277.727 255.378 277.947 255.258 278.387C255.048 279.117 254.948 279.477 254.858 279.787C249.918 296.237 233.018 305.987 216.308 302.047C215.998 301.967 215.628 301.877 214.898 301.687C214.458 301.577 214.238 301.517 214.028 301.467C203.278 298.857 191.947 301.887 183.937 309.527C183.787 309.677 183.628 309.837 183.298 310.147C182.758 310.677 182.488 310.947 182.258 311.167C169.748 322.937 150.248 322.937 137.738 311.167C137.508 310.947 137.237 310.677 136.697 310.147C136.367 309.837 136.208 309.677 136.058 309.527C128.048 301.887 116.718 298.857 105.968 301.467C105.758 301.517 105.538 301.577 105.098 301.687C104.368 301.877 103.997 301.967 103.687 302.047C86.9775 305.987 70.0776 296.237 65.1376 279.787C65.0476 279.477 64.9475 279.117 64.7375 278.387C64.6175 277.947 64.5575 277.727 64.4975 277.517C61.3875 266.907 53.0875 258.607 42.4775 255.497C42.2675 255.437 42.0475 255.377 41.6075 255.257C40.8775 255.047 40.5175 254.948 40.2075 254.858C23.7575 249.918 14.0075 233.017 17.9475 216.307C18.0275 215.997 18.1176 215.627 18.3076 214.897C18.4176 214.457 18.4776 214.237 18.5276 214.027C21.1376 203.277 18.1075 191.947 10.4675 183.937C10.3175 183.787 10.1575 183.627 9.84752 183.297C9.31752 182.757 9.0475 182.487 8.8275 182.257C-2.9425 169.747 -2.9425 150.247 8.8275 137.737C9.0475 137.507 9.31752 137.237 9.84752 136.697C10.1575 136.367 10.3175 136.207 10.4675 136.057C18.1075 128.047 21.1376 116.717 18.5276 105.967C18.4776 105.757 18.4176 105.538 18.3076 105.098C18.1176 104.368 18.0275 103.997 17.9475 103.687C14.0075 86.9774 23.7575 70.0773 40.2075 65.1373C40.5175 65.0473 40.8775 64.9474 41.6075 64.7374C42.0475 64.6174 42.2675 64.5574 42.4775 64.4974C53.0875 61.3874 61.3875 53.0874 64.4975 42.4774C64.5575 42.2674 64.6175 42.0475 64.7375 41.6075C64.9475 40.8775 65.0476 40.5174 65.1376 40.2074C70.0776 23.7574 86.9775 14.0074 103.687 17.9474C103.997 18.0274 104.368 18.1175 105.098 18.3075C105.538 18.4175 105.758 18.4775 105.968 18.5275C116.718 21.1375 128.048 18.1074 136.058 10.4674C136.208 10.3174 136.367 10.1575 136.697 9.84752Z"

        private const val SHAPE6_PATH_DATA =
            "M79.86 37.77C130.22 -12.59 211.87 -12.59 262.23 37.77C312.59 88.13 312.59 169.78 262.23 220.14L220.14 262.23C169.78 312.59 88.13 312.59 37.77 262.23C-12.59 211.87 -12.59 130.22 37.77 79.86L79.86 37.77Z"

        private const val PLAY_PATH_DATA =
            "M6 4v16a1 1 0 0 0 1.524 .852l13 -8a1 1 0 0 0 0 -1.704l-13 -8a1 1 0 0 0 -1.524 .852z"

        private const val PAUSE_PATH_DATA =
            "M9 4h-2a2 2 0 0 0 -2 2v12a2 2 0 0 0 2 2h2a2 2 0 0 0 2 -2v-12a2 2 0 0 0 -2 -2z M17 4h-2a2 2 0 0 0 -2 2v12a2 2 0 0 0 2 2h2a2 2 0 0 0 2 -2v-12a2 2 0 0 0 -2 -2z"

        private val baseShape1Path: Path by lazy { PathParser.createPathFromPathData(SHAPE1_PATH_DATA) }
        private val baseShape6Path: Path by lazy { PathParser.createPathFromPathData(SHAPE6_PATH_DATA) }
        private val basePlayPath: Path by lazy { PathParser.createPathFromPathData(PLAY_PATH_DATA) }
        private val basePausePath: Path by lazy { PathParser.createPathFromPathData(PAUSE_PATH_DATA) }
    }

    private var currentUsername: String = ""
    private var isSelfProfile: Boolean = false
    private var currentTrack: LastFmTrack? = null
    private var palette: LastFmPalette = LastFmPaletteHelper.DEFAULT_PALETTE

    private val cardPath = Path()
    private val cardRect = RectF()
    private val shape1Path = Path()
    private val shape6Path = Path()
    private val playPath = Path()
    private val pausePath = Path()

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(18, 255, 255, 255) // ~7% white pressed overlay
    }
    private val shape1Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shape6Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val placeholderNotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = AndroidUtilities.bold()
        textAlign = Paint.Align.CENTER
    }

    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = AndroidUtilities.bold()
    }
    private val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255) // 86% white
        typeface = Typeface.DEFAULT
    }
    private val statusPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255) // 67% white
        typeface = Typeface.DEFAULT
    }

    private val imageReceiver = ImageReceiver(this)
    private var coverBitmap: Bitmap? = null
    private var coverShader: BitmapShader? = null
    private val shaderMatrix = Matrix()

    private var pollingActive = false
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (pollingActive && currentUsername.isNotEmpty()) {
                fetchTrack(false)
                AndroidUtilities.runOnUIThread(this, POLL_INTERVAL_MS)
            }
        }
    }

    init {
        isClickable = true
        setWillNotDraw(false)
        imageReceiver.setRoundRadius(0)
        imageReceiver.setDelegate(object : ImageReceiver.ImageReceiverDelegate {
            override fun didSetImage(receiver: ImageReceiver, set: Boolean, thumb: Boolean, memCache: Boolean) {
                if (!set || thumb) return
                val bmp = receiver.bitmap ?: return
                coverBitmap = bmp
                coverShader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

                Utilities.themeQueue.postRunnable {
                    val p = LastFmPaletteHelper.extract(bmp)
                    AndroidUtilities.runOnUIThread {
                        palette = p
                        updateCardShader()
                        invalidate()
                    }
                }
                invalidate()
            }

            override fun didSetImageBitmap(type: Int, url: String?, drawable: Drawable?) {}
            override fun onAnimationReady(receiver: ImageReceiver?) {}
        })
    }

    fun bind(username: String, isSelf: Boolean) {
        val trimmed = username.trim()
        val usernameChanged = !TextUtils.equals(currentUsername, trimmed)
        currentUsername = trimmed
        isSelfProfile = isSelf

        if (usernameChanged) {
            currentTrack = null
            coverBitmap = null
            coverShader = null
            palette = LastFmPaletteHelper.DEFAULT_PALETTE
            imageReceiver.setImageBitmap(null as Bitmap?)
            updateCardShader()
            invalidate()
            if (pollingActive) {
                AndroidUtilities.cancelRunOnUIThread(pollRunnable)
                fetchTrack(true)
                AndroidUtilities.runOnUIThread(pollRunnable, POLL_INTERVAL_MS)
            }
        }
    }

    private fun fetchTrack(immediate: Boolean) {
        if (currentUsername.isEmpty()) return
        val userToFetch = currentUsername
        LastFmApiClient.fetchRecentTrack(userToFetch, isSelfProfile, object : LastFmApiClient.Callback {
            override fun onResult(track: LastFmTrack?) {
                AndroidUtilities.runOnUIThread {
                    if (!TextUtils.equals(currentUsername, userToFetch)) return@runOnUIThread
                    val oldTrack = currentTrack
                    currentTrack = track

                    val oldCover = oldTrack?.coverUrl
                    val newCover = track?.coverUrl
                    if (!TextUtils.equals(oldCover, newCover)) {
                        coverBitmap = null
                        coverShader = null
                        if (!newCover.isNullOrEmpty()) {
                            imageReceiver.setImage(ImageLocation.getForPath(newCover), null, null as Drawable?, null as String?, null, 0)
                        } else {
                            imageReceiver.setImageBitmap(null as Bitmap?)
                            palette = LastFmPaletteHelper.DEFAULT_PALETTE
                            updateCardShader()
                        }
                    }
                    invalidate()
                }
            }
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        imageReceiver.onAttachedToWindow()
        pollingActive = true
        AndroidUtilities.cancelRunOnUIThread(pollRunnable)
        if (currentUsername.isNotEmpty()) {
            fetchTrack(true)
            AndroidUtilities.runOnUIThread(pollRunnable, POLL_INTERVAL_MS)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        imageReceiver.onDetachedFromWindow()
        pollingActive = false
        AndroidUtilities.cancelRunOnUIThread(pollRunnable)
    }

    private val cardHeight get() = AndroidUtilities.dp(92f)
    private val marginTop get() = AndroidUtilities.dp(6f)
    // Standard inter-section gap: ShadowSectionCell (12dp) + M3 inter-row gap (2dp when M3 is enabled)
    private val marginBottom get() = AndroidUtilities.dp(12f) + (if (M3SectionsHelper.isEnabled()) AndroidUtilities.dp(2f) else 0)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = marginTop + cardHeight + marginBottom
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updatePaths(w, h)
        updateCardShader()
    }

    private fun updatePaths(w: Int, h: Int) {
        val marginH = AndroidUtilities.dp(12f)
        val top = marginTop.toFloat()
        val bottom = (marginTop + cardHeight).toFloat()
        cardRect.set(marginH.toFloat(), top, (w - marginH).toFloat(), bottom)

        // Uniform rounded corners (16dp) on all four corners matching stock Telegram cards and NagramXF
        val cornerRadius = AndroidUtilities.dp(16f).toFloat()
        cardPath.reset()
        cardPath.addRoundRect(cardRect, cornerRadius, cornerRadius, Path.Direction.CW)

        // Album art: 60dp square, left-aligned 16dp inside card, vertically centered
        val artLeft = cardRect.left + AndroidUtilities.dp(16f)
        val artTop = cardRect.top + (cardRect.height() - AndroidUtilities.dp(60f)) / 2f
        val artSize = AndroidUtilities.dp(60f).toFloat()
        imageReceiver.setImageCoords(artLeft, artTop, artSize, artSize)

        // Transform material_shape1 to 60x60dp at (artLeft, artTop)
        // Note: material_shape1 path viewBox is (-4, -4, 328, 328) with total span ~328
        val matrix1 = Matrix()
        val scale1 = artSize / 328f
        matrix1.setScale(scale1, scale1)
        matrix1.postTranslate(artLeft + 4f * scale1, artTop + 4f * scale1)
        baseShape1Path.transform(matrix1, shape1Path)

        // Play/Pause button: 44dp square, right-aligned 16dp inside card, vertically centered
        val btnLeft = cardRect.right - AndroidUtilities.dp(16f) - AndroidUtilities.dp(44f)
        val btnTop = cardRect.top + (cardRect.height() - AndroidUtilities.dp(44f)) / 2f
        val btnSize = AndroidUtilities.dp(44f).toFloat()

        // Transform material_shape6 to 44x44dp at (btnLeft, btnTop)
        val matrix6 = Matrix()
        val scale6 = btnSize / 300f
        matrix6.setScale(scale6, scale6)
        matrix6.postTranslate(btnLeft, btnTop)
        baseShape6Path.transform(matrix6, shape6Path)

        // Inside icon: 20dp square, centered inside 44dp button (offset 12dp from button origin)
        val iconLeft = btnLeft + AndroidUtilities.dp(12f)
        val iconTop = btnTop + AndroidUtilities.dp(12f)
        val iconSize = AndroidUtilities.dp(20f).toFloat()

        val matrixIcon = Matrix()
        val scaleIcon = iconSize / 24f
        matrixIcon.setScale(scaleIcon, scaleIcon)
        matrixIcon.postTranslate(iconLeft, iconTop)
        basePlayPath.transform(matrixIcon, playPath)
        basePausePath.transform(matrixIcon, pausePath)
    }

    private fun updateCardShader() {
        if (cardRect.width() > 0 && cardRect.height() > 0) {
            val gradient = LinearGradient(
                cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                palette.color1, palette.color2, Shader.TileMode.CLAMP
            )
            cardPaint.shader = gradient
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Card Background
        canvas.drawPath(cardPath, cardPaint)

        // 2. Pressed Overlay
        if (isPressed) {
            canvas.drawPath(cardPath, overlayPaint)
        }

        // 3. Album Art
        val artLeft = cardRect.left + AndroidUtilities.dp(16f)
        val artTop = cardRect.top + (cardRect.height() - AndroidUtilities.dp(60f)) / 2f
        val artSize = AndroidUtilities.dp(60f).toFloat()

        val bmp = coverBitmap
        if (bmp != null && !bmp.isRecycled) {
            val shader = coverShader
            if (shader != null) {
                val bW = bmp.width.toFloat()
                val bH = bmp.height.toFloat()
                val scale = maxOf(artSize / bW, artSize / bH)
                val dx = artLeft + (artSize - bW * scale) / 2f
                val dy = artTop + (artSize - bH * scale) / 2f
                shaderMatrix.setScale(scale, scale)
                shaderMatrix.postTranslate(dx, dy)
                shader.setLocalMatrix(shaderMatrix)
                shape1Paint.shader = shader
                canvas.drawPath(shape1Path, shape1Paint)
            }
        } else {
            // Placeholder: material_shape1 filled with color1 (#2D3446) + centered ♫ glyph in semibold white
            shape1Paint.shader = null
            shape1Paint.color = LastFmPaletteHelper.DEFAULT_PALETTE.color1
            canvas.drawPath(shape1Path, shape1Paint)

            placeholderNotePaint.textSize = AndroidUtilities.dp(24f).toFloat()
            val noteX = artLeft + artSize / 2f
            val fm = placeholderNotePaint.fontMetrics
            val noteY = artTop + artSize / 2f - (fm.ascent + fm.descent) / 2f
            canvas.drawText("♫", noteX, noteY, placeholderNotePaint)
        }

        // 4. Play/Pause State Indicator
        val accent = if (isPressed) {
            ColorUtils.blendARGB(palette.accentColor, Color.BLACK, 0.20f)
        } else {
            palette.accentColor
        }
        shape6Paint.color = accent
        canvas.drawPath(shape6Path, shape6Paint)

        val isNowPlaying = currentTrack?.isNowPlaying ?: false
        val activeIconPath = if (isNowPlaying) pausePath else playPath
        canvas.drawPath(activeIconPath, iconPaint)

        // 5. Text Block (3 single-line rows, 3dp gaps, vertically centered as a group)
        val textLeft = cardRect.left + AndroidUtilities.dp(16f + 60f + 14f) // 90dp from card left
        val textRight = cardRect.right - AndroidUtilities.dp(16f + 44f + 12f) // 72dp from card right
        val maxTextWidth = (textRight - textLeft).coerceAtLeast(10f)

        val titleText = currentTrack?.title ?: LocaleController.getString(R.string.InuLastFmUnknownTrack)
        val artistText = currentTrack?.artist ?: LocaleController.getString(R.string.InuLastFmUnknownArtist)
        val statusText = if (isNowPlaying) {
            LocaleController.getString(R.string.InuLastFmNowPlaying)
        } else {
            LocaleController.getString(R.string.InuLastFmScrobbling)
        }

        val titleSize = AndroidUtilities.dp(15f).toFloat()
        val subSize = (titleSize - AndroidUtilities.dp(2f)).coerceAtLeast(AndroidUtilities.dp(10f).toFloat())
        titlePaint.textSize = titleSize
        artistPaint.textSize = subSize
        statusPaint.textSize = subSize

        val ellipTitle = TextUtils.ellipsize(titleText, titlePaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
        val ellipArtist = TextUtils.ellipsize(artistText, artistPaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
        val ellipStatus = TextUtils.ellipsize(statusText, statusPaint, maxTextWidth, TextUtils.TruncateAt.END).toString()

        val gap = AndroidUtilities.dp(3f).toFloat()
        val fmTitle = titlePaint.fontMetrics
        val fmArtist = artistPaint.fontMetrics
        val fmStatus = statusPaint.fontMetrics

        val hTitle = fmTitle.descent - fmTitle.ascent
        val hArtist = fmArtist.descent - fmArtist.ascent
        val hStatus = fmStatus.descent - fmStatus.ascent
        val totalTextHeight = hTitle + gap + hArtist + gap + hStatus

        val groupTop = cardRect.top + (cardRect.height() - totalTextHeight) / 2f

        val y1 = groupTop - fmTitle.ascent
        canvas.drawText(ellipTitle, textLeft, y1, titlePaint)

        val y2 = groupTop + hTitle + gap - fmArtist.ascent
        canvas.drawText(ellipArtist, textLeft, y2, artistPaint)

        val y3 = groupTop + hTitle + gap + hArtist + gap - fmStatus.ascent
        canvas.drawText(ellipStatus, textLeft, y3, statusPaint)
    }

    override fun performClick(): Boolean {
        super.performClick()
        val url = currentTrack?.trackUrl?.takeIf { it.isNotEmpty() }
            ?: "https://www.last.fm/user/" + Uri.encode(currentUsername)
        Browser.openUrl(context, url)
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || !isClickable) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (cardRect.contains(event.x, event.y)) {
                    isPressed = true
                    invalidate()
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val pressed = cardRect.contains(event.x, event.y)
                if (isPressed != pressed) {
                    isPressed = pressed
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isPressed) {
                    isPressed = false
                    invalidate()
                    performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isPressed) {
                    isPressed = false
                    invalidate()
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
