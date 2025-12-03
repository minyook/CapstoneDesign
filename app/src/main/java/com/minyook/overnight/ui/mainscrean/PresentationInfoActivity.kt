package com.minyook.overnight.ui.mainscrean

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.minyook.overnight.R
import com.minyook.overnight.ui.file.UploadActivity

class PresentationInfoActivity : AppCompatActivity(),
    FolderSelectionBottomSheet.OnFolderSelectedListener {

    private lateinit var scrollView: NestedScrollView
    private lateinit var itemsContainer: LinearLayout
    private lateinit var addItemButton: Button
    private lateinit var startButton: Button
    private lateinit var folderPathEditText: TextInputEditText
    private lateinit var etTeamInfo: TextInputEditText
    private lateinit var etTopicName: TextInputEditText

    private var itemCounter = 0

    // Firebase 관련
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth // ★ 인증 객체 추가

    private var selectedFolderId: String? = null
    private var currentUserUid: String? = null // ★ UID 변수

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_presentation_info)

        // 내비게이션 바 숨김
        hideSystemUI()

        // ★ [핵심] 키보드가 올라올 때 뷰가 가려지지 않도록 패딩을 주는 리스너
        // 전체 화면 모드(hideSystemUI)를 쓸 때는 adjustResize가 잘 안 먹힐 수 있어서 이 코드가 필수적입니다.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.setPadding(0, 0, 0, if (imeVisible) imeHeight else 0)
            insets
        }

        // 1. Firebase 초기화
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // ★ 현재 로그인한 사용자 UID 가져오기
        currentUserUid = auth.currentUser?.uid
        if (currentUserUid == null) {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 2. 뷰 바인딩
        scrollView = findViewById(R.id.scrollView)
        itemsContainer = findViewById(R.id.itemsContainer)
        addItemButton = findViewById(R.id.addItemButton)
        startButton = findViewById(R.id.startButton)
        folderPathEditText = findViewById(R.id.edittext_folder_path)
        etTeamInfo = findViewById(R.id.edittext_team_info)
        etTopicName = findViewById(R.id.edittext_topic_info)

        val btnBack = findViewById<android.widget.ImageButton>(R.id.btn_back)
        btnBack.setOnClickListener { finish() }

        // 3. 폴더 선택 팝업 (UID 전달)
        folderPathEditText.setOnClickListener {
            // 내 UID를 넘겨서 내 폴더만 가져오게 함
            val bottomSheet = FolderSelectionBottomSheet.newInstance(currentUserUid!!)
            bottomSheet.show(supportFragmentManager, FolderSelectionBottomSheet.TAG)
        }

        // 4. 항목 추가 버튼
        addItemButton.setOnClickListener {
            if (itemsContainer.childCount < 5) {
                addNewItemCard()
            } else {
                Toast.makeText(this, "항목은 최대 5개까지 추가할 수 있습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. 저장 버튼
        startButton.setOnClickListener {
            saveTopicToFirestore()
        }

        // 초기 항목 1개 추가
        addNewItemCard()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    private fun hideSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun addNewItemCard() {
        itemCounter++
        val inflater = LayoutInflater.from(this)
        val itemCardView = inflater.inflate(R.layout.item_criterion, itemsContainer, false)
        val itemNameEditText: TextInputEditText = itemCardView.findViewById(R.id.edittext_item_name)
        val deleteButton: android.widget.ImageButton = itemCardView.findViewById(R.id.button_delete_item)

        itemNameEditText.setText("평가 항목 $itemCounter")

        deleteButton.setOnClickListener {
            itemsContainer.removeView(itemCardView)
            itemCounter--
        }
        itemsContainer.addView(itemCardView)

        scrollView.post {
            scrollView.fullScroll(NestedScrollView.FOCUS_DOWN)
            // 추가된 항목의 입력창에 바로 포커스 주기 (사용자 편의)
            itemNameEditText.requestFocus()
        }
    }

    // 폴더 선택 완료 시 호출
    override fun onFolderSelected(folderId: String, folderName: String) {
        selectedFolderId = folderId
        folderPathEditText.setText(folderName)
    }

    // ★ [핵심] Firestore 저장 로직 수정
    private fun saveTopicToFirestore() {
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

            val standardMap = hashMapOf<String, Any>(
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

        // 저장할 데이터
        val topicData = hashMapOf(
            "folderId" to selectedFolderId,
            "topicName" to topicName,
            "teamInfo" to teamInfo,
            "standards" to standardsList,
            "createdAt" to com.google.firebase.Timestamp.now()
        )

        startButton.isEnabled = false

        // ★ [경로 수정] user -> {UID} -> folders -> {FolderID} -> topics -> {NewID}
        // 사진에 있는 경로 구조를 정확히 따릅니다.
        db.collection("user").document(currentUserUid!!)
            .collection("folders").document(selectedFolderId!!)
            .collection("topics") // 하위에 topics 컬렉션을 만들어 저장
            .add(topicData)
            .addOnSuccessListener { documentReference ->
                val newTopicId = documentReference.id
                Toast.makeText(this, "발표 주제가 저장되었습니다.", Toast.LENGTH_SHORT).show()

                // 다음 화면으로 데이터 전달 (경로가 바뀌었으므로 userId도 함께 전달)
                val intent = Intent(this, UploadActivity::class.java)
                intent.putExtra("userId", currentUserUid)
                intent.putExtra("folderId", selectedFolderId)
                intent.putExtra("topicId", newTopicId)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                startButton.isEnabled = true
                Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}