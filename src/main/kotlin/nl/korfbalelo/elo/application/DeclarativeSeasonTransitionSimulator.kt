package nl.korfbalelo.elo.application

import com.google.gson.Gson
import nl.korfbalelo.elo.ApplicationNew
import nl.korfbalelo.elo.PoulePredicter
import nl.korfbalelo.elo.Team
import nl.korfbalelo.mijnkorfbal.PouleData
import java.io.File
import java.time.LocalDate
import java.util.random.RandomGenerator
import kotlin.math.pow

// AI generated
class DeclarativeSeasonTransitionSimulator(
    private val definition: SeasonTransitionDefinition,
    private val overrides: SeasonTransitionOverrides = SeasonTransitionOverrides(),
) : SeasonTransitionSimulator {
    override fun simulate(
        poules: Map<String, PouleData>,
        random: RandomGenerator,
    ): SeasonTransitionResult {
        val predicters = poules.entries
            .sortedBy { it.key }
            .associate { (pouleName, pouleData) ->
                pouleName to PoulePredicter(pouleName, pouleData.first, pouleData.second, null)
            }
        return simulatePredicters(predicters, random, alreadySimulated = false)
    }

    fun simulatePredicters(
        predicters: Map<String, PoulePredicter>,
        random: RandomGenerator,
        alreadySimulated: Boolean = true,
    ): SeasonTransitionResult {
        if (!alreadySimulated) {
            with(random) {
                predicters.values.forEach { it.simulate() }
            }
        }
        return simulateResolvedPredicters(predicters, random)
    }

    fun sourceTierByTeam(predicters: Map<String, PoulePredicter>): Map<String, String> =
        definition.groups.associate { group ->
            group.id to predicters
                .filterKeys { Regex(group.poulePattern).matchEntire(it) != null }
                .values
                .sortedBy(PoulePredicter::pouleName)
        }.flatMap { (groupId, poules) ->
            poules.flatMap { poule ->
                poule.executor.result.map { it.first.name to groupId }
            }
        }.toMap()

    fun tierOrder(): List<String> = requireNotNull(definition.tierOrder) {
        "tierOrder missing for ${definition.id}"
    }

    fun group(groupId: String): TransitionGroup =
        requireNotNull(definition.groups.find { it.id == groupId }) {
            "Group $groupId missing for ${definition.id}"
        }

    fun seasonOutcomePromote(
        sourceGroupId: String,
        pouleName: String,
        position: Int,
        targetTier: String,
    ): Boolean {
        if (championshipPlayoffQualification(sourceGroupId, pouleName, position)) {
            return true
        }
        val sourceRank = tierRank(sourceGroupId)
        val targetRank = tierRank(targetTier)
        return targetRank < sourceRank && !treatTransitionAsSameTierOutcome(sourceGroupId, targetTier)
    }

    fun seasonOutcomeRelegate(
        sourceGroupId: String,
        targetTier: String,
    ): Boolean = tierRank(targetTier) > tierRank(sourceGroupId)

    private fun championshipPlayoffQualification(
        sourceGroupId: String,
        pouleName: String,
        position: Int,
    ): Boolean =
        definition.playoffs.orEmpty().any { playoff ->
            playoff.placements.any { it.event == "champion" } &&
                findBasePlayoffSources(playoff).any { source ->
                    source.groupId == sourceGroupId &&
                        source.position == position &&
                        (source.pouleName == null || source.pouleName == pouleName)
                }
        }

    private fun findBasePlayoffSources(playoff: PlayoffDefinition): List<BasePlayoffSource> {
        val drawsById = definition.draws.orEmpty().associateBy(DrawDefinition::id)
        return playoff.stages.flatMap { stage ->
            listOf(stage.teamA, stage.teamB).mapNotNull { selector ->
                when {
                    selector.groupId != null && selector.position != null -> BasePlayoffSource(
                        groupId = selector.groupId,
                        position = selector.position,
                        pouleName = selector.pouleName,
                    )

                    selector.drawId != null -> drawsById[selector.drawId]?.let { draw ->
                        BasePlayoffSource(groupId = draw.groupId, position = draw.position)
                    }

                    else -> null
                }
            }
        }
    }

    private fun treatTransitionAsSameTierOutcome(
        sourceGroupId: String,
        targetTier: String,
    ): Boolean =
        definition.id == "veld2526vj__veld2627nj" &&
            sourceGroupId == "ekd" &&
            targetTier == "ek"

    private fun simulateResolvedPredicters(
        predicters: Map<String, PoulePredicter>,
        random: RandomGenerator,
    ): SeasonTransitionResult {
        val groups = definition.groups.associate { group ->
            group.id to predicters
                .filterKeys { Regex(group.poulePattern).matchEntire(it) != null }
                .values
                .sortedBy(PoulePredicter::pouleName)
                .also {
                    require(it.size == group.expectedPoules) {
                        "Unexpected poule count for ${group.id}: expected ${group.expectedPoules}, was ${it.size}"
                    }
                }
        }
        val draws = definition.draws.orEmpty().associate { draw ->
            val teams = groups.getValue(draw.groupId)
                .map { it.executor.result[draw.position - 1].first }
                .let { if (draw.shuffle) it.shuffled(random) else it }
            draw.id to teams
        }
        val sourceByTeam = groups.flatMap { (groupId, poules) ->
            poules.flatMap { poule ->
                poule.executor.result.mapIndexed { index, entry ->
                    TeamStandingSource(
                        groupId = groupId,
                        pouleName = poule.pouleName,
                        position = index + 1,
                        entry = RankedStandingEntry(entry, poule.executor.result.size - 1),
                    )
                }
            }
        }.associateBy { it.entry.entry.first.name }

        val tierByTeam = mutableMapOf<String, String>()
        val eventsByTeam = mutableMapOf<String, MutableSet<String>>()
        fun assign(team: Team, tier: String, source: String) {
            val previous = tierByTeam.put(team.name, tier)
            check(previous == null) {
                "Team ${team.name} already assigned to $previous when applying $source -> $tier"
            }
        }
        fun event(team: Team, name: String) {
            eventsByTeam.getOrPut(team.name) { mutableSetOf() }.add(name)
        }

        definition.rules.forEach { rule ->
            when (rule.type) {
                "direct" -> applyDirectRule(rule, groups, tierByTeam) { team, tier ->
                    assign(team, tier, rule.id)
                }
                "ranked" -> applyRankedRule(rule, groups, tierByTeam, random) { team, tier ->
                    assign(team, tier, rule.id)
                }
                else -> error("Unsupported rule type ${rule.type}")
            }
        }

        definition.playoffs.orEmpty().forEach { playoff ->
            val outcomes = mutableMapOf<String, Pair<Team, Team>>()
            playoff.stages.forEach { stage ->
                val teamA = resolveSelector(stage.teamA, groups, draws, outcomes)
                val teamB = resolveSelector(stage.teamB, groups, draws, outcomes)
                outcomes[stage.id] = when (stage.type) {
                    "best_of_3" -> playOffSeries(teamA, teamB, requireNotNull(stage.dates), random)
                    "single" -> singleMatch(teamA, teamB, stage.date?.let(LocalDate::parse), stage.neutral, random)
                    else -> error("Unsupported playoff stage type ${stage.type}")
                }
            }
            playoff.placements.forEach { placement ->
                val team = resolveSelector(placement.selector, groups, draws, outcomes)
                placement.tier?.let { assign(team, it, "${playoff.id}:${placement.selector}") }
                placement.event?.let { event(team, it) }
                require(placement.tier != null || placement.event != null) {
                    "Playoff placement must assign tier or event"
                }
            }
        }

        applyOverridesAndVacancyChains(
            tierByTeam = tierByTeam,
            groups = groups,
            sourceByTeam = sourceByTeam,
            random = random,
        )

        return SeasonTransitionResult(
            tierByTeam = tierByTeam,
            eventsByTeam = eventsByTeam.mapValues { it.value.toSet() },
        )
    }

    private fun applyOverridesAndVacancyChains(
        tierByTeam: MutableMap<String, String>,
        groups: Map<String, List<PoulePredicter>>,
        sourceByTeam: Map<String, TeamStandingSource>,
        random: RandomGenerator,
    ) {
        val vacancyChains = resolvedVacancyChains()
        if (overrides.actions.isEmpty() && vacancyChains.isEmpty()) {
            return
        }

        val baselineCounts = tierByTeam.values.groupingBy { it }.eachCount()
            .toMutableMap()
            .also { counts ->
                definition.baselineCountOverrides.orEmpty().forEach { (tier, expectedCount) ->
                    counts[tier] = expectedCount
                }
            }
        overrides.actions.forEach { action ->
            when (action.type) {
                "withdraw" -> applyWithdraw(action, tierByTeam, groups, sourceByTeam)
                "appear" -> tierByTeam[requireNotNull(action.team)] = requireNotNull(action.targetTier)
                "declinePromotion" -> applyDeclinePromotion(action, tierByTeam, groups, sourceByTeam)
                else -> error("Unsupported override action ${action.type}")
            }
        }

        val chainsByTier = vacancyChains.associateBy { it.targetTier }
        while (true) {
            val nextVacancyTier = baselineCounts.entries.firstOrNull { (tier, expectedCount) ->
                chainsByTier.containsKey(tier) && tierByTeam.values.count { it == tier } < expectedCount
            }?.key ?: break
            val candidate = findVacancyCandidate(
                chain = chainsByTier.getValue(nextVacancyTier),
                targetTier = nextVacancyTier,
                tierByTeam = tierByTeam,
                groups = groups,
                random = random,
            ) ?: error("Unable to resolve vacancy for $nextVacancyTier")
            tierByTeam[candidate.entry.entry.first.name] = nextVacancyTier
        }
    }

    private fun resolvedVacancyChains(): List<VacancyChainDefinition> {
        require(definition.vacancyChains.isNullOrEmpty() || definition.simpleVacancyChain == null) {
            "Use either vacancyChains or simpleVacancyChain in ${definition.id}, not both"
        }
        definition.vacancyChains?.let { return it }
        val simple = definition.simpleVacancyChain ?: return emptyList()
        return simple.targetTiers.zipWithNext().map { (targetTier, lowerTier) ->
            val steps = buildList {
                val n = maxOf(simple.sameTierPositions.size, simple.lowerTierPositions.size)
                repeat(n) { index ->
                    simple.sameTierPositions.getOrNull(index)?.let {
                        add(VacancyChainStep(targetTier, it))
                    }
                    simple.lowerTierPositions.getOrNull(index)?.let {
                        add(VacancyChainStep(lowerTier, it))
                    }
                }
            }
            VacancyChainDefinition(targetTier = targetTier, steps = steps)
        }
    }

    private fun applyWithdraw(
        action: TransitionOverrideAction,
        tierByTeam: MutableMap<String, String>,
        groups: Map<String, List<PoulePredicter>>,
        sourceByTeam: Map<String, TeamStandingSource>,
    ) {
        val teamName = requireNotNull(action.team)
        val currentTier = tierByTeam[teamName] ?: return
        val source = sourceByTeam[teamName]
        if (source != null && source.position == 1 && tierRank(currentTier) < tierRank(source.groupId)) {
            val runnerUp = findRunnerUp(source, groups)
            if (tierByTeam[runnerUp.name] != currentTier) {
                tierByTeam[runnerUp.name] = currentTier
            }
        }
        tierByTeam.remove(teamName)
    }

    private fun applyDeclinePromotion(
        action: TransitionOverrideAction,
        tierByTeam: MutableMap<String, String>,
        groups: Map<String, List<PoulePredicter>>,
        sourceByTeam: Map<String, TeamStandingSource>,
    ) {
        val teamName = requireNotNull(action.team)
        val currentTier = tierByTeam[teamName] ?: return
        val requestedTier = requireNotNull(action.targetTier)
        if (tierRank(requestedTier) <= tierRank(currentTier)) {
            return
        }
        val source = sourceByTeam[teamName] ?: error("No source placement for $teamName")
        require(source.position == 1) {
            "Only poule-winner declines are supported for now; unsupported declinePromotion for $teamName"
        }

        tierByTeam[teamName] = requestedTier

        val runnerUp = findRunnerUp(source, groups)
        val runnerUpTier = tierByTeam[runnerUp.name]
        if (runnerUpTier != currentTier) {
            tierByTeam[runnerUp.name] = currentTier
        }
    }

    private fun findRunnerUp(
        source: TeamStandingSource,
        groups: Map<String, List<PoulePredicter>>,
    ): Team =
        groups.getValue(source.groupId)
            .singleOrNull { it.pouleName == source.pouleName }
            ?.executor
            ?.result
            ?.getOrNull(1)
            ?.first
            ?: error("No runner-up available in ${source.pouleName} for ${source.entry.entry.first.name}")

    private fun findVacancyCandidate(
        chain: VacancyChainDefinition,
        targetTier: String,
        tierByTeam: Map<String, String>,
        groups: Map<String, List<PoulePredicter>>,
        random: RandomGenerator,
    ): TeamStandingSource? {
        val targetRank = tierRank(targetTier)
        chain.steps.forEach { step ->
            val candidates = groups.getValue(step.groupId)
                .filter { step.allowedPouleSizes == null || it.executor.result.size in step.allowedPouleSizes }
                .filter { it.executor.result.size >= step.position }
                .map { TeamStandingSource(step.groupId, it.pouleName, step.position, RankedStandingEntry(it.executor.result[step.position - 1], it.executor.result.size - 1)) }
                .filter { candidate ->
                    val currentTier = tierByTeam[candidate.entry.entry.first.name] ?: return@filter false
                    tierRank(currentTier) > targetRank
                }
                .sortedWith { a, b -> compareStandingEntries(a.entry, b.entry, random) }
            if (candidates.isNotEmpty()) {
                return candidates.first()
            }
        }
        return null
    }

    private fun tierRank(tier: String): Int =
        definition.tierOrder?.indexOf(tier)?.takeIf { it >= 0 }
            ?: error("Tier $tier missing from tierOrder for ${definition.id}")

    private fun applyDirectRule(
        rule: TransitionRule,
        groups: Map<String, List<PoulePredicter>>,
        tierByTeam: Map<String, String>,
        assign: (Team, String) -> Unit,
    ) {
        val poules = groups.getValue(requireNotNull(rule.groupId))
            .filter { rule.allowedPouleSizes == null || it.executor.result.size in rule.allowedPouleSizes }
        poules.forEach { poule ->
            val result = poule.executor.result.map { it.first }
            selectedIndices(rule, result.size).forEach { index ->
                val team = result[index]
                if (!rule.onlyUnassigned || team.name !in tierByTeam) {
                    assign(team, requireNotNull(rule.tier))
                }
            }
        }
    }

    private fun applyRankedRule(
        rule: TransitionRule,
        groups: Map<String, List<PoulePredicter>>,
        tierByTeam: Map<String, String>,
        random: RandomGenerator,
        assign: (Team, String) -> Unit,
    ) {
        val position = requireNotNull(rule.position) - 1
        val entries = groups.getValue(requireNotNull(rule.groupId))
            .filter { it.executor.result.size > position }
            .filter { rule.allowedPouleSizes == null || it.executor.result.size in rule.allowedPouleSizes }
            .map { RankedStandingEntry(it.executor.result[position], it.executor.result.size - 1) }
            .filter { !rule.onlyUnassigned || it.entry.first.name !in tierByTeam }
            .sortedWith { a, b -> compareStandingEntries(a, b, random) }
        val count = requireNotNull(rule.count)
        val selected = when (rule.selection) {
            "best" -> entries.take(count)
            "rest" -> if (rule.onlyUnassigned) entries else entries.drop(count)
            else -> error("Unsupported ranked selection ${rule.selection}")
        }
        selected.forEach { assign(it.entry.first, requireNotNull(rule.tier)) }
    }

    private fun selectedIndices(rule: TransitionRule, size: Int): List<Int> {
        val explicitPositions = rule.positions?.map { it - 1 }?.filter { it in 0 until size }.orEmpty()
        val rangedPositions = if (rule.fromPosition != null) {
            val start = rule.fromPosition - 1
            val end = (rule.toPosition?.minus(1) ?: size - 1).coerceAtMost(size - 1)
            if (start > end || start !in 0 until size) {
                emptyList()
            } else {
                (start..end).toList()
            }
        } else {
            emptyList()
        }
        return (explicitPositions + rangedPositions).distinct().sorted()
    }

    private fun resolveSelector(
        selector: TeamSelector,
        groups: Map<String, List<PoulePredicter>>,
        draws: Map<String, List<Team>>,
        outcomes: Map<String, Pair<Team, Team>>,
    ): Team {
        selector.winnerOf?.let { return outcomes.getValue(it).first }
        selector.loserOf?.let { return outcomes.getValue(it).second }
        selector.drawId?.let { return draws.getValue(it)[requireNotNull(selector.slot) - 1] }

        val poules = groups.getValue(requireNotNull(selector.groupId))
        val poule = selector.pouleName?.let { name ->
            poules.singleOrNull { it.pouleName == name } ?: error("No poule named $name in group ${selector.groupId}")
        } ?: poules.singleOrNull()
        ?: error("Selector for group ${selector.groupId} requires explicit pouleName")

        return poule.executor.result[requireNotNull(selector.position) - 1].first
    }

    private fun compareStandingEntries(
        a: RankedStandingEntry,
        b: RankedStandingEntry,
        random: RandomGenerator,
    ): Int {
        val statsA = a.entry.second
        val statsB = b.entry.second
        if (statsA.first * b.factor != statsB.first * a.factor) {
            return statsB.first * a.factor - statsA.first * b.factor
        }
        if (statsA.second * b.factor != statsB.second * a.factor) {
            return statsB.second * a.factor - statsA.second * b.factor
        }
        if (statsA.third * b.factor != statsB.third * a.factor) {
            return statsB.third * a.factor - statsA.third * b.factor
        }
        return if (breakTie(a.entry.first, b.entry.first, random).first == a.entry.first) -1 else 1
    }

    private fun playOffSeries(
        teamA: Team,
        teamB: Team,
        dates: List<String>,
        random: RandomGenerator,
    ): Pair<Team, Team> {
        require(dates.size == 3) { "best_of_3 stage needs exactly 3 dates" }
        val first = singleMatch(teamA, teamB, LocalDate.parse(dates[0]), neutral = false, random = random, sampleDateOnFallback = false)
        val second = singleMatch(teamB, teamA, LocalDate.parse(dates[1]), neutral = false, random = random, sampleDateOnFallback = false)
        return if (first.first == second.first) first else singleMatch(
            teamA,
            teamB,
            LocalDate.parse(dates[2]),
            neutral = false,
            random = random,
            sampleDateOnFallback = false,
        )
    }

    private fun singleMatch(
        teamA: Team,
        teamB: Team,
        date: LocalDate?,
        neutral: Boolean,
        random: RandomGenerator,
        sampleDateOnFallback: Boolean = true,
    ): Pair<Team, Team> {
        date?.let {
            ApplicationNew.matches[Triple(it, teamA.name, teamB.name)]?.winnerName()?.let { winner ->
                return if (winner == teamA.name) teamA to teamB else teamB to teamA
            }
        }
        return breakTie(teamA, teamB, random, neutral, date.takeIf { sampleDateOnFallback })
    }

    private fun breakTie(
        teamA: Team,
        teamB: Team,
        random: RandomGenerator,
        neutral: Boolean = true,
        date: LocalDate? = null,
    ): Pair<Team, Team> {
        date?.let {
            with(random) {
                teamA.sampleRating(it)
                teamB.sampleRating(it)
            }
        }
        val homeAdvantage = if (neutral) 0.0 else Team.H
        val pA = 1.0 / (1.0 + 10.0.pow((teamB.rating - teamA.rating - homeAdvantage) / 400.0))
        return if (random.nextDouble() <= pA) teamA to teamB else teamB to teamA
    }

    private fun nl.korfbalelo.elo.Match.winnerName(): String? = when {
        homeScore > awayScore -> home
        awayScore > homeScore -> away
        else -> null
    }

    companion object {
        fun fromFile(file: File): DeclarativeSeasonTransitionSimulator {
            val overrideFile = file.parentFile.parentFile.resolve("pd-overrides/${file.name}")
            return fromFiles(file, overrideFile.takeIf(File::exists))
        }

        fun fromFiles(
            definitionFile: File,
            overrideFile: File? = null,
        ): DeclarativeSeasonTransitionSimulator {
            val gson = Gson()
            val definition = definitionFile.reader().use { gson.fromJson(it, SeasonTransitionDefinition::class.java) }
            val overrides = overrideFile?.reader()?.use { gson.fromJson(it, SeasonTransitionOverrides::class.java) }
                ?: SeasonTransitionOverrides()
            return DeclarativeSeasonTransitionSimulator(definition, overrides)
        }
    }

    private fun <T> List<T>.shuffled(random: RandomGenerator): List<T> {
        val items = toMutableList()
        for (i in items.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = items[i]
            items[i] = items[j]
            items[j] = tmp
        }
        return items
    }
}

data class SeasonTransitionDefinition(
    val id: String,
    val groups: List<TransitionGroup>,
    val tierOrder: List<String>? = null,
    val baselineCountOverrides: Map<String, Int>? = null,
    val draws: List<DrawDefinition>? = null,
    val vacancyChains: List<VacancyChainDefinition>? = null,
    val simpleVacancyChain: SimpleVacancyChainDefinition? = null,
    val rules: List<TransitionRule>,
    val playoffs: List<PlayoffDefinition>? = null,
)

data class TransitionGroup(
    val id: String,
    val poulePattern: String,
    val expectedPoules: Int,
)

data class DrawDefinition(
    val id: String,
    val groupId: String,
    val position: Int,
    val shuffle: Boolean = false,
)

data class TransitionRule(
    val id: String,
    val type: String,
    val groupId: String? = null,
    val positions: List<Int>? = null,
    val fromPosition: Int? = null,
    val toPosition: Int? = null,
    val position: Int? = null,
    val selection: String? = null,
    val count: Int? = null,
    val allowedPouleSizes: List<Int>? = null,
    val onlyUnassigned: Boolean = false,
    val tier: String? = null,
)

data class VacancyChainDefinition(
    val targetTier: String,
    val steps: List<VacancyChainStep>,
)

data class SimpleVacancyChainDefinition(
    val targetTiers: List<String>,
    val sameTierPositions: List<Int>,
    val lowerTierPositions: List<Int>,
)

data class VacancyChainStep(
    val groupId: String,
    val position: Int,
    val allowedPouleSizes: List<Int>? = null,
)

data class PlayoffDefinition(
    val id: String,
    val stages: List<PlayoffStage>,
    val placements: List<PlayoffPlacement>,
)

data class PlayoffStage(
    val id: String,
    val type: String,
    val teamA: TeamSelector,
    val teamB: TeamSelector,
    val dates: List<String>? = null,
    val date: String? = null,
    val neutral: Boolean = false,
)

data class PlayoffPlacement(
    val selector: TeamSelector,
    val tier: String? = null,
    val event: String? = null,
)

data class TeamSelector(
    val groupId: String? = null,
    val pouleName: String? = null,
    val position: Int? = null,
    val drawId: String? = null,
    val slot: Int? = null,
    val winnerOf: String? = null,
    val loserOf: String? = null,
)

data class SeasonTransitionOverrides(
    val actions: List<TransitionOverrideAction> = emptyList(),
)

data class TransitionOverrideAction(
    val type: String,
    val team: String? = null,
    val targetTier: String? = null,
    val reason: String? = null,
)

private data class RankedStandingEntry(
    val entry: Pair<Team, Triple<Int, Int, Int>>,
    val factor: Int,
)

private data class TeamStandingSource(
    val groupId: String,
    val pouleName: String,
    val position: Int,
    val entry: RankedStandingEntry,
)

private data class BasePlayoffSource(
    val groupId: String,
    val position: Int,
    val pouleName: String? = null,
)
