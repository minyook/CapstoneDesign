package com.minyook.overnight.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.minyook.overnight.data.model.CriterionResult

class MultiSegmentDonutChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var criteriaList: List<CriterionResult> = emptyList()
    private var totalMaxScore: Float = 100f

    // 🎨 색상 팔레트 (파랑, 노랑, 초록, 빨강, 보라 - 예시 이미지 참고)
    private val segmentColors = listOf(
        Color.parseColor("#4F6EF3"), // 파랑
        Color.parseColor("#F59E0B"), // 노랑
        Color.parseColor("#10B981"), // 초록
        Color.parseColor("#EF4444"), // 빨강
        Color.parseColor("#8B5CF6")  // 보라
    )
    private val emptyColor = Color.parseColor("#E0E0E0") // 빈 영역(회색)

    // 🖌️ 페인트 설정
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT // 끝부분을 딱 잘라냄 (도넛 조각 연결 위해)
    }

    private val rectF = RectF() // 그릴 영역
    private val strokeWidth = 50f // 도넛 두께
    private val gapAngle = 2f   // 조각 사이의 흰색 간격 (도)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (criteriaList.isEmpty()) return

        val width = width.toFloat()
        val height = height.toFloat()
        val radius = (Math.min(width, height) - strokeWidth) / 2f

        // 중앙 정렬을 위한 사각형 영역 설정
        rectF.set(
            (width / 2f) - radius,
            (height / 2f) - radius,
            (width / 2f) + radius,
            (height / 2f) + radius
        )

        paint.strokeWidth = strokeWidth

        // 12시 방향(-90도)부터 그리기 시작
        var currentAngle = -90f

        criteriaList.forEachIndexed { index, item ->
            // 1. 이 항목이 차지하는 전체 각도 (배점 비율)
            // (항목 배점 / 전체 만점) * 360도
            val segmentSweepAngle = (item.maxScore / totalMaxScore) * 360f

            // 간격을 뺀 실제 그릴 각도
            val drawSweepAngle = Math.max(0f, segmentSweepAngle - gapAngle)

            // 2. [배경] 회색 아크 그리기 (해당 항목의 만점 영역)
            paint.color = emptyColor
            canvas.drawArc(rectF, currentAngle, drawSweepAngle, false, paint)

            // 3. [전경] 실제 점수 아크 그리기 (색상 채움)
            // (획득 점수 / 만점) 비율만큼만 채움
            val scoreRatio = item.actualScore.toFloat() / item.maxScore.toFloat()
            val fillSweepAngle = drawSweepAngle * scoreRatio

            if (fillSweepAngle > 0) {
                paint.color = segmentColors[index % segmentColors.size]
                canvas.drawArc(rectF, currentAngle, fillSweepAngle, false, paint)
            }

            // 다음 조각 시작 위치로 이동 (간격 포함)
            currentAngle += segmentSweepAngle
        }
    }

    // 데이터 설정 함수
    fun setCriteria(list: List<CriterionResult>) {
        this.criteriaList = list
        // 전체 만점 계산 (보통 100점이지만, 유동적으로 계산)
        this.totalMaxScore = list.sumOf { it.maxScore }.toFloat()
        if (this.totalMaxScore == 0f) this.totalMaxScore = 100f // 0 나누기 방지

        invalidate() // 다시 그리기 요청 (onDraw 호출)
    }
}