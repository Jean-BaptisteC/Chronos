package com.meenbeese.chronos.adapters

import android.view.LayoutInflater
import android.view.ViewGroup

import androidx.recyclerview.widget.RecyclerView

import com.meenbeese.chronos.data.preference.BasePreferenceData
import com.meenbeese.chronos.data.preference.BasePreferenceData.ViewHolder

class PreferenceAdapter(
    private val items: MutableList<BasePreferenceData<ViewHolder>>
) : RecyclerView.Adapter<ViewHolder>() {

    private val viewTypeMap = mutableMapOf<Class<out BasePreferenceData<*>>, Int>()
    private val viewTypeReverseMap = mutableMapOf<Int, Class<out BasePreferenceData<*>>>()

    private var nextViewType = 0

    init {
        registerItemTypes(items)
    }

    private fun registerItemTypes(newItems: List<BasePreferenceData<*>>) {
        for (item in newItems) {
            val clazz = item::class.java
            if (!viewTypeMap.containsKey(clazz)) {
                viewTypeMap[clazz] = nextViewType
                viewTypeReverseMap[nextViewType] = clazz
                nextViewType++
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val clazz = viewTypeReverseMap[viewType]!!
        val item = items.first { it::class.java == clazz }
        return item.getViewHolder(LayoutInflater.from(parent.context), parent)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        items[position].bindViewHolder(holder)
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        val clazz = items[position]::class.java
        if (!viewTypeMap.containsKey(clazz)) {
            viewTypeMap[clazz] = nextViewType
            viewTypeReverseMap[nextViewType] = clazz
            nextViewType++
        }
        return viewTypeMap[clazz]!!
    }
}
