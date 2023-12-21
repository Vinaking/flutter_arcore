package com.example.flutter_arcore

import android.content.Context
import android.graphics.*
import android.view.View
import androidx.core.content.ContextCompat
import org.tensorflow.lite.task.vision.detector.Detection
import java.util.*

class ObjectDetectView(context: Context) : View(context) {
    private var boundryPaint: Paint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()
    private var bounds = Rect()

    private var results: List<Detection> = LinkedList<Detection>()
    private var text: String = ""
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    private var scaleFactor = 1f

    init {
        boundryPaint.color = Color.YELLOW
        boundryPaint.strokeWidth = 10f
        boundryPaint.style = Paint.Style.STROKE

        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 50f

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f

    }

    fun setData(results: List<Detection>, text: String, imageWidth: Int, imageHeight: Int) {
        this.results = results
        this.text = text
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        scaleFactor = kotlin.math.max(width * 1f / imageWidth, height * 1f / imageHeight)

        val leftTop = (width - (width * scaleFactor))/2f

        for (result in results) {
            val rect = result.boundingBox
            val top = rect.top * scaleFactor
            val bottom = rect.bottom * scaleFactor
            val left = rect.left * scaleFactor + leftTop
            val right = rect.right * scaleFactor + leftTop

            // Draw bounding box around detected objects
            val drawableRect = RectF(left, top, right, bottom)
            canvas.drawRect(drawableRect, boundryPaint)

            val drawableText =
                result.categories[0].label + " " +
                        String.format("%.2f", result.categories[0].score)

            // Draw rect behind display text
            textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
            val textWidth = bounds.width()
            val textHeight = bounds.height()
            canvas.drawRect(
                left,
                top,
                left + textWidth + 8,
                top + textHeight + 8,
                textBackgroundPaint
            )
            canvas.drawText(drawableText, left, top + bounds.height(), textPaint)

        }

    }

}