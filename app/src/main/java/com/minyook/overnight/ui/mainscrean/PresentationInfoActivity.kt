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
import com.google.firebase.firestore.FirebaseFirestore
import com.minyook.overnight.R
import com.minyook.overnight.ui.file.UploadActivity

class PresentationInfoActivity : AppCompatActivity(),
    FolderSelectionBottomSheet.OnFolderSelectedListener {

    private lateinit var itemsContainer: LinearLayout
    private lateinit var addItemButton: Button // (XML ID: addItemButton / MaterialButton)
    private lateinit var startButton: Button   // (XML ID: startButton / AppCompatButton)
    private lateinit var folderPathEditText: TextInputEditText
    private lateinit var etTeamInfo: TextInputEditText
    private lateinit var etTopicName: TextInputEditText

    private var itemCounter = 0

    // Firestore 관련 변수
    private lateinit var db: FirebaseFirestore
    private var selectedFolderId: String? = null // 선택된 폴더의 문서 ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_presentation_info)

        // 1. Firestore 초기화
        db = FirebaseFirestore.getInstance()

        // 2. 뷰 바인딩
        itemsContainer = findViewById(R.id.itemsContainer)
        addItemButton = findViewById(R.id.addItemButton)
        startButton = findViewById(R.id.startButton)
        folderPathEditText = findViewById(R.id.edittext_folder_path)

        // 팀 정보, 주제 입력창 바인딩 (XML ID 확인 필요: activity_presentation_info.xml 기준)
        etTeamInfo = findViewById(R.id.edittext_team_info)
        etTopicName = findViewById(R.id.edittext_topic_info)

        // 3. 폴더 선택 팝업
        folderPathEditText.setOnClickListener {
            val bottomSheet = FolderSelectionBottomSheet()
            bottomSheet.show(supportFragmentManager, FolderSelectionBottomSheet.TAG)
        }

        // 4. 기준 항목 추가 버튼
        addItemButton.setOnClickListener {
            if (itemsContainer.childCount < 5) {
                addNewItemCard()
            } else {
                Toast.makeText(this, "항목은 최대 5개까지 추가할 수 있습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. 저장 및 시작 버튼 -> Firestore 저장 로직 호출
        startButton.setOnClickListener {
            saveTopicToFirestore()
        }

        // 초기 항목 1개 추가
        addNewItemCard()
    }

    /**
     * 채점 기준 항목(Card)을 UI에 동적으로 추가
     */
    private fun addNewItemCard() {
        itemCounter++

        val inflater = LayoutInflater.from(this)
        val itemCardView = inflater.inflate(
            R.layout.item_criterion, // 보내주신 item_criterion.xml 사용
            itemsContainer,
            false
        )

        val itemNameEditText: TextInputEditText = itemCardView.findViewById(R.id.edittext_item_name)
        val deleteButton: ImageButton = itemCardView.findViewById(R.id.button_delete_item)

        itemNameEditText.setText("평가 항목 $itemCounter")

        deleteButton.setOnClickListener {
            itemsContainer.removeView(itemCardView)
            itemCounter--
        }

        itemsContainer.addView(itemCardView)
    }

    /**
     * FolderSelectionBottomSheet에서 폴더 선택 시 호출 (인터페이스 수정됨)
     */
    override fun onFolderSelected(folderId: String, folderName: String) {
        selectedFolderId = folderId
        folderPathEditText.setText(folderName) // 화면엔 이름만 표시
    }

    /**
     * 입력된 모든 정보를 Firestore 'topics' 컬렉션에 저장
     */
    private fun saveTopicToFirestore() {
        // 1. 유효성 검사: 폴더 선택 여부
        if (selectedFolderId == null) {
            Toast.makeText(this, "폴더(과목)를 먼저 선택해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val teamInfo = etTeamInfo.text.toString().trim()
        val topicName = etTopicName.text.toString().trim()

        if (teamInfo.isEmpty() || topicName.isEmpty()) {
            Toast.makeText(this, "팀 정보와 발표 주제를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. 채점 기준 리스트 수집
        val standardsList = mutableListOf<HashMap<String, Any>>()
        var totalScore = 0

        for (i in 0 until itemsContainer.childCount) {
            val view = itemsContainer.getChildAt(i)
            val etName = view.findViewById<TextInputEditText>(R.id.edittext_item_name)
            val etContent = view.findViewById<TextInputEditText>(R.id.edittext_item_content)
            val etScore = view.findViewById<TextInputEditText>(R.id.edittext_item_score)

            val name = etName.text.toString().trim()
            val detail = etContent.text.toString().trim()
            val scoreStr = etScore.text.toString().trim()

            if (name.isEmpty() || scoreStr.isEmpty()) {
                Toast.makeText(this, "모든 평가 항목의 이름과 배점을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return
            }

            val score = scoreStr.toIntOrNull() ?: 0
            totalScore += score

            // Standard 데이터 구조 생성
            val standardMap = hashMapOf <String, Any> (
                "standardName" to name,
                "standardDetail" to detail,
                "standardScore" to score
            )
            standardsList.add(standardMap)
        }

        if (totalScore != 100) {
            Toast.makeText(this, "배점의 총합은 100점이 되어야 합니다. (현재: ${totalScore}점)", Toast.LENGTH_LONG).show()
            return
        }

        // 3. 저장할 Topic 데이터 생성
        // 경로: contents/{contentId}/topics/{topicId}
        val topicData = hashMapOf(
            "contentId" to selectedFolderId,
            "topicName" to topicName,
            "teamInfo" to teamInfo,
            "standards" to standardsList,
            "createdAt" to com.google.firebase.Timestamp.now()
        )

        startButton.isEnabled = false // 중복 클릭 방지

        // 4. Firestore 저장
        db.collection("contents").document(selectedFolderId!!)
            .collection("topics")
            .add(topicData)
            .addOnSuccessListener { documentReference ->
                val newTopicId = documentReference.id
                Toast.makeText(this, "발표 주제가 저장되었습니다.", Toast.LENGTH_SHORT).show()

                // 5. 다음 화면(UploadActivity)으로 이동하면서 ID 전달
                val intent = Intent(this, UploadActivity::class.java)
                intent.putExtra("contentId", selectedFolderId)
                intent.putExtra("topicId", newTopicId) // ⭐ 방금 만든 주제 ID 전달
                startActivity(intent)
                finish() // 뒤로가기 시 다시 입력창 안 나오게
            }
            .addOnFailureListener { e ->
                startButton.isEnabled = true
                Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}