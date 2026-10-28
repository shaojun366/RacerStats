package com.example.racerstats.maps

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.racerstats.R
import com.example.racerstats.track.model.GpsPoint
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Local, north-up route schematic. It is not a map or an editable gate surface. */
class TrackRouteView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val route = Path()
    private var points = emptyList<GpsPoint>()
    private var start: Pair<GpsPoint, GpsPoint>? = null
    private var finish: Pair<GpsPoint, GpsPoint>? = null
    private val density = resources.displayMetrics.density

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }

    fun setRoute(
        points: List<GpsPoint>,
        startLine: Pair<GpsPoint, GpsPoint>? = null,
        finishLine: Pair<GpsPoint, GpsPoint>? = null
    ) {
        this.points = points.filter(::valid)
        start = startLine?.takeIf { valid(it.first) && valid(it.second) }
        finish = finishLine?.takeIf { valid(it.first) && valid(it.second) }
        contentDescription = context.getString(R.string.route_accessibility, this.points.size)
        invalidate()
    }

    private fun valid(p: GpsPoint) = p.latitude.isFinite() && p.longitude.isFinite() &&
        p.latitude in -90.0..90.0 && p.longitude in -180.0..180.0

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(ContextCompat.getColor(context, R.color.card))
        pen.style = Paint.Style.STROKE
        pen.strokeWidth = density
        pen.color = ContextCompat.getColor(context, R.color.line)
        val step = 32 * density
        var x = 0f
        while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), pen); x += step }
        var y = 0f
        while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, pen); y += step }
        pen.style = Paint.Style.FILL
        pen.textSize = 11 * resources.displayMetrics.scaledDensity
        pen.color = ContextCompat.getColor(context, R.color.muted)
        canvas.drawText(context.getString(R.string.route_schematic), 18 * density, 26 * density, pen)
        canvas.drawText("N ↑", width - 42 * density, 26 * density, pen)
        val all = points + listOfNotNull(start?.first, start?.second, finish?.first, finish?.second)
        if (all.isEmpty()) {
            pen.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.route_empty), width / 2f, height / 2f, pen)
            pen.textAlign = Paint.Align.LEFT
            return
        }
        val origin = all.first()
        val longitudeScale = max(0.00001, cos(Math.toRadians(origin.latitude)))
        fun east(p: GpsPoint): Double = ((p.longitude - origin.longitude + 540) % 360 - 180) * longitudeScale
        fun north(p: GpsPoint): Double = p.latitude - origin.latitude
        val minX = all.minOf(::east); val maxX = all.maxOf(::east)
        val minY = all.minOf(::north); val maxY = all.maxOf(::north)
        val padding = 44 * density
        val scale = min(max(1f, width - 2 * padding) / max(0.00001, maxX - minX),
            max(1f, height - 2 * padding) / max(0.00001, maxY - minY))
        fun px(p: GpsPoint) = (width / 2 + (east(p) - (minX + maxX) / 2) * scale).toFloat()
        fun py(p: GpsPoint) = (height / 2 - (north(p) - (minY + maxY) / 2) * scale).toFloat()
        route.reset()
        points.forEachIndexed { i, p -> if (i == 0) route.moveTo(px(p), py(p)) else route.lineTo(px(p), py(p)) }
        pen.style = Paint.Style.STROKE
        pen.strokeJoin = Paint.Join.ROUND
        pen.strokeCap = Paint.Cap.ROUND
        pen.color = ContextCompat.getColor(context, R.color.accent)
        pen.strokeWidth = 4 * density
        canvas.drawPath(route, pen)
        fun gate(line: Pair<GpsPoint, GpsPoint>?, color: Int) {
            line ?: return
            pen.color = ContextCompat.getColor(context, color)
            pen.strokeWidth = 5 * density
            canvas.drawLine(px(line.first), py(line.first), px(line.second), py(line.second), pen)
        }
        gate(start, R.color.good)
        if (finish != start) gate(finish, R.color.bad)
        points.firstOrNull()?.let {
            pen.style = Paint.Style.FILL
            pen.color = ContextCompat.getColor(context, R.color.text)
            canvas.drawCircle(px(it), py(it), 4 * density, pen)
        }
        pen.style = Paint.Style.FILL
        pen.color = ContextCompat.getColor(context, R.color.muted)
        canvas.drawText(context.getString(R.string.route_legend), 18 * density, height - 16 * density, pen)
    }
}
