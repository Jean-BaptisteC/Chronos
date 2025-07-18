package com.meenbeese.chronos.fragments

import android.app.AlarmManager
import android.content.Context
import android.os.Bundle
import android.provider.AlarmClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.meenbeese.chronos.R
import com.meenbeese.chronos.adapters.SimplePagerAdapter
import com.meenbeese.chronos.data.Preferences
import com.meenbeese.chronos.dialogs.TimerFactoryDialog
import com.meenbeese.chronos.BuildConfig
import com.meenbeese.chronos.Chronos
import com.meenbeese.chronos.data.AlarmData
import com.meenbeese.chronos.data.toEntity
import com.meenbeese.chronos.databinding.FragmentHomeBinding
import com.meenbeese.chronos.db.AlarmViewModel
import com.meenbeese.chronos.db.AlarmViewModelFactory
import com.meenbeese.chronos.dialogs.TimeChooserDialog
import com.meenbeese.chronos.ext.getFlow
import com.meenbeese.chronos.interfaces.AlarmNavigator
import com.meenbeese.chronos.screens.ClockScreen
import com.meenbeese.chronos.services.TimerService
import com.meenbeese.chronos.utils.FormatUtils
import com.meenbeese.chronos.utils.ImageUtils.getContrastingTextColorFromBg
import com.meenbeese.chronos.utils.ImageUtils.rememberBackgroundPainterState
import com.meenbeese.chronos.views.AnimatedFabMenu
import com.meenbeese.chronos.views.ClockPageView
import com.meenbeese.chronos.views.CustomTabView
import com.meenbeese.chronos.views.FabItem

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import kotlin.math.abs

import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class HomeFragment : BaseFragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val isBottomSheetExpanded = mutableStateOf(false)
    private lateinit var behavior: BottomSheetBehavior<*>
    private lateinit var alarmViewModel: AlarmViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        val app = requireActivity().application as Chronos
        val factory = AlarmViewModelFactory(app.repository)
        alarmViewModel = ViewModelProvider(this, factory)[AlarmViewModel::class.java]
        alarmViewModel.alarms.observe(viewLifecycleOwner) { alarms ->
            Log.d("HomeFragment", "Alarms updated, size: ${alarms.size}")
            if (alarms.isEmpty() && behavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        val tabView = binding.root.findViewById<ComposeView>(R.id.tabLayoutCompose)
        val tabs = listOf(getString(R.string.title_alarms), getString(R.string.title_settings))
        val selectedTabIndex = mutableIntStateOf(0)

        tabView.setContent {
            val expanded by isBottomSheetExpanded

            val modifier = if (expanded) {
                Modifier.padding(WindowInsets.statusBars.asPaddingValues())
            } else {
                Modifier
            }

            CustomTabView(
                tabs = tabs,
                selectedTabIndex = selectedTabIndex.intValue,
                onTabSelected = { index ->
                    selectedTabIndex.intValue = index
                    binding.viewPager.currentItem = index

                    if (index == 0) {
                        behavior.isDraggable = true
                        behavior.peekHeight = binding.bottomSheet.measuredHeight / 2
                        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    } else {
                        behavior.isDraggable = false
                        behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    }
                },
                modifier = modifier
            )
        }

        behavior = BottomSheetBehavior.from(binding.bottomSheet)
        behavior.isHideable = false
        behavior.addBottomSheetCallback(object : BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                isBottomSheetExpanded.value = newState == BottomSheetBehavior.STATE_EXPANDED
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        val pagerAdapter = SimplePagerAdapter(
            this,
            AlarmsFragment.Instantiator(context),
            SettingsFragment.Instantiator(context)
        )

        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                selectedTabIndex.intValue = position

                if (position == 0) {
                    binding.fabMenuCompose.apply {
                        visibility = View.VISIBLE
                        disposeComposition()
                        post { setupSpeedDial() }
                    }
                    behavior.isDraggable = true
                    behavior.peekHeight = binding.bottomSheet.measuredHeight / 2
                    behavior.state = BottomSheetBehavior.STATE_COLLAPSED
                } else {
                    binding.fabMenuCompose.apply {
                        disposeComposition()
                        visibility = View.INVISIBLE
                    }
                    behavior.isDraggable = false
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
        })
        binding.viewPager.post {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                attachScrollListenerToAlarms()
            }
        }

        setupSpeedDial()

        setClockFragments()

        handleIntentActions()

        return binding.root
    }

    private fun setupSpeedDial() {
        binding.fabMenuCompose.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnLifecycleDestroyed(viewLifecycleOwner)
        )
        binding.fabMenuCompose.setContent {
            val timerItem = FabItem(icon = R.drawable.ic_timer, text = R.string.title_set_timer)
            val watchItem = FabItem(icon = R.drawable.ic_stopwatch, text = R.string.title_set_stopwatch)
            val alarmItem = FabItem(icon = R.drawable.ic_alarm_add, text = R.string.title_set_alarm)

            AnimatedFabMenu(
                icon = R.drawable.ic_add,
                text = R.string.title_create,
                items = listOf(
                    timerItem,
                    watchItem,
                    alarmItem
                ),
                onItemClick = { fabItem ->
                    when (fabItem) {
                        timerItem -> invokeTimerScheduler()
                        watchItem -> invokeWatchScheduler()
                        alarmItem -> invokeAlarmScheduler()
                    }
                }
            )
        }
        binding.fabMenuCompose.post {
            binding.fabMenuCompose.bringToFront()
            binding.fabMenuCompose.elevation = 20f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()

        if (binding.viewPager.currentItem == 1) {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
        } else {
            behavior.isDraggable = true
        }
    }

    private fun attachScrollListenerToAlarms() {
        val alarmsFragment = childFragmentManager.fragments
            .filterIsInstance<AlarmsFragment>()
            .firstOrNull()
        val recyclerView = alarmsFragment?.recyclerView ?: return

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var lastStateChangeTime = 0L
            private val debounceInterval = 300L

            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val currentTime = System.currentTimeMillis()
                val threshold = 10

                if (abs(dy) < threshold) return

                if (dy > 0 && behavior.state != BottomSheetBehavior.STATE_EXPANDED && rv.canScrollVertically(1).not()) {
                    if (currentTime - lastStateChangeTime > debounceInterval) {
                        behavior.state = BottomSheetBehavior.STATE_EXPANDED
                        lastStateChangeTime = currentTime
                    }
                } else if (dy < 0 && behavior.state != BottomSheetBehavior.STATE_COLLAPSED && rv.canScrollVertically(-1).not()) {
                    if (currentTime - lastStateChangeTime > debounceInterval) {
                        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
                        lastStateChangeTime = currentTime
                    }
                }
            }
        })
    }

    /**
     * Check actions passed from MainActivity; open timer/alarm
     * schedulers if necessary.
     */
    private fun handleIntentActions() {
        val action = arguments?.getString(INTENT_ACTION)
        when (action) {
            AlarmClock.ACTION_SET_ALARM -> binding.root.post { invokeAlarmScheduler() }
            AlarmClock.ACTION_SET_TIMER -> binding.root.post { invokeTimerScheduler() }
        }
    }

    /**
     * Open the alarm scheduler dialog to allow the user to create
     * a new alarm.
     */
    private fun invokeAlarmScheduler() {
        val calendar = Calendar.getInstance()
        val hourNow = calendar.get(Calendar.HOUR_OF_DAY)
        val minuteNow = calendar.get(Calendar.MINUTE)

        val composeView = binding.root.findViewById<ComposeView>(R.id.composeDialogHost)

        composeView.disposeComposition()
        composeView.setContent {
            var showDialog by remember { mutableStateOf(true) }

            if (showDialog) {
                TimeChooserDialog(
                    initialHour = hourNow,
                    initialMinute = minuteNow,
                    is24HourClock = Preferences.MILITARY_TIME.get(requireContext()),
                    onDismissRequest = { showDialog = false },
                    onTimeSet = { hour, minute ->
                        showDialog = false

                        val time = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)

                            if (BuildConfig.DEBUG) {
                                add(Calendar.MINUTE, 1)
                            }
                        }.timeInMillis

                        val alarm = AlarmData(
                            id = 0,
                            name = null,
                            time = Calendar.getInstance().apply { timeInMillis = time },
                            isEnabled = true,
                            days = MutableList(7) { false }, // All days off initially
                            isVibrate = true,
                            sound = null
                        )

                        CoroutineScope(Dispatchers.IO).launch {
                            val entity = alarm.toEntity()
                            val id = alarmViewModel.insertAndReturnId(entity)

                            alarm.id = id.toInt()
                            alarm.set(requireContext())
                        }

                        val formattedTime = FormatUtils.formatShort(context, Date(time))
                        Toast.makeText(requireContext(), "Alarm set for $formattedTime", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    private fun invokeWatchScheduler() {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_up_sheet,
                R.anim.slide_out_up_sheet,
                R.anim.slide_in_down_sheet,
                R.anim.slide_out_down_sheet
            )
            .replace(R.id.fragment, StopwatchFragment())
            .addToBackStack(null)
            .commit()
    }

    /**
     * Open the timer scheduler dialog to allow the user to start
     * a timer.
     */
    private fun invokeTimerScheduler() {
        val context = requireContext()
        val manager = parentFragmentManager
        val chronos = context.applicationContext as Chronos
        val composeView = binding.root.findViewById<ComposeView>(R.id.composeDialogHost2)

        composeView.disposeComposition()
        composeView.setContent {
            var showDialog by remember { mutableStateOf(true) }

            if (showDialog) {
                TimerFactoryDialog(
                    onDismiss = { showDialog = false },
                    onTimeChosen = { hours, minutes, seconds, ringtone, isVibrate ->
                        showDialog = false

                        val totalMillis = ((hours * 3600) + (minutes * 60) + seconds) * 1000L

                        if (totalMillis <= 0) {
                            Toast.makeText(requireContext(), "Invalid timer duration", Toast.LENGTH_SHORT).show()
                            return@TimerFactoryDialog
                        }

                        val timer = chronos.newTimer()
                        timer.setDuration(totalMillis, chronos)
                        timer.setVibrate(context, isVibrate)
                        timer.setSound(context, ringtone)
                        timer[chronos] = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                        TimerService.startService(context)

                        val args = Bundle().apply {
                            putParcelable(TimerFragment.EXTRA_TIMER, timer)
                        }

                        val fragment = TimerFragment().apply {
                            arguments = args
                        }

                        manager.beginTransaction()
                            .setCustomAnimations(
                                R.anim.slide_in_up_sheet,
                                R.anim.slide_out_up_sheet,
                                R.anim.slide_in_down_sheet,
                                R.anim.slide_out_down_sheet
                            )
                            .replace(R.id.fragment, fragment)
                            .addToBackStack(null)
                            .commit()
                    },
                    defaultHours = 0,
                    defaultMinutes = 1,
                    defaultSeconds = 0
                )
            }
        }
    }

    /**
     * Update the time zones displayed in the clock fragments pager.
     */
    internal fun setClockFragments() {
        binding.clockPageView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.clockPageView.setContent {
            val timeZoneEnabled by Preferences.TIME_ZONE_ENABLED.getFlow(requireContext()).collectAsState(initial = false)
            val selectedZonesCsv by Preferences.TIME_ZONES.getFlow(requireContext()).collectAsState(initial = "")

            val selectedZones = buildList {
                add(TimeZone.getDefault().id)
                if (timeZoneEnabled) {
                    selectedZonesCsv
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && TimeZone.getAvailableIDs().contains(it) }
                        .forEach { add(it) }
                }
            }

            val background = rememberBackgroundPainterState(isAlarm = false)

            val clockScreens = selectedZones.map {
                @Composable {
                    ClockScreen(
                        timezoneId = it,
                        onClockTap = {
                            if (Preferences.SCROLL_TO_NEXT.get(requireContext())) {
                                navigateToNearestAlarm()
                            }
                        },
                        getTextColor = {
                            getContrastingTextColorFromBg(requireContext())
                        }
                    )
                }
            }

            ClockPageView(
                fragments = clockScreens,
                backgroundPainter = background!!,
                pageIndicatorVisible = clockScreens.size > 1
            )
        }
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

    companion object {
        const val INTENT_ACTION = "com.meenbeese.chronos.HomeFragment.INTENT_ACTION"
    }
}
