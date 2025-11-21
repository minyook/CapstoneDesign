package com.minyook.overnight.ui.mainscrean

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson // 👈 Gson 임포트 필요
import com.minyook.overnight.R
import com.minyook.overnight.ui.file.UploadActivity

// 🔴 PresentationInfoActivity 내부 클래스로 정의되었던 데이터 클래스를 다시 정의 (공통 사용)
data class PresentationInfo(
    val title: String,
    val date: String = "", // 날짜는 저장 시 추가할 수 있습니다.
    val folderPath: String,
    val criteria: List<String>
)

class PresentationInfoActivity : AppCompatActivity(),
    FolderSelectionBottomSheet.OnFolderSelectedListener {

    private lateinit var itemsContainer: LinearLayout
    private lateinit var addItemButton: Button
    private lateinit var startButton: Button
    private lateinit var folderPathEditText: TextInputEditText

    private var itemCounter = 0
    private val PREFS_NAME = "AnalysisPrefs"
    private val KEY_PRESENTATION_INFO = "presentation_info_json"
    private val gson = Gson() // 👈 Gson 객체 초기화

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_presentation_info)

        itemsContainer = findViewById(R.id.itemsContainer)
        addItemButton = findViewById(R.id.addItemButton)
        startButton = findViewById(R.id.startButton)
        folderPathEditText = findViewById(R.id.edittext_folder_path)

        folderPathEditText.setOnClickListener {
            val bottomSheet = FolderSelectionBottomSheet()
            bottomSheet.show(supportFragmentManager, FolderSelectionBottomSheet.TAG)
        }

        addItemButton.setOnClickListener {
            if (itemsContainer.childCount < 5) {
                addNewItemCard()
            } else {
                Toast.makeText(this, "항목은 최대 5개까지 추가할 수 있습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // 🔴 '발표 시작하기' 버튼 클릭 리스너 수정: 데이터 저장 로직 추가 🔴
        startButton.setOnClickListener {
            if (savePresentationInfoData()) { // 👈 데이터 저장 성공 시에만 이동
                val intent = Intent(this, UploadActivity::class.java)
                startActivity(intent)
            }
        }

        addNewItemCard()
    }

    /**
     * 6. 새 항목 카드를 itemsContainer에 추가하는 함수 (동일)
     */
    private fun addNewItemCard() {
        itemCounter++

        val inflater = LayoutInflater.from(this)
        val itemCardView = inflater.inflate(
            R.layout.item_criterion,
            itemsContainer,
            false
        )

        val itemNameEditText: TextInputEditText = itemCardView.findViewById(R.id.edittext_item_name)
        val deleteButton: ImageButton = itemCardView.findViewById(R.id.button_delete_item)

        itemNameEditText.setText("항목 $itemCounter")

        deleteButton.setOnClickListener {
            itemsContainer.removeView(itemCardView)
        }

        itemsContainer.addView(itemCardView)
    }

    /**
     * FolderSelectionBottomSheet에서 폴더를 선택하면 호출되는 콜백 함수 (동일)
     */
    override fun onFolderSelected(path: String) {
        folderPathEditText.setText(path)
        Toast.makeText(this, "경로 설정: $path", Toast.LENGTH_SHORT).show()
    }

    // -----------------------------------------------------------------
    // 💾 신규: 입력된 데이터를 수집하여 로컬에 저장하는 핵심 함수
    // -----------------------------------------------------------------

    private fun savePresentationInfoData(): Boolean {
        // 1. 폴더 경로 확인 (필수 입력값)
        val folderPath = folderPathEditText.text?.toString()
        if (folderPath.isNullOrBlank()) {
            Toast.makeText(this, "폴더 경로를 반드시 설정해야 합니다.", Toast.LENGTH_LONG).show()
            return false
        }

        // 2. 발표 기준 항목 수집
        val criteriaList = mutableListOf<String>()
        var allCriteriaValid = true

        for (i in 0 until itemsContainer.childCount) {
            val itemCardView = itemsContainer.getChildAt(i)
            val nameEditText: TextInputEditText = itemCardView.findViewById(R.id.edittext_item_name)
            val itemName = nameEditText.text?.toString()

            if (itemName.isNullOrBlank()) {
                Toast.makeText(this, "항목 이름을 모두 채워주세요.", Toast.LENGTH_LONG).show()
                allCriteriaValid = false
                break
            }
            criteriaList.add(itemName)
        }

        if (!allCriteriaValid) return false

        // 3. PresentationInfo 객체 생성
        val presentationInfo = PresentationInfo(
            title = "발표 제목 (미구현)", // TODO: 발표 제목 입력 필드가 있다면 해당 값으로 대체
            folderPath = folderPath,
            criteria = criteriaList
        )

        // 4. SharedPreferences에 JSON 문자열로 저장
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val jsonString = gson.toJson(presentationInfo)
        prefs.edit().putString(KEY_PRESENTATION_INFO, jsonString).apply()

        Toast.makeText(this, "발표 기준이 로컬에 저장되었습니다.", Toast.LENGTH_SHORT).show()
        return true
    }
}