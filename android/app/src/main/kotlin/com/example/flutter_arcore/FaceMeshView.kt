package com.example.flutter_arcore

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.google.ar.sceneform.math.Vector3

class FaceMeshView(context: Context) : View(context) {
    private var points = ArrayList<Vector3>()
    private var pointsRight = ArrayList<Vector3>()
    private var pointsBottom = ArrayList<Vector3>()
    private val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    fun setPointsRight(points: ArrayList<Vector3>) {
        this.pointsRight = points
        invalidate()
    }

    fun setPointsBottom(points: ArrayList<Vector3>) {
        this.pointsBottom = points
        invalidate()
    }

    fun setPoints(points: ArrayList<Vector3>) {
        this.points = points
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (point in points) {
            canvas.drawCircle(point.x, point.y, 5f, paint)
        }
    }
}