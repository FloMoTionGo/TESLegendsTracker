package com.ediposouza.teslesgendstracker.ui.cards.tabs

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.annotation.LayoutRes
import android.support.v4.app.ActivityCompat
import android.support.v4.app.ActivityOptionsCompat
import android.support.v7.util.DiffUtil
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.widget.CompoundButton
import com.ediposouza.teslesgendstracker.App
import com.ediposouza.teslesgendstracker.R
import com.ediposouza.teslesgendstracker.data.*
import com.ediposouza.teslesgendstracker.interactor.PrivateInteractor
import com.ediposouza.teslesgendstracker.interactor.PublicInteractor
import com.ediposouza.teslesgendstracker.ui.base.*
import com.ediposouza.teslesgendstracker.ui.cards.*
import com.ediposouza.teslesgendstracker.ui.util.GridSpacingItemDecoration
import com.ediposouza.teslesgendstracker.ui.util.SimpleDiffCallback
import com.ediposouza.teslesgendstracker.util.*
import jp.wasabeef.recyclerview.animators.ScaleInAnimator
import kotlinx.android.synthetic.main.fragment_cards_list.*
import kotlinx.android.synthetic.main.include_login_button.*
import kotlinx.android.synthetic.main.itemlist_card.view.*
import org.greenrobot.eventbus.Subscribe
import org.jetbrains.anko.itemsSequence

/**
 * Created by EdipoSouza on 10/30/16.
 */
open class CardsAllFragment : BaseFragment() {

    protected val KEY_CURRENT_ATTR = "currentClassKey"

    open val ADS_EACH_ITEMS = 21 //after 7 lines
    open val CARDS_PER_ROW = 3
    val EXPAND_CODE = 123

    var currentAttr: CardAttribute = CardAttribute.STRENGTH
    var cardsLoaded: List<Card> = listOf()
    var magickaFilter: Int = -1
    var setFilter: CardSet? = null
    var classFilter: DeckClass? = null
    var rarityFilter: CardRarity? = null
    var searchFilter: String? = null
    var menuSets: SubMenu? = null
    var onlyFavorites: CompoundButton? = null
    var sets: List<CardSet> = listOf()
    open var enableMenu: Boolean = true

    val transitionName: String by lazy { getString(R.string.card_transition_name) }
    val gridLayoutManager by lazy { cards_recycler_view?.layoutManager as? GridLayoutManager }
    val SEARCH_ANIMAL by lazy { getString(R.string.cards_search_animal) }
    val SEARCH_LORE by lazy { getString(R.string.cards_search_lore) }
    val SEARCH_REWARD by lazy { getString(R.string.cards_search_reward) }
    val SEARCH_UNDEAD by lazy { getString(R.string.cards_search_undead) }
    val SEARCH_UNIQUE by lazy { getString(R.string.cards_search_unique) }

    protected var shouldScrollToTop: Boolean = false

    open protected val isCardsCollection: Boolean = false

    open val cardsAdapter by lazy {
        CardsAllAdapter(ADS_EACH_ITEMS, gridLayoutManager, R.layout.itemlist_card_ads,
                { view, card -> showCardExpanded(card, view) }) {
            view: View, card: Card ->
            showCardExpanded(card, view)
            true
        }
    }

    open val itemDecoration by lazy {
        GridSpacingItemDecoration(CARDS_PER_ROW,
                resources.getDimensionPixelSize(R.dimen.card_margin), true)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return container?.inflate(R.layout.fragment_cards_list)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(enableMenu)
        configRecycleView()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        outState?.apply {
            putInt(KEY_CURRENT_ATTR, currentAttr.ordinal)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (cards_recycler_view?.layoutManager != null) {
            cardsAdapter.onRestoreState(cards_recycler_view.layoutManager as GridLayoutManager)
        }
        currentAttr = CardAttribute.values()[savedInstanceState?.getInt(KEY_CURRENT_ATTR) ?: 0]
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        menuSets = menu?.findItem(R.id.menu_sets)?.subMenu
        getSets()
        menu?.findItem(R.id.menu_only_favorite)?.isVisible = true
        onlyFavorites = menu?.findItem(R.id.menu_only_favorite)?.actionView as? CompoundButton
        onlyFavorites?.apply {
            configSignButtons()
            setOnCheckedChangeListener { _, _ ->
                val shouldShowLogin = !App.hasUserLogged() && isChecked
                cards_recycler_view.visibility = View.INVISIBLE.takeIf { shouldShowLogin } ?: View.VISIBLE
                signin_buttons?.visibility = View.VISIBLE.takeIf { shouldShowLogin } ?: View.INVISIBLE
                showCards()
                MetricsManager.trackAction(MetricAction.ACTION_CARD_FILTER_FAVORITE(isChecked))
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_sets_all -> filterSet(item, null)
            else -> sets.find { it.ordinal == item?.itemId }?.apply { filterSet(item, this) }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EXPAND_CODE && resultCode == Activity.RESULT_OK) {
            updateCardsList()
        }
    }

    private fun getSets() {
        menuSets?.apply {
            clear()
            add(0, R.id.menu_sets_all, 0, getString(R.string.cards_sets_all)).setIcon(R.drawable.ic_checked)
            PublicInteractor.getSets {
                sets = it
                sets.forEach {
                    add(0, it.ordinal, 0, it.title)
                }
            }
        }
    }

    private fun filterSet(menuItem: MenuItem?, set: CardSet?) {
        menuSets?.itemsSequence()?.forEach {
            it.setIcon(if (it.itemId == menuItem?.itemId) R.drawable.ic_checked else 0)
        }
        eventBus.post(CmdFilterSet(set))
    }

    open fun configRecycleView() {
        with(cards_recycler_view) {
            layoutManager = object : GridLayoutManager(context, CARDS_PER_ROW) {
                override fun supportsPredictiveItemAnimations(): Boolean = false
            }
            itemAnimator = ScaleInAnimator()
            adapter = cardsAdapter
            addItemDecoration(itemDecoration)
            cards_refresh_layout.setOnRefreshListener {
                cards_refresh_layout.isRefreshing = false
                loadCardsByAttr(currentAttr)
            }
        }
    }

    override fun configLoggedViews() {
        super.configLoggedViews()
        cards_recycler_view.visibility = View.VISIBLE.takeIf { App.hasUserLogged() } ?: View.INVISIBLE
    }

    @Subscribe
    @Suppress("unused")
    fun onCmdUpdateRarityMagickaFiltersVisibility(update: CmdUpdateVisibility) {
        if (isFragmentSelected) {
            (activity as BaseFilterActivity).updateRarityMagickaFiltersVisibility(update.show)
        }
    }

    @Subscribe
    @Suppress("unused", "UNUSED_PARAMETER")
    fun onCmdLoginSuccess(cmdLoginSuccess: CmdLoginSuccess) {
        configLoggedViews()
        if (onlyFavorites?.isChecked ?: false) {
            showCards()
        } else {
            loadCardsByAttr(currentAttr)
        }
    }

    @Subscribe
    @Suppress("unused")
    fun onCmdShowCardsByAttr(showCardsByAttr: CmdShowCardsByAttr) {
        loadCardsByAttr(showCardsByAttr.attr)
        if (isFragmentSelected) {
            MetricsManager.trackAction(MetricAction.ACTION_CARD_FILTER_ATTR(showCardsByAttr.attr))
        }
    }

    @Subscribe
    @Suppress("unused")
    fun onCmdFilterSet(filterSet: CmdFilterSet) {
        setFilter = filterSet.set
        loadCardsByAttr(currentAttr)
    }

    @Subscribe
    @Suppress("unused")
    fun onCmdFilterClass(filterClass: CmdFilterClass) {
        classFilter = filterClass.cls
        showCards()
        if (isFragmentSelected) {
            MetricsManager.trackAction(MetricAction.ACTION_CARD_FILTER_SET(setFilter))
        }
    }

    @Subscribe
    @Suppress("unused")
    fun onCmdFilterSearch(filterSearch: CmdFilterSearch) {
        searchFilter = filterSearch.search
        showCards()
    }

    @Subscribe
    @Suppress("unused")
    fun onCmdFilterRarity(filterRarity: CmdFilterRarity) {
        rarityFilter = filterRarity.rarity
        showCards()
        if (isFragmentSelected) {
            MetricsManager.trackAction(MetricAction.ACTION_CARD_FILTER_RARITY(rarityFilter))
        }
    }

    @Subscribe
    @Suppress("unused")
    fun onCmdFilterMagicka(filterMagicka: CmdFilterMagicka) {
        magickaFilter = filterMagicka.magicka
        showCards()
        if (isFragmentSelected) {
            MetricsManager.trackAction(MetricAction.ACTION_CARD_FILTER_MAGICKA(magickaFilter))
        }
    }

    open fun loadCardsByAttr(attribute: CardAttribute) {
        currentAttr = attribute
        PublicInteractor.getCards(setFilter, attribute) {
            cardsLoaded = it
            showCards()
        }
    }

    open fun showCards() {
        if (onlyFavorites?.isChecked ?: false) {
            PrivateInteractor.getUserFavoriteCards(setFilter, currentAttr) { userFavorites ->
                cardsAdapter.showCards(filteredCards().filter { userFavorites.contains(it.shortName) })
                scrollToTop()
            }
        } else {
            cardsAdapter.showCards(filteredCards())
            eventBus.post(CmdUpdateVisibility(true))
            scrollToTop()
        }
    }

    protected fun scrollToTop() {
        if (shouldScrollToTop) {
            cards_recycler_view?.scrollToPosition(0)
        } else {
            shouldScrollToTop = true
        }
    }

    fun updateCardsList(selectedAttr: CardAttribute = currentAttr) {
        currentAttr = selectedAttr
        shouldScrollToTop = false
        if (cards_recycler_view != null) {
            loadCardsByAttr(currentAttr)
        }
    }

    open fun filteredCards(): List<Card> {
        return cardsLoaded
                .filter {
                    when (searchFilter) {
                        null -> it is Card
                        else -> {
                            val search = searchFilter?.toLowerCase()?.trim() ?: ""
                            val hasValidSearchKeyword = (search == SEARCH_LORE && it.lore.isNotEmpty()) ||
                                    (search == SEARCH_ANIMAL && ConfigManager.animalRaces().split(", ").map { CardRace.of(it) }.contains(it.race)) ||
                                    (search == SEARCH_UNDEAD && ConfigManager.undeadRaces().split(", ").map { CardRace.of(it) }.contains(it.race)) ||
                                    (search == SEARCH_REWARD && it.season.isNotEmpty()) ||
                                    (search == SEARCH_UNIQUE && it.unique)
                            hasValidSearchKeyword || (it.name.toLowerCase().contains(search) ||
                                    it.race.name.toLowerCase().contains(search) ||
                                    it.set.title.toLowerCase().contains(search) ||
                                    it.rarity.name.toLowerCase().contains(search) ||
                                    it.type.name.toLowerCase().contains(search) ||
                                    it.keywords.filter { it.name.toLowerCase().contains(search) }.isNotEmpty() ||
                                    it.text.contains(search))
                        }
                    }
                }
                .filter {
                    when (rarityFilter) {
                        null -> it.rarity is CardRarity
                        else -> it.rarity == rarityFilter
                    }
                }
                .filter {
                    when {
                        magickaFilter == -1 -> it.cost is Int
                        magickaFilter < 7 -> it.cost == magickaFilter
                        else -> it.cost >= magickaFilter
                    }
                }
                .filter {
                    when {
                        classFilter != null && currentAttr == CardAttribute.DUAL ->
                            if (classFilter?.attr2 == CardAttribute.NEUTRAL)
                                it.dualAttr1 == classFilter?.attr1 || it.dualAttr2 == classFilter?.attr1
                            else
                                (it.dualAttr1 == classFilter?.attr1 && it.dualAttr2 == classFilter?.attr2) ||
                                        (it.dualAttr1 == classFilter?.attr2 && it.dualAttr2 == classFilter?.attr1)
                        else -> it.attr is CardAttribute
                    }
                }
    }

    open fun showCardExpanded(card: Card, view: View) {
        if (onlyFavorites?.isChecked ?: false) {
            startActivityForResult(CardActivity.newIntent(context, card), EXPAND_CODE,
                    ActivityOptionsCompat.makeSceneTransitionAnimation(activity, view, transitionName).toBundle())
        } else {
            ActivityCompat.startActivity(activity, CardActivity.newIntent(context, card),
                    ActivityOptionsCompat.makeSceneTransitionAnimation(activity, view, transitionName).toBundle())
        }
    }

    open class CardsAllAdapter(adsEachItems: Int, layoutManager: GridLayoutManager?,
                               @LayoutRes adsLayout: Int, val itemClick: (View, Card) -> Unit,
                               val itemLongClick: (View, Card) -> Boolean) : BaseAdsAdapter(adsEachItems, adsLayout, layoutManager) {

        var items: List<Card> = listOf()

        override fun onCreateDefaultViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
            return CardsAllViewHolder(parent.inflate(R.layout.itemlist_card), itemClick, itemLongClick)
        }

        override fun onBindDefaultViewHolder(holder: RecyclerView.ViewHolder?, position: Int) {
            (holder as CardsAllViewHolder).bind(items[position])
        }

        override fun getDefaultItemCount(): Int = items.size

        fun showCards(cards: List<Card>) {
            val oldItems = items
            items = cards
            if (items.isEmpty() || items.minus(oldItems).isEmpty()) {
                notifyDataSetChanged()
                return
            }
            DiffUtil.calculateDiff(SimpleDiffCallback(items, oldItems) { oldItem, newItem ->
                oldItem.shortName == newItem.shortName
            }).dispatchUpdatesTo(this)
        }

    }

    open class CardsAllViewHolder(val view: View?, val itemClick: (View, Card) -> Unit,
                                  val itemLongClick: (View, Card) -> Boolean) : RecyclerView.ViewHolder(view) {

        init {
            val cardLayoutParams = itemView.card_all_image.layoutParams
            cardLayoutParams.height = itemView.context.resources.getDimensionPixelSize(R.dimen.card_height)
            itemView.card_all_image.layoutParams = cardLayoutParams
        }

        fun bind(card: Card) {
            with(itemView) {
                setOnClickListener { itemClick(card_all_image, card) }
                setOnLongClickListener { itemLongClick(card_all_image, card) }
                card_all_image.loadFromCard(card)
            }
        }

    }

}
