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
private const val SUGGESTION_COLUMNS = 4
private const val SUGGESTION_ROWS = 3

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

    private var backdrop: View? = null
    private var resultCallback: ((ChoiceResultType, String) -> Unit)? = null

    /**
     * KanjiChoiceWindow does not need to reInit layout as its getDefaultParams() are all relative. Re-initing will cause bugs.
     */
    override fun reInit(options: Window.ReinitOptions)
    {
        options.reinitViewLayout = false
        super.reInit(options)
    }

    /**
     * [callback] is used when the finger was lifted over empty space: the panel then stays open and the
     * user taps an option (or taps outside to close it).
     */
    fun onSquareScrollStart(squareChar: ISquareChar, kanjiBoxParams: BoxParams, callback: (ChoiceResultType, String) -> Unit)
    {
        closeAll(false)
        resultCallback = callback

        if (squareChar is SquareCharOcr)
        {
            drawFixedLayout(squareChar)
        }

        show()
    }

    fun onSquareScroll(e: MotionEvent) : Int
    {
        val hovered = findSelectable(e)

        for (kanjiView in currentKanjiViews)
        {
            if (isSelectable(kanjiView))
            {
                setCellBackground(kanjiView, kanjiView === hovered)
            }
        }

        return when ((hovered?.tag as? ChoiceResultType))
        {
            ChoiceResultType.EDIT -> R.drawable.icon_edit
            ChoiceResultType.DELETE -> R.drawable.icon_delete
            else -> R.drawable.icon_swap
        }
    }

    fun onSquareScrollEnd(e: MotionEvent) : Pair<ChoiceResultType, String>
    {
        val hovered = findSelectable(e)

        if (hovered != null)
        {
            val result = resultFor(hovered)
            closeAll(true)
            return result
        }

        // Finger lifted over nothing: keep the panel open so an option can be tapped
        for (kanjiView in currentKanjiViews)
        {
            if (isSelectable(kanjiView))
            {
                setCellBackground(kanjiView, false)
            }
        }
        addBackdrop()

        return Pair(ChoiceResultType.NONE, "")
    }

    private fun addBackdrop()
    {
        if (backdrop != null) return

        val v = View(context)
        v.setBackgroundColor(0x22000000)
        v.setOnClickListener { finishWith(ChoiceResultType.NONE, "") }
        choiceWindow.addView(v, 0, RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT))
        backdrop = v
    }

    private fun finishWith(type: ChoiceResultType, text: String)
    {
        val callback = resultCallback
        closeAll(true)
        callback?.invoke(type, text)
    }

    private fun closeAll(hideWindow: Boolean)
    {
        removeKanjiViews()

        backdrop?.let { choiceWindow.removeView(it) }
        backdrop = null
        resultCallback = null

        if (hideWindow)
        {
            hide()
        }
    }

    private fun isSelectable(v: View): Boolean
    {
        return v is TextView || v.tag is ChoiceResultType
    }

    private fun findSelectable(e: MotionEvent): View?
    {
        for (kanjiView in currentKanjiViews)
        {
            if (isSelectable(kanjiView) && checkForSelection(kanjiView, e))
            {
                return kanjiView
            }
        }
        return null
    }

    private fun resultFor(v: View): Pair<ChoiceResultType, String>
    {
        val tag = v.tag
        return when
        {
            tag is ChoiceResultType -> Pair(tag, "")
            v is TextView -> Pair(ChoiceResultType.SWAP, v.text.toString())
            else -> Pair(ChoiceResultType.NONE, "")
        }
    }

    private fun setCellBackground(v: View, hovered: Boolean)
    {
        v.setBackgroundResource(when
        {
            hovered -> R.drawable.bg_solid_border_0_blue_black
            v.tag == KANA_PRIORITY_TAG -> R.drawable.bg_solid_border_0_yellow_black
            else -> R.drawable.bg_solid_border_0_white_black
        })
    }

    private fun checkForSelection(kanjiView: View, e: MotionEvent): Boolean
    {
        val pos = IntArray(2)
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

    /**
     * The panel always opens in the same place so it can be learned by muscle memory:
     *   - top left: a row with the character image, edit, delete and close, and under it the suggestions
     *   - next to it (wide screens) or below it (narrow screens): the whole hiragana and katakana set
     */
    private fun drawFixedLayout(squareChar: SquareCharOcr)
    {
        val pad = dpToPx(context, 6)
        val screenW = realDisplaySize.x
        val sugCell = dpToPx(context, 52)
        val sugCols = SUGGESTION_COLUMNS
        val sugRows = SUGGESTION_ROWS
        val zoneW = sugCols * sugCell
        val zoneH = (1 + sugRows) * sugCell
        val left = pad
        val top = pad

        // Row 0: character image, edit, delete, close
        drawKanjiImage(squareChar, left, top, sugCell, sugCell)
        drawActionCell(ChoiceResultType.EDIT, left + sugCell, top, sugCell)
        drawActionCell(ChoiceResultType.DELETE, left + sugCell * 2, top, sugCell)
        drawCloseCell(left + sugCell * 3, top, sugCell)

        // Rows 1..n: suggestions
        val choices = squareChar.allChoices
        for ((i, choice) in choices.withIndex())
        {
            if (i >= sugCols * sugRows) break
            drawKanjiText(choice.first, left + (i % sugCols) * sugCell, top + (1 + i / sugCols) * sugCell, sugCell, sugCell)
        }

        // Kana: beside the suggestions if there is room, otherwise underneath
        val maxKanaCell = dpToPx(context, 44)
        val besideCell = minOf((screenW - left - zoneW - pad * 2) / 11, maxKanaCell)
        val kanaCell: Int
        val kanaX: Int
        val kanaY: Int
        if (besideCell >= dpToPx(context, 30))
        {
            kanaCell = besideCell
            kanaX = left + zoneW + pad
            kanaY = top
        }
        else
        {
            kanaCell = minOf((screenW - pad * 2) / 11, maxKanaCell)
            kanaX = (screenW - kanaCell * 11) / 2
            kanaY = top + zoneH + pad
        }

        if (kanaCell > 0)
        {
            drawKanaPanel(kanaX, kanaY, kanaCell)
        }
    }

    private fun drawKanaPanel(x0: Int, y0: Int, cell: Int)
    {
        for (block in 0..1)
        {
            val rowsOfKana = if (block == 0) HIRAGANA_ROWS else KATAKANA_ROWS
            val priority = if (block == 0) HIRAGANA_PRIORITY else KATAKANA_PRIORITY
            val originX = x0 + block * 6 * cell

            for ((r, rowChars) in rowsOfKana.withIndex())
            {
                for ((c, ch) in rowChars.withIndex())
                {
                    if (ch == KANA_BLANK) continue
                    drawKanaCell(ch.toString(), originX + c * cell, y0 + r * cell, cell,
                            block == 0, priority.indexOf(ch) >= 0)
                }
            }
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
        }
        setCellBackground(tv, false)

        addCell(tv, x, y, size, size)
    }

    private fun drawKanjiText(kanji: String, x: Int, y: Int, kanjiWidth: Int, kanjiHeight: Int)
    {
        val tv = TextView(context)
        tv.text = kanji
        tv.gravity = Gravity.CENTER
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, (kanjiWidth / 1.5).toFloat())

        when
        {
            kanji.isEmpty() -> tv.setTextColor(Color.GRAY)
            LangUtils.IsHiragana(kanji[0]) -> tv.setTextColor(ContextCompat.getColor(context, R.color.kana_pink))
            LangUtils.IsKatakana(kanji[0]) -> tv.setTextColor(ContextCompat.getColor(context, R.color.kana_blue))
            LangUtils.IsKanji(kanji[0]) -> tv.setTextColor(Color.BLACK)
            else -> tv.setTextColor(Color.GRAY)
        }

        setCellBackground(tv, false)
        addCell(tv, x, y, kanjiWidth, kanjiHeight)
    }

    private fun drawActionCell(type: ChoiceResultType, x: Int, y: Int, size: Int)
    {
        val iv = ImageView(context)
        val p = size / 5
        iv.setPadding(p, p, p, p)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.setImageResource(if (type == ChoiceResultType.EDIT) R.drawable.icon_edit else R.drawable.icon_delete)
        iv.tag = type
        setCellBackground(iv, false)
        addCell(iv, x, y, size, size)
    }

    private fun drawCloseCell(x: Int, y: Int, size: Int)
    {
        val tv = TextView(context)
        tv.text = "\u2715"
        tv.gravity = Gravity.CENTER
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, (size / 2.5).toFloat())
        tv.setTextColor(Color.GRAY)
        tv.tag = ChoiceResultType.NONE
        setCellBackground(tv, false)
        addCell(tv, x, y, size, size)
    }

    private fun addCell(v: View, x: Int, y: Int, width: Int, height: Int)
    {
        v.layoutParams = RelativeLayout.LayoutParams(width, height)
        v.x = x.toFloat()
        v.y = y.toFloat()

        if (isSelectable(v))
        {
            v.setOnClickListener {
                val result = resultFor(v)
                finishWith(result.first, result.second)
            }
        }

        choiceWindow.addView(v)
        currentKanjiViews.add(v)
    }

    private fun drawKanjiImage(squareChar: SquareCharOcr, x: Int, y: Int, kanjiWidth: Int, kanjiHeight: Int)
    {
        // Image nonsense
        val pos = squareChar.bitmapPos
        val dp10 = dpToPx(context, 10)
        val orig = squareChar.displayData.bitmap

        // Keep the crop inside the screenshot (characters at the very edge could otherwise crash)
        val cropX = pos[0].coerceIn(0, orig.width - 1)
        val cropY = pos[1].coerceIn(0, orig.height - 1)
        val width = (pos[2] - pos[0]).coerceIn(1, orig.width - cropX)
        val height = (pos[3] - pos[1]).coerceIn(1, orig.height - cropY)

        val bitmapChar = Bitmap.createBitmap(orig, cropX, cropY, width, height)
        val charImage = ImageView(context)
        charImage.setPadding(dp10, dp10, dp10, dp10)
        charImage.layoutParams = RelativeLayout.LayoutParams(kanjiWidth, kanjiHeight)
        charImage.x = x.toFloat()
        charImage.y = y.toFloat()
        charImage.scaleType = ImageView.ScaleType.FIT_CENTER
        charImage.cropToPadding = true
        charImage.setImageBitmap(bitmapChar)
        charImage.background = context.getDrawable(R.drawable.bg_translucent_border_0_black_black)
        choiceWindow.addView(charImage)
        currentKanjiViews.add(charImage)
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
