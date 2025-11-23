package com.minyook.overnight.ui.file

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.minyook.overnight.R
import com.minyook.overnight.data.model.CriterionResult

/**
 * 결과 화면의 리스트를 담당하는 어댑터
 * (디자인 변경: 기준명 -> 피드백 -> 점수 순서로 세로 배치)
 */
class CriteriaListAdapter(
    private val results: List<CriterionResult>,
    private val onItemClicked: (CriterionResult) -> Unit
) : RecyclerView.Adapter<CriteriaListAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCriterionName: TextView = itemView.findViewById(R.id.tv_criteria_title)
        val tvCriterionContent: TextView = itemView.findViewById(R.id.tv_criteria_content) // 피드백
        val tvCriterionScore: TextView = itemView.findViewById(R.id.tv_criteria_score)

        fun bind(result: CriterionResult) {
            // 1. 기준명
            tvCriterionName.text = result.criterionName

            // 2. 피드백 내용 (없으면 기본 문구)
            tvCriterionContent.text = if (result.feedback.isNotEmpty()) result.feedback else "피드백이 없습니다."

            // 3. 점수 (획득점수 / 만점)
            tvCriterionScore.text = "${result.actualScore} / ${result.maxScore}"

            itemView.setOnClickListener {
                onItemClicked(result)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_criteria_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(results[position])
    }

    override fun getItemCount(): Int = results.size
}