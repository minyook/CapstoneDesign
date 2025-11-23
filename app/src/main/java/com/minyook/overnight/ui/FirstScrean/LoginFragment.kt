package com.minyook.overnight.ui.FirstScrean

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.minyook.overnight.R
import com.minyook.overnight.databinding.FragmentLoginBinding
import com.minyook.overnight.ui.mainscrean.OvernightActivity

class LoginFragment : Fragment() {

    // 상수 정의
    private val PREFS_FILE_NAME = "OvernightAppPrefs"
    private val USER_UID_KEY = "user_uid"
    private val ACTION_TO_SIGN_UP = R.id.action_loginFragment_to_signUpFragment

    // ViewBinding
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    // Firebase 객체
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore // Firestore 객체

    // Google Login 관련
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Firebase 인스턴스 초기화
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Google 로그인 결과 콜백 등록
        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)!!
                    Log.d("Auth", "Google 계정 인증 성공: ${account.email}")
                    // Firebase 인증 및 DB 저장 로직 호출
                    firebaseAuthWithGoogle(account)
                } catch (e: ApiException) {
                    Log.w("Auth", "Google 로그인 실패", e)
                    Toast.makeText(requireContext(), "구글 로그인 실패", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w("Auth", "Google 로그인 취소됨")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        // 자동 로그인 체크
        checkLoginStatusAndNavigate()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Google 로그인 옵션 설정
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        setupClickListeners()
        setupSignUpPromptText()
    }

    // --- [핵심 기능] Google 로그인 후 Firestore 연동 ---
    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)

        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val uid = user?.uid ?: return@addOnCompleteListener

                    // Firestore 문서 참조 (컬렉션: user, 문서ID: uid)
                    val userDocRef = db.collection("user").document(uid)

                    // 1. DB에 이미 유저 정보가 있는지 확인
                    userDocRef.get().addOnSuccessListener { document ->
                        if (document.exists()) {
                            // [A] 이미 가입된 유저 -> 기존 정보 유지 (전화번호 등 덮어쓰기 방지)
                            Log.d("Firestore", "기존 회원 로그인: $uid")

                            saveUserUid(uid) // 세션 저장
                            Toast.makeText(requireContext(), "환영합니다! ${user.displayName}님", Toast.LENGTH_SHORT).show()
                            navigateToOvernightActivity()
                        } else {
                            // [B] 신규 유저 -> DB 형식에 맞춰 데이터 생성
                            val newUserInfo = hashMapOf(
                                "email" to (user.email ?: ""),
                                "name" to (user.displayName ?: "Google User"),
                                "user_docid" to uid,  // 사진 속 형식 유지
                                "birth" to "",        // 빈 값으로 필드 생성
                                "phone" to ""         // 빈 값으로 필드 생성
                            )

                            userDocRef.set(newUserInfo)
                                .addOnSuccessListener {
                                    Log.d("Firestore", "신규 구글 회원 DB 생성 완료")

                                    saveUserUid(uid) // 세션 저장
                                    Toast.makeText(requireContext(), "구글 계정으로 가입되었습니다.", Toast.LENGTH_SHORT).show()
                                    navigateToOvernightActivity()
                                }
                                .addOnFailureListener { e ->
                                    Log.e("Firestore", "DB 생성 실패", e)
                                    // DB 저장은 실패했더라도 로그인은 성공 처리 (필요 시 정책에 따라 변경)
                                    saveUserUid(uid)
                                    navigateToOvernightActivity()
                                }
                        }
                    }
                } else {
                    Log.w("Auth", "Firebase 인증 실패", task.exception)
                    Toast.makeText(requireContext(), "인증에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // --- 일반 이메일/비번 로그인 ---
    private fun performLogin() {
        val email = binding.etEmail.text?.toString()?.trim()
        val password = binding.etPassword.text?.toString()?.trim()

        if (email.isNullOrEmpty() || password.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "이메일과 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnLogin.isEnabled = false

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                binding.btnLogin.isEnabled = true
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val uid = user?.uid ?: return@addOnCompleteListener

                    saveUserUid(uid)
                    Toast.makeText(requireContext(), "로그인 성공", Toast.LENGTH_SHORT).show()
                    navigateToOvernightActivity()
                } else {
                    Toast.makeText(requireContext(), "로그인 실패: 정보를 확인해주세요.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // --- 유틸리티 함수들 ---

    private fun checkLoginStatusAndNavigate() {
        val sharedPrefs = requireActivity().getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        val userUid = sharedPrefs.getString(USER_UID_KEY, null)

        if (auth.currentUser != null && userUid != null) {
            navigateToOvernightActivity()
        }
    }

    private fun saveUserUid(uid: String) {
        val sharedPrefs = requireActivity().getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().putString(USER_UID_KEY, uid).apply()
    }

    private fun navigateToOvernightActivity() {
        val intent = Intent(requireContext(), OvernightActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener { performLogin() }

        binding.btnGoogleLogin.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        binding.btnMicrosoftLogin.setOnClickListener {
            Toast.makeText(requireContext(), "준비 중입니다.", Toast.LENGTH_SHORT).show()
        }

        binding.tvSignupPrompt.setOnClickListener {
            findNavController().navigate(ACTION_TO_SIGN_UP)
        }

        binding.tvForgotPassword.setOnClickListener {
            val intent = Intent(requireContext(), FindPasswordActivity::class.java)
            startActivity(intent)
        }
    }

    @Suppress("DEPRECATION")
    private fun setupSignUpPromptText() {
        val text = getString(R.string.prompt_signup)
        binding.tvSignupPrompt.text = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}