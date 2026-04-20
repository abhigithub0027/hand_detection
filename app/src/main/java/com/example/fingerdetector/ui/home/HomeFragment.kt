package com.example.fingerdetector.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.fingerdetector.base.BaseActivity
import com.example.fingerdetector.databinding.FragmentHomeBinding
import com.example.fingerdetector.ui.capture.CaptureFragment
import com.example.fingerdetector.ui.capture.CaptureViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CaptureViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? BaseActivity)?.configureToolbar("Finger Detector", false)
        binding.checklistText.text = """
            • Base activity with fragment navigation and runtime permissions
            • CameraX custom camera with palm overlay and finger oval overlay
            • MediaPipe hand landmarks for hand side, finger count and simulated minutiae
            • Lighting classification, exposure adjustment and blur validation
            • External storage export in the requested “Finger Data” folder
        """.trimIndent()

        binding.startButton.setOnClickListener {
            viewModel.resetSession()
            (activity as? BaseActivity)?.navigate(CaptureFragment())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
