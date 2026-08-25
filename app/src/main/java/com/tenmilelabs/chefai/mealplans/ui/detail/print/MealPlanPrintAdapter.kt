package com.tenmilelabs.chefai.mealplans.ui.detail.print

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.tenmilelabs.chefai.mealplans.domain.print.MealPlanPrintDocument
import com.tenmilelabs.chefai.mealplans.domain.print.PrintTableBlock
import java.io.FileOutputStream

/**
 * Renders a [MealPlanPrintDocument] as a PDF for Android's Print Framework — one page per
 * [PrintTableBlock] (a row of up to 7 day columns).
 *
 * [onLayout] and [onWrite] can each be called more than once for the same instance: the system
 * re-invokes them if the user changes paper size or orientation in the print dialog, so nothing
 * here is computed once and cached from construction time — both read [attributes] fresh.
 */
class MealPlanPrintAdapter(
    private val document: MealPlanPrintDocument,
) : PrintDocumentAdapter() {

    private var attributes: PrintAttributes? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        if (document.blocks.isEmpty()) {
            callback.onLayoutFailed("Nothing to print")
            return
        }

        attributes = newAttributes
        val info = PrintDocumentInfo.Builder("${document.planName}.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(document.blocks.size)
            .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        val mediaSize = attributes?.mediaSize
        if (mediaSize == null) {
            callback.onWriteFailed("Not laid out")
            return
        }
        val margins = attributes?.minMargins

        val pdfDocument = PdfDocument()
        try {
            val pageWidth = millsToPoints(mediaSize.widthMils)
            val pageHeight = millsToPoints(mediaSize.heightMils)

            document.blocks.forEachIndexed { index, block ->
                if (cancellationSignal.isCanceled) {
                    callback.onWriteCancelled()
                    return
                }
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index).create()
                val page = pdfDocument.startPage(pageInfo)
                drawPage(page.canvas, block, pageWidth.toFloat(), pageHeight.toFloat(), margins)
                pdfDocument.finishPage(page)
            }

            FileOutputStream(destination.fileDescriptor).use { pdfDocument.writeTo(it) }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        } finally {
            pdfDocument.close()
        }
    }

    private fun drawPage(
        canvas: Canvas,
        block: PrintTableBlock,
        pageWidth: Float,
        pageHeight: Float,
        margins: PrintAttributes.Margins?,
    ) {
        val marginLeft = maxOf(MARGIN_POINTS, millsToPoints(margins?.leftMils ?: 0).toFloat())
        val marginTop = maxOf(MARGIN_POINTS, millsToPoints(margins?.topMils ?: 0).toFloat())
        val marginRight = maxOf(MARGIN_POINTS, millsToPoints(margins?.rightMils ?: 0).toFloat())
        val marginBottom = maxOf(MARGIN_POINTS, millsToPoints(margins?.bottomMils ?: 0).toFloat())

        val contentLeft = marginLeft
        val contentRight = pageWidth - marginRight
        val contentWidth = contentRight - contentLeft

        val titlePaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = TITLE_TEXT_SIZE
            isFakeBoldText = true
        }
        canvas.drawText(document.planName, contentLeft, marginTop + titlePaint.textSize, titlePaint)

        val tableTop = marginTop + TITLE_TEXT_SIZE + SECTION_GAP
        val tableBottom = pageHeight - marginBottom
        val columnCount = block.columns.size.coerceAtLeast(1)
        val columnWidth = contentWidth / columnCount

        val dayLabelPaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = DAY_LABEL_TEXT_SIZE
            isFakeBoldText = true
        }
        val mealTitlePaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = MEAL_TITLE_TEXT_SIZE
        }
        val ingredientPaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.DKGRAY
            textSize = INGREDIENT_TEXT_SIZE
        }
        val gridPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = GRID_STROKE_WIDTH
            color = Color.LTGRAY
        }

        canvas.drawRect(contentLeft, tableTop, contentRight, tableBottom, gridPaint)

        block.columns.forEachIndexed { index, column ->
            val columnLeft = contentLeft + columnWidth * index
            if (index > 0) {
                canvas.drawLine(columnLeft, tableTop, columnLeft, tableBottom, gridPaint)
            }

            val textLeft = columnLeft + CELL_PADDING
            val textWidth = (columnWidth - CELL_PADDING * 2).toInt().coerceAtLeast(1)

            canvas.save()
            canvas.translate(textLeft, tableTop + CELL_PADDING)

            var y = drawWrapped(canvas, column.label, textWidth, dayLabelPaint, 0f)
            y += ROW_GAP

            column.meals.forEach { meal ->
                val titleText = meal.slotLabel?.let { "$it: ${meal.title}" } ?: meal.title
                y += drawWrapped(canvas, titleText, textWidth, mealTitlePaint, y)

                if (meal.topIngredients.isNotEmpty()) {
                    y += drawWrapped(canvas, meal.topIngredients.joinToString(", "), textWidth, ingredientPaint, y)
                }
                y += MEAL_GAP
            }

            canvas.restore()
        }
    }

    /** Draws [text] word-wrapped to [width], offset down by [y] from the canvas's current origin; returns the height it consumed. */
    private fun drawWrapped(canvas: Canvas, text: String, width: Int, paint: TextPaint, y: Float): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(0f, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    private fun millsToPoints(mils: Int): Int = (mils * POINTS_PER_MIL).toInt()

    private companion object {
        /** A point is 1/72in; a mil is 1/1000in. */
        const val POINTS_PER_MIL = 72f / 1000f

        const val MARGIN_POINTS = 24f
        const val TITLE_TEXT_SIZE = 20f
        const val SECTION_GAP = 16f
        const val DAY_LABEL_TEXT_SIZE = 13f
        const val MEAL_TITLE_TEXT_SIZE = 10f

        /** Smaller than [MEAL_TITLE_TEXT_SIZE] — ingredients are a secondary line under the title. */
        const val INGREDIENT_TEXT_SIZE = 7.5f
        const val CELL_PADDING = 8f
        const val ROW_GAP = 6f
        const val MEAL_GAP = 10f
        const val GRID_STROKE_WIDTH = 1f
    }
}
