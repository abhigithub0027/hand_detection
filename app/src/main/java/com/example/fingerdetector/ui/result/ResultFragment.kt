package com.example.fingerdetector.ui.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.fingerdetector.base.BaseActivity
import com.example.fingerdetector.data.repository.CaptureSessionRepository
import com.example.fingerdetector.databinding.FragmentResultBinding
import com.example.fingerdetector.ui.capture.CaptureViewModel
import com.example.fingerdetector.ui.home.HomeFragment
import java.util.Locale

class ResultFragment : Fragment() {

    private var _binding: FragmentResultBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CaptureViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? BaseActivity)?.configureToolbar("Capture Result", true)
        val snapshot = CaptureSessionRepository.snapshot()
        val palm = snapshot.palmRecord
        val fingers = snapshot.fingerRecords

        binding.summaryLabel.text = buildString {
            appendLine("Palm saved: ${palm?.fileName ?: "No"}")
            appendLine("Detected hand: ${palm?.handSide?.title ?: "Unknown"}")
            appendLine("Fingers saved: ${fingers.size}/5")
            if (fingers.isNotEmpty()) {
                appendLine("Matched fingers: ${fingers.joinToString { it.fingerType.title }}")
            }
        }

        binding.fileLabel.text = buildString {
            appendLine("Saved files")
            palm?.let { appendLine("Palm: ${it.fileName}") }
            fingers.forEach { appendLine("${it.fingerType.title}: ${it.fileName}") }
        }

        binding.telemetryLabel.text = buildString {
            appendLine("Telemetry")
            palm?.let {
                appendLine(
                    "Palm -> blur ${"%.2f".format(Locale.US, it.telemetry.blurScore)}, brightness ${"%.2f".format(Locale.US, it.telemetry.brightnessScore)}, focus ${it.telemetry.focusDistance ?: "n/a"}"
                )
            }
            fingers.forEach {
                appendLine(
                    "${it.fingerType.title} -> blur ${"%.2f".format(Locale.US, it.telemetry.blurScore)}, brightness ${"%.2f".format(Locale.US, it.telemetry.brightnessScore)}, focus ${it.telemetry.focusDistance ?: "n/a"}, match ${"%.2f".format(Locale.US, it.matchScore)}"
                )
            }
        }

        binding.restartButton.setOnClickListener {
            viewModel.resetSession()
            CaptureSessionRepository.reset()
            (activity as? BaseActivity)?.showRoot(HomeFragment())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
