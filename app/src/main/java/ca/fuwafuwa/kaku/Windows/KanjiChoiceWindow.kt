package ca.fuwafuwa.kaku.Windows

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import ca.fuwafuwa.kaku.LangUtils
import ca.fuwafuwa.kaku.Ocr.BoxParams
import ca.fuwafuwa.kaku.R
import ca.fuwafuwa.kaku.Windows.Data.ISquareChar
import ca.fuwafuwa.kaku.Windows.Data.SquareCharOcr
import ca.fuwafuwa.kaku.dpToPx

enum class ChoiceResultType
{
    EDIT,
    DELETE,
    SWAP,
    NONE
}

private const val KANA_PRIORITY_TAG = "kana_priority"
private const val KANA_BLANK = '\u3000'

private val HIRAGANA_ROWS = listOf(
        "あいうえお", "かきくけこ", "さしすせそ", "たちつてと", "なにぬねの",
        "はひふへほ", "まみむめも", "や\u3000ゆ\u3000よ", "らりるれろ", "わ\u3000を\u3000ん")
private val KATAKANA_ROWS = listOf(
        "アイウエオ", "カキクケコ", "サシスセソ", "タチツテト", "ナニヌネノ",
        "ハヒフヘホ", "マミムメモ", "ヤ\u3000ユ\u3000ヨ", "ラリルレロ", "ワ\u3000ヲ\u3000ン")

// Kana whose strokes are often split apart by the OCR (highlighted in the panel). Edit freely.
private const val HIRAGANA_PRIORITY = "うこたなにりけふいはほま"
private const val KATAKANA_PRIORITY = "ンシツソリニミラルテハナケホ"

class KanjiChoiceWindow(context: Context, windowCoordinator: WindowCoordinator) : Window(context, windowCoordinator, R.layout.window_kanji_choice)
{
    private val choiceWindow = window.findViewById<RelativeLayout>(R.id.kanji_choice_window)!!
    private val currentKanjiViews = mutableListOf<View>()

    private lateinit var mKanjiBoxParams : BoxParams

    private var drawnOnTop = false

    /**
     * KanjiChoiceWindow does not need to reInit layout as its getDefaultParams() are all relative. Re-initing will cause bugs.
     */
    override fun reInit(options: Window.ReinitOptions)
    {
        options.reinitViewLayout = false
        super.reInit(options)
    }

    fun onSquareScrollStart(squareChar: ISquareChar, kanjiBoxParams: BoxParams)
    {
        if (squareChar !is SquareCharOcr)
        {
            show()

            mKanjiBoxParams = kanjiBoxParams
            mKanjiBoxParams.y -= statusBarHeight

            return
        }

        val topRectHeight = kanjiBoxParams.y - statusBarHeight
        val bottomRectHeight = realDisplaySize.y - kanjiBoxParams.y - kanjiBoxParams.height - (realDisplaySize.y - viewHeight - statusBarHeight)

        if (bottomRectHeight > topRectHeight)
        {
            drawnOnTop = false
            drawOnBottom(squareChar, kanjiBoxParams, calculateBounds(kanjiBoxParams, topRectHeight, bottomRectHeight))
        }
        else
        {
            drawnOnTop = true
            drawOnTop(squareChar, kanjiBoxParams, calculateBounds(kanjiBoxParams, topRectHeight, bottomRectHeight))
        }

        mKanjiBoxParams = kanjiBoxParams
        mKanjiBoxParams.y -= statusBarHeight

        show()
    }

    fun onSquareScroll(e: MotionEvent) : Int
    {
        var inKanji = false

        for (kanjiView in currentKanjiViews)
        {
            val isTextView = kanjiView is TextView

            if (checkForSelection(kanjiView, e) && isTextView)
            {
                inKanji = true
                kanjiView.setBackgroundResource(R.drawable.bg_solid_border_0_blue_black)
            }
            else if (isTextView)
            {
                kanjiView.setBackgroundResource(
                    if (kanjiView.tag == KANA_PRIORITY_TAG) R.drawable.bg_solid_border_0_yellow_black
                    else R.drawable.bg_solid_border_0_white_black)
            }
        }

        return when (getResultTypeForMotionEvent(e, inKanji, drawnOnTop))
        {
            ChoiceResultType.EDIT ->
            {
                R.drawable.icon_edit
            }
            ChoiceResultType.DELETE ->
            {
                R.drawable.icon_delete
            }
            else ->
            {
                R.drawable.icon_swap
            }
        }
    }

    fun onSquareScrollEnd(e: MotionEvent) : Pair<ChoiceResultType, String>
    {
        var swappedKanji = ""

        for (kanjiView in currentKanjiViews)
        {
            if (checkForSelection(kanjiView, e) && kanjiView is TextView)
            {
                swappedKanji = kanjiView.text.toString()
            }
        }

        removeKanjiViews()
        hide()

        return Pair(getResultTypeForMotionEvent(e, swappedKanji != "", drawnOnTop), swappedKanji)
    }

    private fun getResultTypeForMotionEvent(e: MotionEvent, inKanji: Boolean, drawnOnTop: Boolean) : ChoiceResultType
    {
        if (inKanji)
        {
            return ChoiceResultType.SWAP
        }

        val midpoint = mKanjiBoxParams.x + (mKanjiBoxParams.width / 2)
        val height = if (drawnOnTop) mKanjiBoxParams.y + mKanjiBoxParams.height + statusBarHeight else mKanjiBoxParams.y + statusBarHeight

        return if (e.rawX < midpoint && heightCheckForResult(e, height, drawnOnTop))
        {
            ChoiceResultType.EDIT
        }
        else if (e.rawX > midpoint && heightCheckForResult(e, height, drawnOnTop))
        {
            ChoiceResultType.DELETE
        }
        else
        {
            ChoiceResultType.NONE
        }
    }

    private fun heightCheckForResult(e: MotionEvent, height: Int, drawnOnTop: Boolean) : Boolean
    {
        return if (drawnOnTop) e.rawY > height else e.rawY < height
    }

    private fun checkForSelection(kanjiView: View, e: MotionEvent): Boolean
    {
        var pos = IntArray(2)
        kanjiView.getLocationOnScreen(pos)

        return pos[0] < e.rawX && e.rawX < pos[0] + kanjiView.width &&
               pos[1] < e.rawY && e.rawY < pos[1] + kanjiView.height
    }

    private fun removeKanjiViews()
    {
        for (k in currentKanjiViews)
        {
            choiceWindow.removeView(k)
        }

        currentKanjiViews.clear()
    }

    private fun calculateBounds(kanjiBoxParams: BoxParams, topRectHeight: Int, bottomRectHeight: Int) : BoxParams
    {
        val midPoint = kanjiBoxParams.x + (kanjiBoxParams.width / 2)
        var maxWidth = dpToPx(context, 400)
        var xPos = 0

        if (realDisplaySize.x > maxWidth)
        {
            xPos = midPoint - (maxWidth / 2)
            if (xPos < 0)
            {
                xPos = 0
            }
            else if (xPos + maxWidth > realDisplaySize.x)
            {
                xPos = realDisplaySize.x - maxWidth
            }
        }

        maxWidth = minOf(realDisplaySize.x, maxWidth)

        if (topRectHeight > bottomRectHeight)
        {
            return BoxParams(xPos, 0, maxWidth, topRectHeight)
        }
        else
        {
            return BoxParams(xPos, kanjiBoxParams.y + kanjiBoxParams.height - statusBarHeight, maxWidth, bottomRectHeight)
        }
    }

    private fun drawOnBottom(squareChar: SquareCharOcr, kanjiBoxParams: BoxParams, choiceParams: BoxParams)
    {
        val kanjiHeight = kanjiBoxParams.height * 2
        val kanjiWidth = kanjiBoxParams.width * 2

        val outerPadding = dpToPx(context, 10)
        val startHeight = choiceParams.y + outerPadding

        val drawableWidth = choiceParams.width - outerPadding
        val minPadding = dpToPx(context, 5)
        val numColumns = minOf(calculateNumColumns(drawableWidth, kanjiWidth, minPadding), squareChar.allChoices.size + 1)
        val outerSpacing = (choiceParams.width - (kanjiWidth + minPadding * 2) * numColumns) / 2
        val innerSpacing = minPadding

        var currColumn = 0
        var currWidth = choiceParams.x + outerSpacing + innerSpacing
        var currHeight = startHeight

        drawKanjiImage(squareChar, currWidth, currHeight, kanjiWidth, kanjiHeight)
        currWidth += kanjiWidth + innerSpacing
        currColumn++

        for (choice in squareChar.allChoices)
        {
            if (currColumn >= numColumns)
            {
                currHeight += kanjiHeight + innerSpacing
                currWidth = choiceParams.x + outerSpacing + innerSpacing
                currColumn = 0
            }

            drawKanjiText(choice.first, currWidth, currHeight, kanjiWidth, kanjiHeight)
            currWidth += kanjiWidth + innerSpacing
            currColumn++
        }

        val cols = maxOf(1, numColumns)
        val rows = (squareChar.allChoices.size + cols) / cols
        val gridLeft = choiceParams.x + outerSpacing + innerSpacing
        val gridRight = gridLeft + cols * (kanjiWidth + innerSpacing)
        drawKanaPanel(gridLeft, gridRight, startHeight, startHeight + rows * (kanjiHeight + innerSpacing),
                choiceParams.y, choiceParams.y + choiceParams.height, false)
    }

    private fun drawOnTop(squareChar: SquareCharOcr, kanjiBoxParams: BoxParams, choiceParams: BoxParams)
    {
        val kanjiHeight = kanjiBoxParams.height * 2
        val kanjiWidth = kanjiBoxParams.width * 2

        val outerPadding = dpToPx(context, 10)
        val startHeight = kanjiBoxParams.y - statusBarHeight - kanjiHeight - outerPadding

        val drawableWidth = choiceParams.width - outerPadding
        val minPadding = dpToPx(context, 5)
        val numColumns = minOf(calculateNumColumns(drawableWidth, kanjiWidth, minPadding), squareChar.allChoices.size + 1)
        val outerSpacing = (choiceParams.width - (kanjiWidth + minPadding * 2) * numColumns) / 2
        val innerSpacing = minPadding

        var currColumn = 0
        var currWidth = choiceParams.x + outerSpacing + innerSpacing
        var currHeight = startHeight

        drawKanjiImage(squareChar, currWidth, currHeight, kanjiWidth, kanjiHeight)
        currWidth += kanjiWidth + innerSpacing
        currColumn++

        for (choice in squareChar.allChoices)
        {
            if (currColumn >= numColumns)
            {
                currHeight -= kanjiHeight + innerSpacing
                currWidth = choiceParams.x + outerSpacing + innerSpacing
                currColumn = 0
            }

            drawKanjiText(choice.first, currWidth, currHeight, kanjiWidth, kanjiHeight)
            currWidth += kanjiWidth + innerSpacing
            currColumn++
        }

        val cols = maxOf(1, numColumns)
        val rows = (squareChar.allChoices.size + cols) / cols
        val gridLeft = choiceParams.x + outerSpacing + innerSpacing
        val gridRight = gridLeft + cols * (kanjiWidth + innerSpacing)
        drawKanaPanel(gridLeft, gridRight, startHeight - (rows - 1) * (kanjiHeight + innerSpacing), startHeight + kanjiHeight,
                choiceParams.y, choiceParams.y + choiceParams.height, true)
    }

    /**
     * Permanent kana panel: the whole gojuon (hiragana and katakana) is always offered as swap targets,
     * with the kana whose strokes are often split apart by the OCR tinted yellow. It goes in the free space
     * beside the suggestion grid, or above/below it when the screen is too narrow for that.
     */
    private fun drawKanaPanel(gridLeft: Int, gridRight: Int, gridTop: Int, gridBottom: Int,
                              regionTop: Int, regionBottom: Int, onTop: Boolean)
    {
        val pad = dpToPx(context, 6)
        val screenW = realDisplaySize.x
        val maxCell = dpToPx(context, 44)

        val leftW = gridLeft - pad * 2
        val rightW = screenW - gridRight - pad * 2
        val sideIsRight = rightW >= leftW
        val sideLayout = bestKanaLayout(maxOf(leftW, rightW), regionBottom - regionTop - pad * 2, maxCell)

        val dockTop = if (onTop) regionTop + pad else gridBottom + pad
        val dockBottom = if (onTop) gridTop - pad else regionBottom - pad
        val dockLayout = bestKanaLayout(screenW - pad * 2, dockBottom - dockTop, maxCell)

        val useSide = sideLayout != null && (dockLayout == null || sideLayout.cell >= dockLayout.cell)
        val layout = (if (useSide) sideLayout else dockLayout) ?: return
        if (layout.cell < dpToPx(context, 14)) return

        val cell = layout.cell
        val panelW = layout.widthCells * cell
        val panelH = layout.heightCells * cell

        val x0: Int
        val y0: Int
        if (useSide)
        {
            x0 = if (sideIsRight) gridRight + pad else gridLeft - pad - panelW
            y0 = if (onTop) regionBottom - pad - panelH else regionTop + pad
        }
        else
        {
            x0 = (screenW - panelW) / 2
            y0 = if (onTop) gridTop - pad - panelH else gridBottom + pad
        }

        for (block in 0..1)
        {
            val rowsOfKana = if (block == 0) HIRAGANA_ROWS else KATAKANA_ROWS
            val priority = if (block == 0) HIRAGANA_PRIORITY else KATAKANA_PRIORITY
            val originX = if (layout.sideBySide) x0 + block * 6 * cell else x0
            val originY = if (layout.sideBySide) y0 else y0 + block * 11 * cell

            for ((r, rowChars) in rowsOfKana.withIndex())
            {
                for ((c, ch) in rowChars.withIndex())
                {
                    if (ch == KANA_BLANK) continue
                    drawKanaCell(ch.toString(), originX + c * cell, originY + r * cell, cell,
                            block == 0, priority.indexOf(ch) >= 0)
                }
            }
        }
    }

    private class KanaLayout(val cell: Int, val sideBySide: Boolean)
    {
        // hiragana + katakana are 5 columns x 10 rows each, with a one-cell gap between them
        val widthCells = if (sideBySide) 11 else 5
        val heightCells = if (sideBySide) 10 else 21
    }

    private fun bestKanaLayout(width: Int, height: Int, maxCell: Int): KanaLayout?
    {
        if (width <= 0 || height <= 0) return null

        val sideBySide = minOf(width / 11, height / 10, maxCell)
        val stacked = minOf(width / 5, height / 21, maxCell)

        return when
        {
            sideBySide <= 0 && stacked <= 0 -> null
            sideBySide >= stacked -> KanaLayout(sideBySide, true)
            else -> KanaLayout(stacked, false)
        }
    }

    private fun drawKanaCell(kana: String, x: Int, y: Int, size: Int, hiragana: Boolean, priority: Boolean)
    {
        val tv = TextView(context)
        tv.text = kana
        tv.gravity = Gravity.CENTER
        tv.includeFontPadding = false
        tv.setPadding(0, 0, 0, 0)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, (size / 1.5).toFloat())
        tv.setTextColor(ContextCompat.getColor(context, if (hiragana) R.color.kana_pink else R.color.kana_blue))

        if (priority)
        {
            tv.tag = KANA_PRIORITY_TAG
            tv.setBackgroundResource(R.drawable.bg_solid_border_0_yellow_black)
        }
        else
        {
            tv.setBackgroundResource(R.drawable.bg_solid_border_0_white_black)
        }

        tv.width = size
        tv.height = size
        tv.x = x.toFloat()
        tv.y = y.toFloat()
        choiceWindow.addView(tv)
        currentKanjiViews.add(tv)
    }

    private fun drawKanjiText(kanji: String, x: Int, y: Int, kanjiWidth: Int, kanjiHeight: Int)
    {
        val tv = TextView(context)
        tv.text = kanji
        tv.gravity = Gravity.CENTER
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, (kanjiWidth / 1.5).toFloat())

        when
        {
            LangUtils.IsHiragana(kanji[0]) -> tv.setTextColor(ContextCompat.getColor(context, R.color.kana_pink))
            LangUtils.IsKatakana(kanji[0]) -> tv.setTextColor(ContextCompat.getColor(context, R.color.kana_blue))
            LangUtils.IsKanji(kanji[0]) -> tv.setTextColor(Color.BLACK)
            else -> tv.setTextColor(Color.GRAY)
        }

        tv.setBackgroundResource(R.drawable.bg_solid_border_0_white_black)
        tv.width = kanjiWidth
        tv.height = kanjiHeight
        tv.x = x.toFloat()
        tv.y = y.toFloat()
        choiceWindow.addView(tv)
        currentKanjiViews.add(tv)
    }

    private fun drawKanjiImage(squareChar: SquareCharOcr, x: Int, y: Int, kanjiWidth: Int, kanjiHeight: Int)
    {
        // Image nonsense
        val pos = squareChar.bitmapPos
        val dp10 = dpToPx(context, 10)
        val orig = squareChar.displayData.bitmap
        var width = pos[2] - pos[0]
        var height = pos[3] - pos[1]
        width = if (width <= 0) 1 else width
        height = if (height <= 0) 1 else height
        val bitmapChar = Bitmap.createBitmap(orig, pos[0], pos[1], width, height)
        val charImage = ImageView(context)
        charImage.setPadding(dp10, dp10, dp10, dp10)
        charImage.layoutParams = LinearLayout.LayoutParams(kanjiWidth, kanjiHeight)
        charImage.x = x.toFloat()
        charImage.y = y.toFloat()
        charImage.scaleType = ImageView.ScaleType.FIT_CENTER
        charImage.cropToPadding = true
        charImage.setImageBitmap(bitmapChar)
        charImage.background = context.getDrawable(R.drawable.bg_translucent_border_0_black_black)
        choiceWindow.addView(charImage)
        currentKanjiViews.add(charImage)
    }

    private fun calculateNumColumns(drawableWidth: Int, columnWidth: Int, minPadding: Int) : Int
    {
        var count = 0
        var width = 0
        val columnAndPadding = columnWidth + (minPadding * 2)

        while ((width + columnAndPadding) < drawableWidth)
        {
            width += columnAndPadding
            count++
        }

        return count
    }


    override fun onTouch(e: MotionEvent): Boolean
    {
        return false
    }

    override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean
    {
        return false
    }

    override fun onResize(e: MotionEvent): Boolean
    {
        return false
    }

    override fun getDefaultParams(): WindowManager.LayoutParams
    {
        val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT > 25) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT)
        params.x = 0
        params.y = 0
        return params
    }
}