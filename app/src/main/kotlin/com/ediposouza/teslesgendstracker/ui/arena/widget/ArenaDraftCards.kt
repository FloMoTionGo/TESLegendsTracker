package com.ediposouza.teslesgendstracker.ui.widget

import android.app.Activity
import android.content.Context
import android.support.v4.app.ActivityCompat
import android.support.v4.app.ActivityOptionsCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.GridLayoutManager
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import com.ediposouza.teslesgendstracker.R
import com.ediposouza.teslesgendstracker.data.*
import com.ediposouza.teslesgendstracker.ui.cards.CardActivity
import com.ediposouza.teslesgendstracker.ui.cards.CmdFilterAttr
import com.ediposouza.teslesgendstracker.ui.cards.CmdFilterRarity
import com.ediposouza.teslesgendstracker.ui.cards.tabs.CardsAllFragment
import com.ediposouza.teslesgendstracker.ui.decks.widget.DeckList
import com.ediposouza.teslesgendstracker.ui.util.GridSpacingItemDecoration
import jp.wasabeef.recyclerview.animators.ScaleInAnimator
import kotlinx.android.synthetic.main.dialog_select_card.view.*
import kotlinx.android.synthetic.main.widget_arena_cards.view.*
import org.greenrobot.eventbus.EventBus

/**
 * Created by EdipoSouza on 11/2/16.
 */
class ArenaDraftCards(ctx: Context?, attrs: AttributeSet?, defStyleAttr: Int) :
        FrameLayout(ctx, attrs, defStyleAttr) {

    private val ADS_EACH_ITEMS = 20
    private val CARDS_PER_ROW = 2
    private val INVALID_TEXT_VALUE = "-"

    val cardTransitionName: String by lazy { context.getString(R.string.card_transition_name) }
    var selectDialog: AlertDialog? = null
    var selectedCard: Card? = null
    var currentAttr: CardAttribute? = CardAttribute.STRENGTH
    var currentMagika: Int = -1
    var currentRarity: CardRarity? = null
    var draftCardlist: DeckList? = null

    init {
        inflate(context, R.layout.widget_arena_cards, rootView as ViewGroup)
        reset()
    }

    constructor(ctx: Context?) : this(ctx, null, 0)

    constructor(ctx: Context?, attrs: AttributeSet) : this(ctx, attrs, 0)

    fun config(activity: Activity, cls: DeckClass, cards: List<Card>, cardOnLongOnClick: (Card) -> Unit, arena_draft_cardlist: DeckList) {
        draftCardlist = arena_draft_cardlist
        with(arena_draft_card_iv) {
            setOnClickListener { showSelectCardDialog(activity, cls, cards) }
            setOnLongClickListener { chooseCard(cardOnLongOnClick) }
        }
        arena_draft_card_value.setOnClickListener {
            val cardSynergy = arena_draft_card_value.tag?.toString() ?: ""
            if (cardSynergy.isNotBlank()) {
                Toast.makeText(context, cardSynergy, Toast.LENGTH_LONG).show()
            }
        }
        arena_draft_card_value_shadow.setOnClickListener { arena_draft_card_value.callOnClick() }
        arena_draft_arrow_iv.setOnClickListener { chooseCard(cardOnLongOnClick) }
    }

    fun reset() {
        selectedCard = null
        currentAttr = null
        currentMagika = -1
        currentRarity = null
        arena_draft_card_iv.setImageBitmap(Card.getDefaultCardImage(context))
        arena_draft_card_value.text = INVALID_TEXT_VALUE
        arena_draft_card_value.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        arena_draft_card_value_shadow.text = INVALID_TEXT_VALUE
    }

    private fun showSelectCardDialog(activity: Activity, cls: DeckClass, cards: List<Card>) {
        val gridLayoutManager: GridLayoutManager = object : GridLayoutManager(context, CARDS_PER_ROW) {
            override fun supportsPredictiveItemAnimations(): Boolean = false
        }
        val cardsAdapter = CardsAllFragment.CardsAllAdapter(ADS_EACH_ITEMS, gridLayoutManager,
                R.layout.itemlist_card_ads, { _, card -> onSelect(card) }) {
            view: View, card: Card ->
            showCardExpanded(activity, card, view)
            true
        }
        val dialogView = View.inflate(context, R.layout.dialog_select_card, null)
        with(dialogView.select_card_dialog_attr) {
            lockAttrs(cls.attr1, cls.attr2)
            filterClick = { attr ->
                currentAttr = attr
                EventBus.getDefault().post(CmdFilterAttr(attr))
                selectAttr(attr, true)
                updateCardList(cards, cardsAdapter)
            }
        }
        with(dialogView.select_card_dialog_magika) {
            collapseOnClick = false
            filterClick = { cost ->
                currentMagika = cost
                updateCardList(cards, cardsAdapter)
            }
        }
        with(dialogView.select_card_dialog_rarity) {
            collapseOnClick = false
            filterClick = { rarity ->
                currentRarity = rarity
                EventBus.getDefault().post(CmdFilterRarity(rarity))
                updateCardList(cards, cardsAdapter)
            }
        }
        with(dialogView.select_card_dialog_recycler_view) {
            layoutManager = gridLayoutManager
            itemAnimator = ScaleInAnimator()
            adapter = cardsAdapter
            addItemDecoration(GridSpacingItemDecoration(CARDS_PER_ROW,
                    resources.getDimensionPixelSize(R.dimen.card_margin), true))
        }
        selectDialog = AlertDialog.Builder(context, R.style.AppDialog)
                .setView(dialogView)
                .setTitle(R.string.new_arena_draft_pick)
                .create().apply {
            setOnShowListener {
                dialogView.select_card_dialog_attr.filterClick?.invoke(currentAttr ?: cls.attr1)
                dialogView.select_card_dialog_magika.open()
                dialogView.select_card_dialog_rarity.expand()
            }
            show()
        }
    }

    private fun chooseCard(cardOnLongOnClick: (Card) -> Unit): Boolean {
        if (selectedCard != null) {
            cardOnLongOnClick.invoke(selectedCard!!)
        }
        return true
    }

    private fun updateCardList(cards: List<Card>, cardsAdapter: CardsAllFragment.CardsAllAdapter) {
        cardsAdapter.showCards(cards.filter { it.attr == currentAttr }
                .filter { currentMagika == -1 || it.cost == currentMagika }
                .filter { currentRarity == null || it.rarity == currentRarity }
                .filter { !it.evolves }
                .sortedBy { it.cost })
    }

    private fun onSelect(card: Card) {
        selectedCard = card
        selectDialog?.dismiss()
        arena_draft_card_iv.setImageBitmap(card.imageBitmap(context))
        val calcArenaValue = calcArenaValue(card.arenaTier, card.arenaTierPlus)
        val arenaValue = (calcArenaValue.first.takeIf { it > 0 } ?: INVALID_TEXT_VALUE).toString()
        arena_draft_card_value.text = "$arenaValue" + ("*".takeIf { calcArenaValue.second.isNotEmpty() } ?: "")
        arena_draft_card_value.tag = context.getString(R.string.new_arena_draft_synergy,
                calcArenaValue.second.joinToString("\n") { "* ${it.name}" })
        arena_draft_card_value.setTextColor(ContextCompat.getColor(context, when (calcArenaValue.first) {
            in 0..CardArenaTier.AVERAGE.value.minus(1) -> R.color.red_500
            in CardArenaTier.AVERAGE.value..CardArenaTier.EXCELLENT.value.minus(1) -> android.R.color.white
            else -> R.color.colorAccent
        })
        )
        arena_draft_card_value_shadow.text = arena_draft_card_value.text
    }

    private fun calcArenaValue(arenaTier: CardArenaTier, arenaTierPlus: CardArenaTierPlus?): Pair<Int, List<Card>> {
        val cardsSynergy = mutableListOf<Card>()
        if (arenaTier == CardArenaTier.UNKNOWN || arenaTier == CardArenaTier.NONE) {
            return Pair(0, cardsSynergy)
        }
        val value = arenaTier.value
        if (arenaTierPlus == null) {
            return Pair(value, cardsSynergy)
        }
        var totalValueExtra = 0
        val extraPoints = arenaTierPlus.type.extraPoints
        draftCardlist?.getCards()?.forEach { (card, _) ->
            val extraValue = when (arenaTierPlus.type) {
                CardArenaTierPlusType.ATTACK -> getExtraPointsForIntValue(arenaTierPlus, card.attack)
                CardArenaTierPlusType.COST -> getExtraPointsForIntValue(arenaTierPlus, card.cost)
                CardArenaTierPlusType.HEALTH -> getExtraPointsForIntValue(arenaTierPlus, card.health)
                CardArenaTierPlusType.ATTR -> extraPoints.takeIf {
                    card.attr == CardAttribute.valueOf(arenaTierPlus.value.toUpperCase()) ||
                            card.dualAttr1 == CardAttribute.valueOf(arenaTierPlus.value.toUpperCase()) ||
                            card.dualAttr2 == CardAttribute.valueOf(arenaTierPlus.value.toUpperCase())
                } ?: 0
                CardArenaTierPlusType.KEYWORD -> extraPoints.takeIf {
                    card.keywords.filter { it.name == arenaTierPlus.value.toUpperCase() }.isNotEmpty()
                } ?: 0
                CardArenaTierPlusType.RACE -> extraPoints.takeIf {
                    card.race.name == arenaTierPlus.value.toUpperCase()
                } ?: 0
                CardArenaTierPlusType.STRATEGY -> 0
                CardArenaTierPlusType.TEXT -> extraPoints.takeIf {
                    card.name.contains(arenaTierPlus.value)
                } ?: 0
                CardArenaTierPlusType.TYPE -> extraPoints.takeIf {
                    card.type.name == arenaTierPlus.value.toUpperCase()
                } ?: 0
                else -> 0
            }
            if (extraValue > 0) {
                totalValueExtra += extraValue
                cardsSynergy.add(card)
            }
        }
        return Pair(value + totalValueExtra, cardsSynergy)
    }

    private fun getExtraPointsForIntValue(arenaTierPlus: CardArenaTierPlus, numberField: Int): Int {
        return arenaTierPlus.type.extraPoints.takeIf {
            when (arenaTierPlus.operator) {
                CardArenaTierPlusOperator.EQUALS -> numberField == arenaTierPlus.value.toInt()
                CardArenaTierPlusOperator.GREAT -> numberField > arenaTierPlus.value.toInt()
                CardArenaTierPlusOperator.MINOR -> numberField < arenaTierPlus.value.toInt()
                else -> false
            }
        } ?: 0
    }

    private fun showCardExpanded(activity: Activity, card: Card, view: View) {
        ActivityCompat.startActivity(context, CardActivity.newIntent(context, card),
                ActivityOptionsCompat.makeSceneTransitionAnimation(activity, view, cardTransitionName).toBundle())
    }

}