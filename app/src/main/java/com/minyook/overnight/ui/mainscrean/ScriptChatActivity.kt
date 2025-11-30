package com.minyook.overnight.ui.mainscrean

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.view.ViewCompat // 필수 추가
import androidx.core.view.WindowCompat // 필수 추가
import androidx.core.view.WindowInsetsCompat // 필수 추가
import com.minyook.overnight.databinding.ActivityScriptChatBinding

class ScriptChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScriptChatBinding
    private lateinit var chatAdapter: ChatAdapter
    private val messageList = ArrayList<ChatMessage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScriptChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // [중요 1] 시스템 바(상태바, 내비게이션바) 뒤로 화면이 그려지도록 설정
        // (이걸 해야 상태바 색상을 투명하게 하거나 전체화면 느낌을 낼 수 있음)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // [중요 2] 키보드 및 시스템 바 높이 처리 (이게 핵심 해결책입니다!)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())

            // 상단 상태바 높이만큼 패딩 (헤더가 잘리지 않게)
            // 하단 내비게이션바 + 키보드 높이만큼 패딩 (입력창이 올라오게)
            view.setPadding(0, insets.top, 0, insets.bottom)

            WindowInsetsCompat.CONSUMED
        }

        // 기존 hideSystemUI() 호출은 삭제하세요! (충돌 원인)
        // hideSystemUI()

        initRecyclerView()
        initListener()
    }

    private fun initRecyclerView() {
        chatAdapter = ChatAdapter(messageList)
        binding.rvChatList.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@ScriptChatActivity).apply {
                stackFromEnd = true
            }

            // 키보드가 올라왔을 때 리스트가 가려지는 것을 방지하기 위한 스크롤 처리
            // (위의 setPadding이 작동하면 뷰 크기가 줄어들기 때문에 이 리스너가 정상 작동합니다)
            addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
                if (bottom < oldBottom) {
                    postDelayed({
                        if (messageList.isNotEmpty()) {
                            smoothScrollToPosition(messageList.size - 1)
                        }
                    }, 100)
                }
            }
        }

        addAiMessage("안녕하세요, Presenter님! \n오늘 발표 대본 작성을 도와드릴까요?")
    }

    private fun initListener() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSend.setOnClickListener {
            val userText = binding.etMessage.text.toString().trim()
            if (userText.isNotEmpty()) {
                sendMessage(userText)
                binding.etMessage.setText("")
                simulateAiResponse()
            }
        }

        // 포커스 잡았을 때 스크롤 내리기
        binding.etMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // 약간의 딜레이를 주어 키보드가 완전히 올라온 뒤 스크롤
                Handler(Looper.getMainLooper()).postDelayed({
                    scrollToBottom()
                }, 200)
            }
        }
    }

    // ... (sendMessage, addAiMessage, scrollToBottom, simulateAiResponse 등 나머지 함수는 그대로 유지)
    private fun sendMessage(message: String) {
        val chatMessage = ChatMessage(message, true)
        chatAdapter.addMessage(chatMessage)
        scrollToBottom()
    }

    private fun addAiMessage(message: String) {
        val chatMessage = ChatMessage(message, false)
        chatAdapter.addMessage(chatMessage)
        scrollToBottom()
    }

    private fun scrollToBottom() {
        if (messageList.isNotEmpty()) {
            binding.rvChatList.smoothScrollToPosition(messageList.size - 1)
        }
    }

    private fun simulateAiResponse() {
        Handler(Looper.getMainLooper()).postDelayed({
            addAiMessage("네, 입력하신 내용에 대한 피드백을 준비 중입니다.\n(지금은 테스트 응답입니다!)")
        }, 1000)
    }
}