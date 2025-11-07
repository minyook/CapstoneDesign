package com.minyook.overnight.ui // 패키지 com.minyook.test.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.minyook.overnight.R // R 파일 경로는 com.minyook.test.R
import kotlin.math.sin

class AnimatedWaveView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val paint1 = Paint(Paint.ANTI_ALIAS_FLAG) // 뒷쪽 웨이브 (연함)
    private val paint2 = Paint(Paint.ANTI_ALIAS_FLAG) // 앞쪽 웨이브 (진함)
    private val path1 = Path()
    private val path2 = Path()

    // --- 웨이브 속성 수정 ---
    // 웨이브 높이 (진폭)
    private val waveAmplitude = 60f
    // 웨이브 밀도 (주파수) - 값을 낮춰서 물결을 넓게 만듦
    private val waveFrequency = 0.005f
    // --- ---

    // 애니메이션 오프셋
    private var offset1 = 0f
    private var offset2 = 0f

    private var waveAnimator: ValueAnimator? = null

    init {
        // R 파일에서 색상 가져오기
        paint1.color = ContextCompat.getColor(context, R.color.light_purple_40)
        paint2.color = ContextCompat.getColor(context, R.color.light_purple_70)

        paint1.style = Paint.Style.FILL
        paint2.style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // 1. 뒷쪽 웨이브 (연한색)
        path1.reset()
        path1.moveTo(0f, 0f)
        path1.lineTo(w, 0f)
        path1.lineTo(w, h * 0.7f)

        for (x in w.toInt() downTo 0) {
            val y = (h * 0.7f) + waveAmplitude * sin((x * waveFrequency) + offset1)
            path1.lineTo(x.toFloat(), y.toFloat())
        }
        path1.close()
        canvas.drawPath(path1, paint1)


        // 2. 앞쪽 웨이브 (진한색)
        path2.reset()
        path2.moveTo(0f, 0f)
        path2.lineTo(w, 0f)
        path2.lineTo(w, h * 0.7f)

        for (x in w.toInt() downTo 0) {
            val y = (h * 0.7f) + waveAmplitude * sin((x * waveFrequency * 1.2f) + offset2)
            path2.lineTo(x.toFloat(), y.toFloat())
        }
        path2.close()
        canvas.drawPath(path2, paint2)
    }

    private fun startAnimation() {
        waveAnimator?.cancel()
        waveAnimator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
            // --- 애니메이션 속도 수정 ---
            // 시간을 5초로 늘려 더 천천히 움직이게 함
            duration = 12000
            // --- ---

            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float

                // --- 웨이브 속도 차이 수정 ---
                offset1 = progress
                // 두 웨이브의 속도 차이를 줄여 더 자연스럽게 함
                offset2 = progress * 0.8f + 1.2f
                // --- ---

                invalidate()
            }
            start()
        }
    }

    // 뷰가 화면에 보일 때 애니메이션 시작
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    // 뷰가 화면에서 사라질 때 애니메이션 정지 (메모리 누수 방지)
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        waveAnimator?.cancel()
    }
}