package com.meenbeese.chronos.fragments

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope

import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap

import com.meenbeese.chronos.Chronos
import com.meenbeese.chronos.data.PreferenceData
import com.meenbeese.chronos.databinding.FragmentClockBinding
import com.meenbeese.chronos.interfaces.AlarmNavigator
import com.meenbeese.chronos.interfaces.ContextFragmentInstantiator
import com.meenbeese.chronos.utils.ImageUtils.isBitmapDark

import kotlinx.coroutines.launch

import java.util.TimeZone

class ClockFragment : BasePagerFragment() {
    private var _binding: FragmentClockBinding? = null
    private val binding get() = _binding!!

    private var timezone: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentClockBinding.inflate(inflater, container, false)

        if (arguments != null && requireArguments().containsKey(EXTRA_TIME_ZONE)) {
            timezone = arguments?.getString(EXTRA_TIME_ZONE)
            timezone?.let {
                binding.timeView.setTimezone(it)
                if (it != TimeZone.getDefault().id) {
                    binding.timezone.text = String.format(
                        "%s\n%s",
                        it.replace("_".toRegex(), " "),
                        TimeZone.getTimeZone(it).displayName
                    )
                }
            }
        }

        lifecycleScope.launch {
            val textColor = getContrastingTextColorFromBg()
            _binding?.timezone?.setTextColor(textColor)
        }

        binding.timeView.setOnClickListener {
            if (PreferenceData.SCROLL_TO_NEXT.getValue<Boolean>(requireContext())) {
                navigateToNearestAlarm()
            }
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun navigateToNearestAlarm() {
        val activity = requireActivity()
        val chronosApp = activity.application as Chronos
        val allAlarms = chronosApp.alarms

        val alarmsWithNextTrigger = allAlarms
            .filter { it.isEnabled }
            .mapNotNull { alarm -> alarm.getNext()?.timeInMillis?.let { alarm to it } }

        val targetAlarm = alarmsWithNextTrigger
            .minByOrNull { it.second }?.first
            ?: allAlarms
                .mapNotNull { alarm -> alarm.getNext()?.timeInMillis?.let { alarm to it } }
                .minByOrNull { it.second }
                ?.first
            ?: return

        val fragment = parentFragmentManager.fragments
            .filterIsInstance<AlarmNavigator>()
            .firstOrNull()

        fragment?.jumpToAlarm(targetAlarm.id, openEditor = true)
    }

    private suspend fun getContrastingTextColorFromBg(): Int {
        val backgroundImage = PreferenceData.BACKGROUND_IMAGE.getValue<String>(requireContext())

        return try {
            val imageRequest = ImageRequest.Builder(requireContext())
                .data(backgroundImage.toUri())
                .size(200, 200)
                .allowHardware(false)
                .build()

            val drawable = requireContext().imageLoader.execute(imageRequest).image

            val bitmap = drawable?.toBitmap()

            bitmap?.let {
                val isDark = isBitmapDark(it)
                if (isDark) Color.LTGRAY else Color.DKGRAY
            } ?: Color.DKGRAY
        } catch (_: Exception) {
            Color.DKGRAY
        }
    }

    override fun getTitle(context: Context?): String? {
        return timezone
    }

    class Instantiator(context: Context?, private val timezone: String?) :
        ContextFragmentInstantiator(
            context!!
        ) {
        override fun getTitle(context: Context?, position: Int): String? {
            return timezone
        }

        override fun newInstance(position: Int): BasePagerFragment {
            val args = Bundle()
            args.putString(EXTRA_TIME_ZONE, timezone)
            val fragment = ClockFragment()
            fragment.arguments = args
            return fragment
        }
    }

    companion object {
        const val EXTRA_TIME_ZONE = "com.meenbeese.chronos.fragments.ClockFragment.EXTRA_TIME_ZONE"
    }
}
