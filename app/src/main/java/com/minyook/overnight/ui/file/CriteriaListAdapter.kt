package com.minyook.overnight.ui.file

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.minyook.overnight.R
import com.minyook.overnight.data.model.CriterionResult

/**
 * 도넛 차트 아래에 항목별 이름과 점수를 수직으로 표시하는 리스트 어댑터.
 */
class CriteriaListAdapter(
    private val results: List<CriterionResult>,
    private val onItemClicked: (CriterionResult) -> Unit
) : RecyclerView.Adapter<CriteriaListAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCriterionName: TextView = itemView.findViewById(R.id.tv_criterion_name)
        val tvCriterionScore: TextView = itemView.findViewById(R.id.tv_criterion_score)

        fun bind(result: CriterionResult) {
            // ⭐️ 로컬에서 로드된 result 객체의 값 사용
            tvCriterionName.text = result.criterionName
            tvCriterionScore.text = "${result.actualScore} / ${result.maxScore}점"

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