package me.jfenn.alarmio.adapters

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat

import com.afollestad.aesthetic.Aesthetic.Companion.get

import me.jfenn.alarmio.Alarmio
import me.jfenn.alarmio.R
import me.jfenn.alarmio.data.SoundData
import me.jfenn.alarmio.interfaces.SoundChooserListener


class SoundsAdapter(private val alarmio: Alarmio, private val sounds: List<SoundData>) :
    RecyclerView.Adapter<SoundsAdapter.ViewHolder>() {
    private var currentlyPlaying = -1
    private var listener: SoundChooserListener? = null
    fun setListener(listener: SoundChooserListener?) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_sound, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position == 0) {
            holder.title.setText(R.string.title_sound_none)
            holder.icon.setOnClickListener(null)
            holder.itemView.setOnClickListener {
                listener?.onSoundChosen(
                    null
                )
            }
            setPlaying(holder, isPlaying = false, isAnimated = false)
            holder.icon.setImageResource(R.drawable.ic_ringtone_disabled)
        } else {
            val sound = sounds[position - 1]
            holder.title.text = sound.name
            holder.icon.setOnClickListener {
                val position1 = holder.adapterPosition
                val sound1 = sounds[position1 - 1]
                currentlyPlaying = if (sound1.isPlaying(alarmio) || currentlyPlaying == position1) {
                    sound1.stop(alarmio)
                    -1
                } else {
                    sound1.preview(alarmio)
                    if (currentlyPlaying >= 0) {
                        sounds[currentlyPlaying - 1].stop(alarmio)
                        notifyItemChanged(currentlyPlaying)
                    }
                    position1
                }
                setPlaying(holder, currentlyPlaying == position1, true)
            }
            holder.itemView.setOnClickListener {
                listener?.onSoundChosen(
                    sounds[holder.adapterPosition - 1]
                )
            }
            setPlaying(holder, sound.isPlaying(alarmio), false)
        }
    }

    @SuppressLint("CheckResult")
    private fun setPlaying(holder: ViewHolder, isPlaying: Boolean, isAnimated: Boolean) {
        (if (isPlaying) get().colorPrimary() else get().textColorPrimary()).take(1)
            .subscribe { integer: Int? ->
                if (isAnimated) {
                    val animator = ValueAnimator.ofObject(
                        ArgbEvaluator(),
                        holder.title.textColors.defaultColor,
                        integer
                    )
                    animator.setDuration(300)
                    animator.addUpdateListener { valueAnimator: ValueAnimator ->
                        val color = valueAnimator.animatedValue as Int
                        holder.title.setTextColor(color)
                        holder.icon.setColorFilter(color)
                    }
                    animator.start()
                } else {
                    holder.title.setTextColor(integer!!)
                    holder.icon.setColorFilter(integer)
                }
            }
        get().textColorPrimary().take(1).subscribe { integer: Int? ->
            if (isAnimated) {
                val animator = ValueAnimator.ofObject(
                    ArgbEvaluator(),
                    if (isPlaying) Color.TRANSPARENT else integer,
                    if (isPlaying) integer else Color.TRANSPARENT
                )
                animator.setDuration(300)
                animator.addUpdateListener { valueAnimator: ValueAnimator ->
                    holder.itemView.setBackgroundColor(
                        valueAnimator.animatedValue as Int
                    )
                }
                animator.start()
            } else holder.itemView.setBackgroundColor((if (isPlaying) integer else Color.TRANSPARENT)!!)
        }
        if (isAnimated) {
            val drawable = AnimatedVectorDrawableCompat.create(
                alarmio, if (isPlaying) R.drawable.ic_play_to_pause else R.drawable.ic_pause_to_play
            )
            if (drawable != null) {
                holder.icon.setImageDrawable(drawable)
                drawable.start()
                return
            }
        }
        holder.icon.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    override fun getItemCount(): Int {
        return sounds.size + 1
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView
        val title: TextView

        init {
            icon = itemView.findViewById(R.id.icon)
            title = itemView.findViewById(R.id.title)
        }
    }
}
