package com.ediposouza.teslesgendstracker.ui.cards.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.ediposouza.teslesgendstracker.R
import kotlinx.android.synthetic.main.widget_magika_filter.view.*

/**
 * Created by EdipoSouza on 11/2/16.
 */
class FilterMagika(ctx: Context?, attrs: AttributeSet?, defStyleAttr: Int) :
        LinearLayout(ctx, attrs, defStyleAttr) {

    var filterClick: ((Int) -> Unit)? = null

    init {
        inflate(context, R.layout.widget_magika_filter, this)
        if (!isInEditMode) {
            with(rootView) {
                magika_filter_0?.setOnClickListener { magikaClick(0) }
                magika_filter_1?.setOnClickListener { magikaClick(1) }
                magika_filter_2?.setOnClickListener { magikaClick(2) }
                magika_filter_3?.setOnClickListener { magikaClick(3) }
                magika_filter_4?.setOnClickListener { magikaClick(4) }
                magika_filter_5?.setOnClickListener { magikaClick(5) }
                magika_filter_6?.setOnClickListener { magikaClick(6) }
                magika_filter_7plus?.setOnClickListener { magikaClick(7) }
            }
            magika_filter.setOnMenuButtonClickListener {
                when (magika_filter.isOpened) {
                    true -> magika_filter.close(true)
                    false ->
                        if (magika_filter.tag == true)
                            magikaClick(-1)
                        else
                            magika_filter.open(true)
                }
            }
        }
    }

    constructor(ctx: Context?) : this(ctx, null, 0)

    constructor(ctx: Context?, attrs: AttributeSet) : this(ctx, attrs, 0)

    fun close() {
        magika_filter.close(true)
    }

    private fun magikaClick(magika: Int) {
        filterClick?.invoke(magika)
        val icon = if (magika == -1) R.drawable.ic_magika else R.drawable.ic_magika_clear
        rootView.magika_filter.apply {
            tag = magika != -1
            close(true)
            menuIconView.setImageResource(icon)
        }
    }

}