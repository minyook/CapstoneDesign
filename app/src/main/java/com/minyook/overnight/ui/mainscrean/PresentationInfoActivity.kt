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
import com.minyook.overnight.R
import com.minyook.overnight.ui.file.UploadActivity

// 🔴 [수정] FolderSelectionBottomSheet.OnFolderSelectedListener 인터페이스 구현
class PresentationInfoActivity : AppCompatActivity(),
    FolderSelectionBottomSheet.OnFolderSelectedListener {

    // 1. 뷰들을 나중에 참조할 수 있게 클래스 멤버로 선언
    private lateinit var itemsContainer: LinearLayout
    private lateinit var addItemButton: Button
    private lateinit var startButton: Button
    private lateinit var folderPathEditText: TextInputEditText // 👈 [추가] 폴더 경로 EditText

    // 2. 추가된 항목의 개수를 세는 카운터
    private var itemCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_presentation_info)

        // 3. 뷰 초기화
        itemsContainer = findViewById(R.id.itemsContainer)
        addItemButton = findViewById(R.id.addItemButton)
        startButton = findViewById(R.id.startButton)

        // 🔴 [수정] 폴더 경로 EditText 초기화 및 클릭 리스너 설정 🔴
        folderPathEditText = findViewById(R.id.edittext_folder_path)
        folderPathEditText.setOnClickListener {
            // 폴더 경로 입력란 클릭 시 BottomSheet 팝업창 띄우기
            val bottomSheet = FolderSelectionBottomSheet()
            bottomSheet.show(supportFragmentManager, FolderSelectionBottomSheet.TAG)
        }

        // 4. '+ 항목 추가' 버튼 클릭 리스너 설정
        addItemButton.setOnClickListener {
            // 5개 제한 로직
            if (itemsContainer.childCount < 5) {
                addNewItemCard()
            } else {
                Toast.makeText(this, "항목은 최대 5개까지 추가할 수 있습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // '발표 시작하기' 버튼 클릭 리스너 설정
        startButton.setOnClickListener {
            val intent = Intent(this, UploadActivity::class.java)
            startActivity(intent)
        }

        // 5. 화면이 처음 열릴 때 기본으로 항목 1개를 추가
        addNewItemCard()
    }

    /**
     * 6. 새 항목 카드를 itemsContainer에 추가하는 함수
     */
    private fun addNewItemCard() {
        itemCounter++

        // 8. LayoutInflater를 사용해 item_criterion.xml을 뷰 객체로 만듦
        val inflater = LayoutInflater.from(this)
        val itemCardView = inflater.inflate(
            R.layout.item_criterion, // 재사용할 카드 레이아웃
            itemsContainer,     // 이 뷰의 부모가 될 컨테이너
            false         // 지금 바로 붙이지 않음 (addView로 붙일 것)
        )

        // 9. 카드 뷰 내부의 UI 요소들을 찾음
        val itemNameEditText: TextInputEditText = itemCardView.findViewById(R.id.edittext_item_name)
        val deleteButton: ImageButton = itemCardView.findViewById(R.id.button_delete_item)

        // 10. 새 항목의 기본 텍스트 설정
        itemNameEditText.setText("항목 $itemCounter")

        // 11. 삭제(X) 버튼 클릭 리스너 설정
        deleteButton.setOnClickListener {
            // itemsContainer에서 이 카드 뷰(itemCardView)를 제거
            itemsContainer.removeView(itemCardView)
        }

        // 12. 완성된 카드 뷰를 컨테이너(LinearLayout)에 추가
        itemsContainer.addView(itemCardView)
    }

    // 🔴 [신규] OnFolderSelectedListener 인터페이스 구현 함수 🔴
    /**
     * FolderSelectionBottomSheet에서 폴더를 선택하면 호출되는 콜백 함수
     */
    override fun onFolderSelected(path: String) {
        // 선택된 경로를 EditText에 업데이트
        folderPathEditText.setText(path)
        Toast.makeText(this, "경로 설정: $path", Toast.LENGTH_SHORT).show()
    }
}