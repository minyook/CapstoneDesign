package com.minyook.overnight.ui.file

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.minyook.overnight.R

class UploadOptionsBottomSheet : BottomSheetDialogFragment() {

    // 액티비티와 통신하기 위한 인터페이스
    interface UploadOptionListener {
        fun onOptionSelected(option: UploadOption)
    }

    enum class UploadOption { GALLERY, FILES, DRIVE }

    private var listener: UploadOptionListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // 부모 액티비티가 리스너를 구현했는지 확인
        listener = context as? UploadOptionListener
            ?: throw ClassCastException("$context must implement UploadOptionListener")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_upload_options, container, false)

        view.findViewById<TextView>(R.id.tv_option_gallery).setOnClickListener {
            listener?.onOptionSelected(UploadOption.GALLERY)
            dismiss()
        }

        view.findViewById<TextView>(R.id.tv_option_files).setOnClickListener {
            listener?.onOptionSelected(UploadOption.FILES)
            dismiss()
        }

        view.findViewById<TextView>(R.id.tv_option_drive).setOnClickListener {
            listener?.onOptionSelected(UploadOption.DRIVE)
            dismiss()
        }

        return view
    }
}