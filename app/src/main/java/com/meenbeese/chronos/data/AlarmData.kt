package com.meenbeese.chronos.data

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Parcelable

import com.meenbeese.chronos.activities.MainActivity
import com.meenbeese.chronos.db.AlarmDatabase
import com.meenbeese.chronos.db.AlarmEntity
import com.meenbeese.chronos.receivers.AlarmReceiver
import com.meenbeese.chronos.services.SleepReminderService
import com.meenbeese.chronos.utils.toNullable

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

import java.util.Calendar
import java.util.Date

@Parcelize
class AlarmData(
    var id: Int,
    var name: String? = null,
    var time: Calendar = Calendar.getInstance(),
    var isEnabled: Boolean = true,
    var days: MutableList<Boolean> = MutableList(7) { false },
    var isVibrate: Boolean = true,
    var sound: SoundData? = null
) : Parcelable {

    fun saveToDatabase(context: Context) {
        val alarmDao = AlarmDatabase.getDatabase(context).alarmDao()
        val alarmEntity = AlarmEntity(id, name, time.timeInMillis, isEnabled, days, isVibrate, sound?.toString())
        CoroutineScope(Dispatchers.IO).launch {
            if (alarmDao.getAlarmById(id) == null) {
                alarmDao.insert(alarmEntity)
            } else {
                alarmDao.update(alarmEntity)
            }
        }
    }

    fun deleteFromDatabase(context: Context) {
        val alarmDao = AlarmDatabase.getDatabase(context).alarmDao()
        CoroutineScope(Dispatchers.IO).launch {
            alarmDao.getAlarmById(id)?.let { alarmDao.delete(it) }
        }
    }

    fun calculateNextTriggerTime(): Long {
        return getNext()?.timeInMillis ?: Long.MAX_VALUE
    }

    fun set(context: Context): Date? {
        val nextTime = getNext() ?: return null
        setAlarm(context, nextTime.timeInMillis)
        return nextTime.time
    }

    private fun setAlarm(context: Context, timeMillis: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val alarmIntent = getIntent(context)
        val serviceIntent = PendingIntent.getService(
            context,
            id,
            Intent(context, SleepReminderService::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        manager.setAlarmClock(
            AlarmClockInfo(timeMillis, PendingIntent.getActivity(
                context, id, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )),
            alarmIntent
        )
        manager.set(
            AlarmManager.RTC_WAKEUP,
            timeMillis,
            serviceIntent
        )
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(getIntent(context))
    }

    private fun getIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, id)
        }
        return PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun getNext(): Calendar? {
        if (!isEnabled) return null
        val now = Calendar.getInstance()
        val next = time.clone() as Calendar
        next.set(Calendar.SECOND, 0)
        while (now.after(next)) next.add(Calendar.DATE, 1)
        if (days.any { it }) {
            var nextDay = next.get(Calendar.DAY_OF_WEEK) - 1
            repeat(7) {
                if (days[nextDay]) return@repeat
                nextDay = (nextDay + 1) % 7
            }
            next.set(Calendar.DAY_OF_WEEK, nextDay + 1)
            while (now.after(next)) next.add(Calendar.DATE, 7)
        }
        return next
    }

    fun isRepeat(): Boolean = days.count { it } > 1
}

fun AlarmData.toEntity(): AlarmEntity {
    return AlarmEntity(
        id = this.id,
        name = this.name,
        timeInMillis = this.time.timeInMillis,
        isEnabled = this.isEnabled,
        days = this.days,
        isVibrate = this.isVibrate,
        sound = this.sound?.toString()
    )
}

fun AlarmEntity.toData(): AlarmData {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = this@toData.timeInMillis
    }

    return AlarmData(
        id = this.id,
        name = this.name,
        time = calendar,
        isEnabled = this.isEnabled,
        days = this.days.toMutableList(),
        isVibrate = this.isVibrate,
        sound = this.sound?.let { SoundData.fromString(it).toNullable() }
    )
}
