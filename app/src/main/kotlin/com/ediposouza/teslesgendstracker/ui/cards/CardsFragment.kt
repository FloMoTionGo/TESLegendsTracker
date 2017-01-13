package com.ediposouza.teslesgendstracker.ui.cards

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.support.design.widget.BottomSheetBehavior
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.view.MenuItemCompat
import android.support.v4.view.ViewPager
import android.support.v7.widget.SearchView
import android.text.format.DateUtils
import android.view.*
import android.view.inputmethod.InputMethodManager
import com.ediposouza.teslesgendstracker.R
import com.ediposouza.teslesgendstracker.data.Attribute
import com.ediposouza.teslesgendstracker.ui.base.*
import com.ediposouza.teslesgendstracker.ui.cards.tabs.CardsAllFragment
import com.ediposouza.teslesgendstracker.ui.cards.tabs.CardsCollectionFragment
import com.ediposouza.teslesgendstracker.ui.cards.tabs.CardsFavoritesFragment
import com.ediposouza.teslesgendstracker.ui.cards.widget.CollectionStatistics
import com.ediposouza.teslesgendstracker.util.MetricScreen
import com.ediposouza.teslesgendstracker.util.MetricsManager
import com.ediposouza.teslesgendstracker.util.inflate
import com.ediposouza.teslesgendstracker.util.toggleExpanded
import kotlinx.android.synthetic.main.activity_dash.*
import kotlinx.android.synthetic.main.fragment_cards.*

/**
 * Created by EdipoSouza on 10/30/16.
 */
class CardsFragment : BaseFragment(), SearchView.OnQueryTextListener {

    private val KEY_PAGE_VIEW_POSITION = "pageViewPositionKey"

    private var query: String? = null
    private val handler = Handler()
    private val trackSearch = Runnable { MetricsManager.trackSearch(query ?: "") }

    private val statisticsSheetBehavior: BottomSheetBehavior<CollectionStatistics>
        get() = BottomSheetBehavior.from(cards_collection_statistics)

    val pageChange = object : ViewPager.SimpleOnPageChangeListener() {
        override fun onPageSelected(position: Int) {
            updateActivityTitle(position)
            (cards_view_pager.adapter as CardsPageAdapter).getItem(position).updateCardsList()
            if (position == 1) {
                statisticsSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                cards_collection_statistics.updateStatistics()
            } else {
                statisticsSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
            eventBus.post(CmdUpdateRarityMagikaFiltersPosition(position == 1))
            MetricsManager.trackScreen(when (position) {
                0 -> MetricScreen.SCREEN_CARDS_ALL()
                1 -> MetricScreen.SCREEN_CARDS_COLLECTION()
                else -> MetricScreen.SCREEN_CARDS_FAVORED()
            })
        }

    }

    private fun updateActivityTitle(position: Int) {
        val title = when (position) {
            1 -> R.string.title_tab_cards_collection
            2 -> R.string.title_tab_cards_favorites
            else -> R.string.title_tab_cards_all
        }
        eventBus.post(CmdUpdateTitle(title))
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return container?.inflate(R.layout.fragment_cards)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        activity.dash_navigation_view.setCheckedItem(R.id.menu_cards)
        cards_collection_statistics.setOnClickListener {
            statisticsSheetBehavior.toggleExpanded()
        }
        statisticsSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        cards_view_pager.adapter = CardsPageAdapter(context, childFragmentManager)
        cards_view_pager.addOnPageChangeListener(pageChange)
        cards_filter_attr.filterClick = {
            eventBus.post(CmdShowCardsByAttr(it))
            cards_filter_attr.selectAttr(it, true)
        }
        cards_filter_rarity.filterClick = { eventBus.post(CmdFilterRarity(it)) }
        cards_filter_magika.filterClick = { eventBus.post(CmdFilterMagika(it)) }
        Handler().postDelayed({
            eventBus.post(CmdShowCardsByAttr(Attribute.STRENGTH))
        }, DateUtils.SECOND_IN_MILLIS)
        MetricsManager.trackScreen(MetricScreen.SCREEN_CARDS_ALL())
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        eventBus.post(CmdUpdateTitle(R.string.app_name_full))
        cards_tab_layout.setupWithViewPager(cards_view_pager)
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        outState?.apply { putInt(KEY_PAGE_VIEW_POSITION, cards_view_pager?.currentItem ?: 0) }
        super.onSaveInstanceState(outState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.apply {
            cards_view_pager.currentItem = getInt(KEY_PAGE_VIEW_POSITION)
        }
    }

    override fun onResume() {
        super.onResume()
        cards_app_bar_layout.setExpanded(true, true)
        (activity as BaseFilterActivity).updateRarityMagikaFiltersVisibility(true)
    }

    override fun onPause() {
        super.onPause()
        (activity as BaseFilterActivity).updateRarityMagikaFiltersVisibility(false)
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        menu?.clear()
        inflater?.inflate(R.menu.menu_search, menu)
        inflater?.inflate(R.menu.menu_sets, menu)
        with(MenuItemCompat.getActionView(menu?.findItem(R.id.menu_search)) as SearchView) {
            queryHint = getString(R.string.cards_search_hint)
            setOnQueryTextListener(this@CardsFragment)
        }
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        query = newText
        eventBus.post(CmdFilterSearch(newText))
        handler.removeCallbacks(trackSearch)
        if (query?.isNotEmpty() ?: false) {
            handler.postDelayed(trackSearch, DateUtils.SECOND_IN_MILLIS * 2)
        }
        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        eventBus.post(CmdFilterSearch(query))
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(activity.currentFocus!!.windowToken, 0)
        return true
    }

    class CardsPageAdapter(ctx: Context, fm: FragmentManager) : FragmentStatePagerAdapter(fm) {

        var titles: Array<String> = ctx.resources.getStringArray(R.array.cards_tabs)
        val cardsCollectionFragment by lazy { CardsCollectionFragment() }
        val cardsFavoritesFragment by lazy { CardsFavoritesFragment() }
        val cardsAllFragment by lazy { CardsAllFragment() }

        override fun getItem(position: Int): CardsAllFragment {
            return when (position) {
                1 -> cardsCollectionFragment
                2 -> cardsFavoritesFragment
                else -> cardsAllFragment
            }
        }

        override fun getCount(): Int {
            return titles.size
        }

        override fun getPageTitle(position: Int): CharSequence {
            return titles[position]
        }

    }

}
