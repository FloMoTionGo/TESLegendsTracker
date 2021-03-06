package com.ediposouza.teslesgendstracker.interactor

import com.ediposouza.teslesgendstracker.ARENA_TIER_PLUS_VALUE_DELIMITER
import com.ediposouza.teslesgendstracker.NEWS_UUID_PATTERN
import com.ediposouza.teslesgendstracker.PATCH_UUID_PATTERN
import com.ediposouza.teslesgendstracker.SEASON_UUID_PATTERN
import com.ediposouza.teslesgendstracker.data.*
import com.ediposouza.teslesgendstracker.util.toIntSafely
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import org.threeten.bp.Month
import org.threeten.bp.YearMonth
import org.threeten.bp.format.DateTimeFormatter

abstract class FirebaseParsers {

    class CardParser {

        val name: String = ""
        val rarity: String = ""
        val unique: Boolean = false
        val cost: String = ""
        val attack: String = ""
        val health: String = ""
        val type: String = ""
        val race: String = CardRace.NONE.name
        val keyword: String = ""
        val text: String = ""
        val arenaTier: String = CardArenaTier.NONE.name
        val arenaTierPlus: Map<String, String> = mapOf()
        val evolves: Boolean = false
        val attr1: String = ""
        val attr2: String = ""
        val season: String = ""
        val shout: Int = 0
        val creators: String = ""
        val generates: String = ""
        val tokens: String = ""
        val lore: String = ""
        val loreLink: String = ""

        fun toCard(shortName: String, set: CardSet, attr: CardAttribute): Card {
            var clsAttr1 = attr
            var clsAttr2 = attr
            if (attr == CardAttribute.DUAL) {
                clsAttr1 = CardAttribute.valueOf(attr1.trim().toUpperCase())
                clsAttr2 = CardAttribute.valueOf(attr2.trim().toUpperCase())
            }
            return Card(name, shortName, set, attr, clsAttr1, clsAttr2, CardRarity.of(rarity), unique,
                    cost.toIntSafely(), attack.toIntSafely(), health.toIntSafely(),
                    CardType.of(type), CardRace.of(race),
                    keyword.split(",")
                            .filter { it.trim().isNotEmpty() }
                            .mapTo(arrayListOf<CardKeyword>()) {
                                CardKeyword.of(it)
                            },
                    text, CardArenaTier.of(arenaTier), getCardArenaTierPlus(), evolves, season, shout,
                    creators.split(", ").filter { it.isNotEmpty() }, generates.split(", ").filter { it.isNotEmpty() },
                    tokens.split(", ").filter { it.isNotEmpty() }, lore, loreLink)
        }

        private fun getCardArenaTierPlus(): List<CardArenaTierPlus?> {
            return arenaTierPlus.map {
                val cardArenaTierPlusType = CardArenaTierPlusType.of(it.key)
                var operator: CardArenaTierPlusOperator? = null
                var value = when (cardArenaTierPlusType) {
                    CardArenaTierPlusType.ATTACK,
                    CardArenaTierPlusType.COST,
                    CardArenaTierPlusType.HEALTH ->
                        with(it.value.split(ARENA_TIER_PLUS_VALUE_DELIMITER)) {
                            operator = CardArenaTierPlusOperator.of(get(0))
                            get(1)
                        }
                    else -> it.value
                }
                CardArenaTierPlus(cardArenaTierPlusType, operator, value)
            }.toList()
        }

        fun toCardStatistic(shortName: String): CardStatistic {
            return CardStatistic(shortName, CardRarity.of(rarity), unique)
        }

    }

    class DeckParser(
            val name: String = "",
            val type: Int = 0,
            val cls: Int = 0,
            val cost: Int = 0,
            val owner: String = "",
            val createdAt: String = "",
            val updatedAt: String = "",
            val patch: String = "",
            val views: Int = 0,
            val likes: List<String> = listOf(),
            val cards: Map<String, Int> = mapOf(),
            val updates: Map<String, Map<String, Int>> = mapOf(),
            val comments: Map<String, Map<String, String>> = mapOf()
    ) {

        companion object {

            private const val KEY_DECK_NAME = "name"
            private const val KEY_DECK_TYPE = "type"
            private const val KEY_DECK_CLASS = "cls"
            private const val KEY_DECK_PATCH = "patch"
            private const val KEY_DECK_UPDATE_AT = "updatedAt"
            private const val KEY_DECK_COMMENT_OWNER = "owner"
            private const val KEY_DECK_COMMENT_MSG = "comment"
            private const val KEY_DECK_COMMENT_CREATE_AT = "createdAt"

            fun toNewCommentMap(owner: String, comment: String): Map<String, String> {
                return mapOf(KEY_DECK_COMMENT_OWNER to owner, KEY_DECK_COMMENT_MSG to comment,
                        KEY_DECK_COMMENT_CREATE_AT to LocalDateTime.now().withNano(0).toString())
            }

        }

        fun toDeck(uuid: String, private: Boolean): Deck {
            val createdAtDate = LocalDateTime.now().takeIf { createdAt.isNullOrBlank() } ?: LocalDateTime.parse(createdAt)
            val updatedAtDate = LocalDateTime.now().takeIf { updatedAt.isNullOrBlank() } ?: LocalDateTime.parse(updatedAt)
            return Deck(uuid, name, owner, private, DeckType.values()[type], DeckClass.values()[cls], cost,
                    createdAtDate, updatedAtDate, patch, likes, views, cards,
                    updates.map { DeckUpdate(LocalDateTime.parse(it.key), it.value) },
                    comments.map {
                        DeckComment(it.key, it.value[KEY_DECK_COMMENT_OWNER] as String,
                                it.value[KEY_DECK_COMMENT_MSG] as String,
                                LocalDateTime.parse(it.value[KEY_DECK_COMMENT_CREATE_AT] as String))
                    })
        }

        fun fromDeck(deck: Deck): DeckParser {
            return DeckParser(deck.name, deck.type.ordinal, deck.cls.ordinal, deck.cost, deck.owner,
                    deck.createdAt.toString(), deck.updatedAt.toString(), deck.patch, deck.views,
                    deck.likes, deck.cards, deck.updates.map { it.date.toString() to it.changes }.toMap(),
                    deck.comments.map {
                        it.uuid to mapOf(
                                KEY_DECK_COMMENT_OWNER to it.owner,
                                KEY_DECK_COMMENT_MSG to it.comment,
                                KEY_DECK_COMMENT_CREATE_AT to it.date.toString())
                    }.toMap())
        }

        fun toDeckUpdateMap(): Map<String, Any> {
            return mapOf(KEY_DECK_NAME to name, KEY_DECK_TYPE to type, KEY_DECK_CLASS to cls,
                    KEY_DECK_PATCH to patch, KEY_DECK_UPDATE_AT to updatedAt)
        }

        override fun toString(): String {
            return "DeckParser(name='$name', type=$type, cls=$cls, cost=$cost, owner='$owner', createdAt='$createdAt', updatedAt='$updatedAt', patch='$patch', views=$views, likes=$likes, cards=$cards, updates=$updates, comments=$comments)"
        }

    }

    class DeckFavoriteParser(

            val name: String = "",
            val cls: Int = 0

    )

    class PatchParser {

        companion object {

            private const val KEY_PATCH_ATTR = "attr"
            private const val KEY_PATCH_SET = "set"
            private const val KEY_PATCH_CHANGE = "change"

        }

        val changes: Map<String, Map<String, Any>> = mapOf()
        val desc: String = ""
        val legendsDeck: String = ""
        val type: String = ""

        @Suppress("UNCHECKED_CAST")
        fun toPatch(uuidDate: String): Patch {
            val date = LocalDate.parse(uuidDate, DateTimeFormatter.ofPattern(PATCH_UUID_PATTERN))
            val patchCardChanges = changes.map {
                val attr = it.value[KEY_PATCH_ATTR].toString()
                val set = it.value[KEY_PATCH_SET].toString()
                val change = it.value[KEY_PATCH_CHANGE].toString()
                PatchChange(attr, set, it.key, change)
            }
            return Patch(uuidDate, date, desc, legendsDeck, PatchType.of(type), patchCardChanges)
        }

    }

    class MatchParser(

            val first: Boolean = false,
            val player: Map<String, Any> = mapOf(),
            val opponent: Map<String, Any> = mapOf(),
            val legend: Boolean = false,
            val mode: Int = 0,
            val rank: Int = 0,
            val season: String = "",
            val win: Boolean = false

    ) {

        companion object {

            public const val KEY_MATCH_DECK_CLASS = "cls"
            private const val KEY_MATCH_DECK_DECK_UUID = "deck"
            private const val KEY_MATCH_DECK_NAME = "name"
            private const val KEY_MATCH_DECK_TYPE = "type"
            private const val KEY_MATCH_DECK_VERSION = "version"

        }

        fun toMatch(uuid: String): Match {
            val playerDeck = MatchDeck(player[KEY_MATCH_DECK_NAME].toString(),
                    DeckClass.values()[player[KEY_MATCH_DECK_CLASS].toString().toInt()],
                    DeckType.values()[player[KEY_MATCH_DECK_TYPE].toString().toInt()],
                    player[KEY_MATCH_DECK_DECK_UUID].toString(),
                    player[KEY_MATCH_DECK_VERSION].toString())
            val opponentDeck = MatchDeck(opponent[KEY_MATCH_DECK_NAME].toString(),
                    DeckClass.values()[opponent[KEY_MATCH_DECK_CLASS].toString().toInt()],
                    DeckType.values()[opponent[KEY_MATCH_DECK_TYPE].toString().toInt()])
            val matchMode = MatchMode.values()[mode]
            return Match(uuid, first, playerDeck, opponentDeck, matchMode, season, rank, legend, win)
        }

        fun fromMatch(match: Match): MatchParser {
            val player = with(match.player) {
                mapOf(KEY_MATCH_DECK_NAME to (name ?: ""), KEY_MATCH_DECK_CLASS to cls.ordinal,
                        KEY_MATCH_DECK_TYPE to type.ordinal, KEY_MATCH_DECK_DECK_UUID to (deck ?: ""),
                        KEY_MATCH_DECK_VERSION to (version ?: ""))
            }
            val opponent = with(match.opponent) {
                mapOf(KEY_MATCH_DECK_CLASS to cls.ordinal, KEY_MATCH_DECK_TYPE to type.ordinal)
            }
            return MatchParser(match.first, player, opponent, match.legend, match.mode.ordinal,
                    match.rank, match.season, match.win)
        }

        override fun toString(): String {
            return "MatchParser(first=$first, player=$player, opponent=$opponent, legend=$legend, mode=$mode, rank=$rank, win=$win)"
        }

    }

    class SeasonParser {

        val reward: Map<String, Any> = mapOf()

        fun toSeason(key: String): Season {
            val date = key.split("_")
            val year = date[0].toInt()
            val month = Month.of(date[1].toInt())
            val id = (year - 2016) * 12 + month.value - 7
            val rewardInfo = reward.entries.first()
            val rewardCardShortname = rewardInfo.value.takeIf { rewardInfo.value is String }
            val yearMonth = YearMonth.parse(key, DateTimeFormatter.ofPattern(SEASON_UUID_PATTERN))
            return Season(id, key, yearMonth, rewardInfo.key, rewardCardShortname as? String)
        }

    }

    class NewsParser(

            val category: Int = 0,
            val cover: String = "",
            val link: String = "",
            val title: String = ""

    ) {

        fun toNews(uuidDate: String): Article {
            val uuidDateFormatter = DateTimeFormatter.ofPattern(NEWS_UUID_PATTERN)
            val articleDate = LocalDate.parse(uuidDate, uuidDateFormatter)
            return Article(title, ArticleCategory.values()[category], cover, link, articleDate)
        }

        fun fromNews(article: Article): NewsParser {
            return NewsParser(article.category.ordinal, article.cover, article.link, article.title)
        }

    }

    class LevelUpParser(

            val source: String = "",
            val target: List<String> = listOf(),
            val type: String = "",
            val extra: String = "",
            val gold: Int = 0,
            val legendary: Boolean = false,
            val racial: Boolean = false

    ) {

        fun toLevelUp(key: String): LevelUp {
            return LevelUp(key.toIntSafely(), LevelUpSource.of(source), source, target, LevelUpType.of(type),
                    LevelUpExtra.of(extra), extra, gold, legendary, racial)
        }

    }

    class RaceParser {

        fun toRace(raceData: Pair<String, List<String>>): Race {
            return Race(CardRace.of(raceData.first), raceData.second)
        }

    }

    class RankedParser(

            val name: String = "",
            val monthly: Int = 0,
            val reset: Int = 0,
            val stars: Int = 0,
            val gems: Int = 0,
            val gold: Int = 0

    ) {

        fun toRanked(key: String): Ranked {
            return Ranked(key.toIntSafely(), name, monthly, reset, stars, gems, gold)
        }

    }

}
