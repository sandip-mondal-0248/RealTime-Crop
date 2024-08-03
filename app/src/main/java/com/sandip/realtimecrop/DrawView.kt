package com.sandip.realtimecrop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt

/**
- A custom view that allows the user to draw a resizable rectangle on the screen.
- The rectangle is defined by four corner points, which can be dragged to adjust its size and position.
 */
class DrawView : View {
    private var points: Array<Point?> = arrayOfNulls(4) // Array to store the corner points of the rectangle

    // List to store the corner balls
    private val draggableBalls = ArrayList<DraggableBall>()

    // Identifier for the group of points (used to manage movements)
    private var groupId: Int = -1

    // Identifier for the currently selected ball
    private var ballId = 0

    // Paint object for drawing operations
    private var paint: Paint? = null

    // Primary constructor for initializing paint and canvas
    constructor(context: Context?) : super(context) {
        paint = Paint()
        isFocusable = true // Necessary for receiving touch events
    }

    // Secondary constructor for attribute initialization
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        paint = Paint()
        isFocusable = true // Necessary for receiving touch events
    }

    // Method to draw the rectangle and corner balls
    override fun onDraw(canvas: Canvas) {
        if (points[3] == null) return // If the fourth point is null, do not draw

        // Initialize rectangle boundaries
        var left: Int
        var top: Int
        var right: Int
        var bottom: Int
        left = points[0]!!.x
        top = points[0]!!.y
        right = points[0]!!.x
        bottom = points[0]!!.y

        // Calculate the minimum and maximum points for the rectangle
        for (i in 1 until points.size) {
            left = minOf(left, points[i]!!.x)
            top = minOf(top, points[i]!!.y)
            right = maxOf(right, points[i]!!.x)
            bottom = maxOf(bottom, points[i]!!.y)
        }

        // Configure paint for drawing the rectangle outline
        paint!!.isAntiAlias = true
        paint!!.isDither = true
        paint!!.strokeJoin = Paint.Join.ROUND
        paint!!.strokeWidth = 5f

        // Draw rectangle outline
        paint!!.style = Paint.Style.STROKE
        paint!!.color = Color.GREEN
        paint!!.strokeWidth = 2f
        canvas.drawRect(
            (left + draggableBalls[0].widthOfBall / 2).toFloat(),
            (top + draggableBalls[0].widthOfBall / 2).toFloat(),
            (right + draggableBalls[2].widthOfBall / 2).toFloat(),
            (bottom + draggableBalls[2].widthOfBall / 2).toFloat(), paint!!
        )

//        // Fill the rectangle with a transparent color
//        paint!!.style = Paint.Style.FILL
//        paint!!.color = Color.TRANSPARENT
//        paint!!.strokeWidth = 0f
//        canvas.drawRect(
//            (left + draggableBalls[0].widthOfBall / 2).toFloat(),
//            (top + draggableBalls[0].widthOfBall / 2).toFloat(),
//            (right + draggableBalls[2].widthOfBall / 2).toFloat(),
//            (bottom + draggableBalls[2].widthOfBall / 2).toFloat(), paint!!
//        )

        // Draw the corner balls
        paint!!.color = Color.GRAY
        paint!!.textSize = 18f
        paint!!.strokeWidth = 0f
        for (ball in draggableBalls) {
            canvas.drawBitmap(ball.bitmap, ball.x.toFloat(), ball.y.toFloat(), paint)
        }
    }

    // Handle touch events to interact with the view
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val eventaction = event.action
        val X = event.x.toInt()
        val Y = event.y.toInt()

        when (eventaction) {
            MotionEvent.ACTION_DOWN -> {
                // On touch down, initialize or select a ball
                if (points[0] == null) {
                    // Initialize the rectangle with default points
                    points[0] = Point(X, Y)
                    points[1] = Point(X, Y + 30)
                    points[2] = Point(X + 30, Y + 30)
                    points[3] = Point(X + 30, Y)

                    ballId = 2
                    groupId = 1
                    // Create color balls at each corner point
                    for (pt in points) {
                        draggableBalls.add(DraggableBall(context, R.drawable.ic_circle, pt))
                    }
                } else {
                    // Check if a ball is selected for dragging
                    ballId = -1
                    groupId = -1
                    for (i in draggableBalls.size - 1 downTo 0) {
                        val ball = draggableBalls[i]
                        // Calculate the center and radius for each ball
                        val centerX = ball.x + ball.widthOfBall
                        val centerY = ball.y + ball.heightOfBall
                        val radCircle = sqrt(
                            (((centerX - X) * (centerX - X)) + (centerY - Y)
                                    * (centerY - Y)).toDouble()
                        )

                        // Check if the touch is within the ball
                        if (radCircle < ball.widthOfBall) {
                            ballId = ball.id
                            groupId = if (ballId == 1 || ballId == 3) {
                                2
                            } else {
                                1
                            }
                            invalidate()
                            break
                        }
                        invalidate()
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> if (ballId > -1) {
                // Move the selected ball and update the rectangle
                draggableBalls[ballId].x = X
                draggableBalls[ballId].y = Y

                if (groupId == 1) {
                    draggableBalls[1].x = draggableBalls[0].x
                    draggableBalls[1].y = draggableBalls[2].y
                    draggableBalls[3].x = draggableBalls[2].x
                    draggableBalls[3].y = draggableBalls[0].y
                } else {
                    draggableBalls[0].x = draggableBalls[1].x
                    draggableBalls[0].y = draggableBalls[3].y
                    draggableBalls[2].x = draggableBalls[3].x
                    draggableBalls[2].y = draggableBalls[1].y
                }

                invalidate()
            }
        }
        invalidate() // Redraw the view
        return true
    }

    // Get the rectangle defined by the current corner points
    fun getRectangle(): Rect {
        val left = points.minOf { it?.x ?: 0 }
        val top = points.minOf { it?.y ?: 0 }
        val right = points.maxOf { it?.x ?: 0 }
        val bottom = points.maxOf { it?.y ?: 0 }

        return Rect(left, top, right, bottom)
    }

    // Class to represent a draggable ball on the canvas
    class DraggableBall(context: Context, resourceId: Int, point: Point?) {
        var bitmap: Bitmap
        var context: Context
        var point: Point?
        var id: Int

        init {
            this.id = count++
            bitmap = BitmapFactory.decodeResource(
                context.resources,
                resourceId
            )
            bitmap = Bitmap.createScaledBitmap(bitmap, 30, 30, false)
            this.context = context
            this.point = point
        }

        val widthOfBall: Int
            get() = bitmap.width

        val heightOfBall: Int
            get() = bitmap.height

        var x: Int
            get() = point!!.x
            set(x) {
                point!!.x = x
            }

        var y: Int
            get() = point!!.y
            set(y) {
                point!!.y = y
            }

        companion object {
            var count: Int = 0
        }
    }
}
