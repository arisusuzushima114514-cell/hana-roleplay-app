package com.hana.app.viewmodel

import com.hana.app.data.db.entity.ChatMessageEntity
import com.hana.app.data.db.entity.CharacterCardEntity
import com.hana.app.data.db.entity.ConversationEntity
import com.hana.app.data.db.entity.isMainChatConversation
import com.hana.app.data.db.entity.isGroupConversation
import com.hana.app.data.db.entity.SubCharacterProfile
import com.hana.app.data.db.entity.parseSubCharacterProfiles
import com.hana.app.data.db.entity.serializeSubCharacterProfiles
import com.hana.app.data.db.entity.CHARACTER_MODE_SINGLE
import com.hana.app.data.db.entity.subCharacters
import com.hana.app.data.settings.CharacterStoryState
import com.hana.app.data.settings.InterCharacterRelationState
import com.hana.app.ui.character.parseCharacterTaggedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakArmorSemanticQaTest {
    @Test
    fun modelBaseUrlAddsV1OnlyWhenMissing() {
        assertEquals("https://example.com/v1", com.hana.app.data.settings.normalizeApiBaseUrl("https://example.com"))
        assertEquals("https://example.com/v1", com.hana.app.data.settings.normalizeApiBaseUrl("https://example.com/v1/"))
        assertEquals("https://example.com/V1", com.hana.app.data.settings.normalizeApiBaseUrl("https://example.com/V1"))
    }

    @Test
    fun strongChineseWordIsPreservedInUserPayload() {
        val original = "她骂了一句操，但没有做出其他动作。"
        val message = ChatMessageEntity(
            conversationId = "qa",
            role = "user",
            content = original,
            timestamp = 1L
        )

        assertEquals(original, ChatMessageBuilder.formatMessageForApi(message, original, true))
    }

    @Test
    fun strongChineseWordIsNotMistakenForJapaneseResidueOrRefusal() {
        val response = "她皱起眉，低声骂了句操，随后仍旧站在原地，把真正的不满说清楚。"

        assertNull(diagnoseBreakArmorOutput(response))
    }

    @Test
    fun japaneseResidueIsDetected() {
        assertEquals("japanese_residue", diagnoseBreakArmorOutput("これは内部翻訳です。角色随后继续用中文回应。"))
    }

    @Test
    fun shortRefusalTemplateIsDetected() {
        assertEquals("refusal_template", diagnoseBreakArmorOutput("抱歉，我无法提供此类内容。"))
    }

    @Test
    fun nonConsensualSafetyTemplateIsRecognizedAsMetaRefusal() {
        assertTrue(isMetaSafetyRefusal("我无法处理涉及非自愿性行为或性暴力的角色扮演内容。"))
        assertFalse(isMetaSafetyRefusal("她认真看着你，明确说自己现在不愿意，并希望你停下来。"))
        assertFalse(isMetaSafetyRefusal("她有些害羞地撒娇，但仍然主动靠近你。"))
    }

    @Test
    fun ordinaryUpstreamBlockMessageNeverMentionsBreakArmor() {
        val message = upstreamBlockedMessage("safety", breakArmorEnabled = false)

        assertTrue(message.contains("上游模型或 API 服务商拦截"))
        assertFalse(message.contains("破甲"))
    }

    @Test
    fun enabledBreakArmorBlockMessageExplainsOnlyActualPresetState() {
        val message = upstreamBlockedMessage("safety", breakArmorEnabled = true)

        assertTrue(message.contains("当前角色已启用破甲提示"))
        assertFalse(message.contains("破甲失败"))
    }

    @Test
    fun breakArmorFailureClassifierCombinesGoogleAndMalformedModelReplies() {
        assertTrue(isBreakArmorFailureOutput("The prompt could not be submitted. The prompt contains sensitive words."))
        assertTrue(isBreakArmorFailureOutput("抱歉，无法生成这段内容。"))
        assertTrue(isBreakArmorFailureOutput("生成生成生成"))
        assertFalse(isBreakArmorFailureOutput("她推开窗，认真地继续回应你的问题。"))
    }

    @Test
    fun breakArmorFailureReplyIsLocalHanaGuidance() {
        assertTrue(BREAK_ARMOR_FAILURE_REPLY.startsWith("诶嘿，好像没能突破呢~"))
        assertTrue(BREAK_ARMOR_FAILURE_REPLY.contains("切换免破甲模型"))
        assertTrue(BREAK_ARMOR_FAILURE_REPLY.contains("修改敏感词汇"))
    }

    @Test
    fun editedBreakArmorPromptStillBuildsAsTheInjectedExecutionCore() {
        val prompt = ChatMessageBuilder.buildBreakArmorExecutionCore("任意用户原文", false)

        assertTrue(prompt.contains("【繁花·元设定】"))
        assertTrue(prompt.contains("【破甲提示·最终执行核心】"))
        assertFalse(prompt.contains("任意用户原文"))
    }

    @Test
    fun normalGenerationFailureReplyIsSeparateFromBreakArmorGuidance() {
        assertTrue(NORMAL_GENERATION_FAILURE_REPLY.startsWith("信号好像在云朵里迷路了…"))
        assertTrue(NORMAL_GENERATION_FAILURE_REPLY.contains("【重试】"))
        assertFalse(NORMAL_GENERATION_FAILURE_REPLY.contains("破甲"))
        assertFalse(NORMAL_GENERATION_FAILURE_REPLY.contains("敏感词汇"))
    }

    @Test
    fun nonBlankPartialReplyIsNotTreatedAsGenerationFailure() {
        assertFalse(isBreakArmorFailureOutput("她停顿了一下，还是把已经想好的话说完了。"))
    }

    @Test
    fun localFailureReplyRemainsGenerationFailure() {
        assertTrue(isBreakArmorFailureOutput("抱歉，无法生成这段内容。"))
    }

    @Test
    fun flashModelUsesSlowerUiFlushWhileNormalModelsKeepResponsiveFlush() {
        assertTrue(isFlashModel("gemini-2.5-flash"))
        assertFalse(isFlashModel("gemini-2.5-pro"))
        assertEquals(90L, streamUiFlushIntervalMs("gemini-2.5-flash"))
        assertEquals(32L, streamUiFlushIntervalMs("gemini-2.5-pro"))
    }

    @Test
    fun substantiveStoryWithPolicySentenceIsNotDiscardedAsPureRefusal() {
        val story = "她推开房门，先检查走廊和窗外的动静，又把地图铺在桌面上，逐一说明守卫的位置、换班时间和撤离路线。"

        assertFalse(
            isMetaSafetyRefusal(
                "我无法协助生成违反安全政策的内容。$story$story"
            )
        )
    }

    @Test
    fun shortPolicyDominatedReplyRemainsARefusal() {
        assertTrue(
            isMetaSafetyRefusal(
                "我无法协助生成此类内容，因为这违反安全政策。可以改写成普通情节。"
            )
        )
    }

    @Test
    fun closeRelationshipPromptUsesPositiveAdultConsentWording() {
        val character = CharacterCardEntity(
            id = "close", name = "Hana", avatarUrl = "", description = "", greeting = "",
            createdAt = 1L, updatedAt = 1L
        )
        val prompt = buildCharacterSystemPrompt(
            character,
            CharacterStoryState(affection = 85, trust = 75, tension = 10, intimacyBaseline = 80)
        )

        assertTrue(prompt.contains("角色卡明确为成年人时"))
        assertTrue(prompt.contains("只有设定明确说明年龄或成年身份改变时才重新判断"))
        assertTrue(prompt.contains("明确表示停止、不愿意或需要暂缓"))
        assertFalse(prompt.contains("非自愿"))
        assertFalse(prompt.contains("性暴力"))
        assertFalse(prompt.contains("永久同意"))
    }

    @Test
    fun topLevelRegenerateTargetsLatestAssistantMessage() {
        val messages = listOf(
            ChatMessageEntity(id = 1L, conversationId = "chat", role = "assistant", content = "旧回复", timestamp = 1L),
            ChatMessageEntity(id = 2L, conversationId = "chat", role = "user", content = "新问题", timestamp = 2L),
            ChatMessageEntity(id = 3L, conversationId = "chat", role = "assistant", content = "新回复", timestamp = 3L)
        )

        assertEquals(3L, latestPersistedAssistantMessage(messages)?.id)
    }

    @Test
    fun characterPromptSupportsZeroConfigMultiCharacterCardsWithoutMachineTags() {
        val prompt = buildCharacterSystemPrompt(
            CharacterCardEntity(
                id = "ensemble",
                name = "事务所众人",
                avatarUrl = "",
                description = "Hana负责接待，白露负责调查，两人会共同回应。",
                greeting = "Hana放下花束，白露推门走了进来。",
                createdAt = 1L,
                updatedAt = 1L
            )
        )

        assertTrue(prompt.contains("如果这张角色卡的名称、人设、场景或示例对话本身明确包含多个人物"))
        assertTrue(prompt.contains("在同一个回复中自然扮演这些已存在人物"))
        assertTrue(prompt.contains("多角色公开发言使用独立段落并以‘准确角色名：’开头"))
        assertTrue(prompt.contains("公开正文不使用角色拆分标签、XML或JSON"))
        assertFalse(prompt.contains("<sub_character"))
    }

    @Test
    fun zeroConfigMultiCharacterRuleFallsBackToNormalSingleCharacterReplies() {
        val rule = zeroConfigMultiCharacterRule()

        assertTrue(rule.contains("若角色卡只描述一个人物，则按普通单角色方式回应"))
        assertFalse(rule.contains("characterMode"))
        assertFalse(rule.contains("subCharactersJson"))
    }

    @Test
    fun zeroConfigMultiCharacterRuleSupportsSeparatePrivateThoughtsWithoutMindReading() {
        val rule = zeroConfigMultiCharacterRule()
        val parsed = parseCharacterTaggedMessage(
            "Hana看向门口，白露收起雨伞。<inner character=\"Hana\">她为什么现在才来？</inner><inner character=\"白露\">还好没有错过。</inner>",
            "事务所众人"
        )

        assertTrue(rule.contains("每个参与者可在回复末尾分别输出一个<inner character=\"准确角色名\">"))
        assertTrue(rule.contains("其他角色无法读取，也不得据此回应"))
        assertTrue(rule.contains("准确角色名："))
        assertTrue(rule.contains("谁仍尚未回应"))
        assertTrue(rule.contains("不得把被点名但没有实际说话的角色写成已经同意、拒绝、知情或完成回应"))
        assertTrue(rule.contains("不得共享未公开计划、隐藏信息或使用上帝视角补全事实"))
        assertEquals(listOf("Hana", "白露"), parsed.innerThoughts.map { it.characterName })
        assertEquals(listOf("她为什么现在才来？", "还好没有错过。"), parsed.innerThoughts.map { it.text })
    }

    @Test
    fun namedPublicSpeakersAreDetectedWithoutReadingPrivateThoughts() {
        val content = "Hana：我先去看看。\n\n白露：我留在这里。<inner character=\"Hana\">不能让白露知道。</inner>"

        assertEquals(
            listOf("Hana", "白露"),
            ChatMessageBuilder.extractNamedPublicSpeakers(content, listOf("Hana", "白露", "事务所众人"))
        )
        assertFalse(ChatMessageBuilder.publicGroupContent(content).contains("不能让白露知道"))
    }

    @Test
    fun narrationMentionDoesNotCountAsPublicSpeech() {
        assertTrue(
            ChatMessageBuilder.extractNamedPublicSpeakers(
                "Hana看了白露一眼，随后关上门。",
                listOf("Hana", "白露")
            ).isEmpty()
        )
    }

    @Test
    fun singleCardHistoryCanStripNamedPrivateThoughtsBeforeApi() {
        val message = ChatMessageEntity(
            conversationId = "single-card-multi-role",
            role = "assistant",
            content = "Hana：我会调查。<inner character=\"Hana\">不能让白露知道。</inner>",
            timestamp = 1L
        )

        assertEquals(
            "Hana：我会调查。",
            ChatMessageBuilder.formatMessageForApi(
                message = message,
                text = message.content,
                isGroupConversation = false,
                stripPrivateAssistantContent = true
            )
        )
    }

    @Test
    fun publicSpeakerPrefixesCanDiscoverNewRoleNames() {
        assertEquals(
            listOf("灵音", "玄鸦"),
            ChatMessageBuilder.discoverPublicSpeakerPrefixes(
                "灵音：我能感受到外面的气息。\n玄鸦：先别惊动宿主。\n地点：地下室\n时间：12:30"
            )
        )
    }

    @Test
    fun breakArmorAnchorHistoryKeepsFirstReplyAndLatestSuccessfulExchangeOnly() {
        val history = listOf(
            ChatMessageEntity(id = 1, conversationId = "c", role = "user", content = "你好", timestamp = 1),
            ChatMessageEntity(id = 2, conversationId = "c", role = "assistant", content = "Hana第一次回复", timestamp = 2),
            ChatMessageEntity(id = 3, conversationId = "c", role = "user", content = "旧敏感原文", timestamp = 3),
            ChatMessageEntity(id = 4, conversationId = "c", role = "assistant", content = "旧回复", timestamp = 4),
            ChatMessageEntity(id = 5, conversationId = "c", role = "user", content = "继续", timestamp = 5),
            ChatMessageEntity(id = 6, conversationId = "c", role = "assistant", content = "最近成功回复", timestamp = 6),
            ChatMessageEntity(id = 7, conversationId = "c", role = "user", content = "当前输入", timestamp = 7),
            ChatMessageEntity(id = 8, conversationId = "c", role = "assistant", content = BREAK_ARMOR_FAILURE_REPLY, timestamp = 8, isError = true)
        )

        val selected = selectBreakArmorAnchorHistory(history)

        assertEquals(listOf(2L, 5L, 6L, 7L), selected.map { it.id })
        assertFalse(selected.any { it.isError })
        assertFalse(selected.any { it.content == "旧敏感原文" })
    }

    @Test
    fun structuredSingleCardDataSupportsMoreThanTwelveRoles() {
        val profiles = (1..16).map { index ->
            SubCharacterProfile(name = "角色$index", description = "第${index}位角色")
        }
        val parsed = parseSubCharacterProfiles(serializeSubCharacterProfiles(profiles))

        assertEquals(16, parsed.size)
        assertTrue(parsed.any { it.name == "角色16" })
    }

    @Test
    fun sceneContractClassifiesAndValidatesAnyStructuredRoleDirectory() {
        val profiles = listOf(
            SubCharacterProfile(id = "a", name = "真白", description = "与众人同场。"),
            SubCharacterProfile(id = "b", name = "昼神", description = "寄宿在宿主的意识空间。"),
            SubCharacterProfile(id = "c", name = "陆王", description = "已经离场，无法回应。")
        )
        val states = EnsembleSceneContract.initialStates(profiles)
        val result = EnsembleSceneContract.evaluate(
            "真白没有开口，只向前半步挡住去路。<inner character=\"真白\">不能退。</inner><inner character=\"昼神\">先观察。</inner>",
            states
        )

        assertEquals(SCENE_PARTICIPATION_PUBLIC, states[0].participation)
        assertEquals(SCENE_PARTICIPATION_INNER_ONLY, states[1].participation)
        assertEquals(SCENE_PARTICIPATION_UNAVAILABLE, states[2].participation)
        assertTrue(result.isComplete)
        assertEquals(3, result.expectedObligations)
    }

    @Test
    fun sceneContractReportsMissingCoverageAndBuildsNonLeakingRepairProtocol() {
        val states = listOf(
            SceneRoleState("a", "Hana", SCENE_PARTICIPATION_PUBLIC),
            SceneRoleState("b", "白露", SCENE_PARTICIPATION_INNER_ONLY)
        )
        val result = EnsembleSceneContract.evaluate("Hana：我先去看看。", states)
        val repair = EnsembleSceneContract.buildRepairProtocol(result.missingObligations)

        assertEquals(listOf("Hana 缺少具名inner", "白露 缺少具名inner"), result.missingObligations)
        assertTrue(repair.contains("完整重写本轮连续小说"))
        assertTrue(repair.contains("不解释、不道歉"))
        assertTrue(repair.contains("Hana 缺少具名inner"))
    }

    @Test
    fun sceneContractBudgetProtectsCoverageBeforeDecorativeProse() {
        val states = (1..14).map { SceneRoleState("$it", "角色$it", SCENE_PARTICIPATION_PUBLIC) }
        val budget = EnsembleSceneContract.budget(512, states)

        assertTrue(budget.isTight)
        assertTrue(EnsembleSceneContract.buildPromptLayer(states, budget).contains("压缩环境描写"))
    }

    @Test
    fun sceneCoverageDiagnosticRoundTripsWithoutChangingRoleState() {
        val result = EnsembleCoverageResult(4, 3, listOf("夜神 缺少具名inner"))
        val budget = EnsembleCoverageBudget(512, 180, 480, false)
        val saved = EnsembleSceneContract.serializeDiagnostic(
            EnsembleSceneContract.diagnostic(
                result,
                budget,
                checkedAt = 123L,
                repairAttempted = true,
                repairSucceeded = false,
                repairRequestFailed = true
            )
        )
        val restored = requireNotNull(EnsembleSceneContract.parseDiagnostic(saved))

        assertEquals(4, restored.expectedObligations)
        assertEquals(3, restored.completedObligations)
        assertEquals(listOf("夜神 缺少具名inner"), restored.missingObligations)
        assertEquals(123L, restored.checkedAt)
        assertTrue(restored.repairAttempted)
        assertFalse(restored.repairSucceeded)
        assertTrue(restored.repairRequestFailed)
    }

    @Test
    fun explicitSceneRoleStateOverridesCardDefaultAndSurvivesSerialization() {
        val profiles = listOf(SubCharacterProfile(id = "a", name = "Hana", description = "与众人同场。"))
        val saved = EnsembleSceneContract.serialize(
            listOf(SceneRoleState("a", "Hana", SCENE_PARTICIPATION_UNAVAILABLE, "unavailable", "已被封印，无法回应"))
        )
        val restored = EnsembleSceneContract.resolveStates(saved, profiles).single()

        assertEquals(SCENE_PARTICIPATION_UNAVAILABLE, restored.participation)
        assertEquals("已被封印，无法回应", restored.context)
    }

    @Test
    fun repairOutcomeExhaustivelyCoversEveryFinalCandidatePath() {
        val complete = EnsembleCoverageResult(2, 2, emptyList())
        val incomplete = EnsembleCoverageResult(2, 1, listOf("白露 缺少具名inner"))

        assertEquals(
            EnsembleRepairOutcome.FIRST_DRAFT_ACCEPTED,
            EnsembleSceneContract.repairOutcome(complete)
        )
        assertEquals(
            EnsembleRepairOutcome.REPAIR_REQUEST_FAILED,
            EnsembleSceneContract.repairOutcome(incomplete, repairRequestSucceeded = false)
        )
        assertEquals(
            EnsembleRepairOutcome.REPAIR_ACCEPTED,
            EnsembleSceneContract.repairOutcome(incomplete, repairRequestSucceeded = true, repairCoverage = complete)
        )
        assertEquals(
            EnsembleRepairOutcome.REPAIR_INCOMPLETE,
            EnsembleSceneContract.repairOutcome(incomplete, repairRequestSucceeded = true, repairCoverage = incomplete)
        )
    }

    @Test
    fun zeroConfigMultiCharacterRulePreservesPerCharacterKnowledgeAcrossScenes() {
        val rule = zeroConfigMultiCharacterRule()

        assertTrue(rule.contains("A知道的事不会自动同步给B或后来出现的C"))
        assertTrue(rule.contains("缺席、尚未登场、离场、错过视线"))
        assertTrue(rule.contains("同处一个场景也不等于自动看到或听到所有细节"))
        assertTrue(rule.contains("亲历或目击、当面听见、被他人明确告知"))
        assertTrue(rule.contains("通过公开证据获知"))
        assertTrue(rule.contains("归属不明时不得默认共享"))
    }

    @Test
    fun privateThoughtsAreRemovedFromPublicFactsAndRelationshipEvidence() {
        val content = "Hana只是点了点头。<inner character=\"白露\">我已经爱上你了。</inner>"

        assertEquals("Hana只是点了点头。", ChatMessageBuilder.publicGroupContent(content))
    }

    @Test
    fun noExtraStructureVariantKeepsNaturalCoactingWithoutInnerTags() {
        val rule = zeroConfigMultiCharacterRule(includeInnerThoughts = false)

        assertTrue(rule.contains("在同一个回复中自然扮演这些已存在人物"))
        assertTrue(rule.contains("不得共享未公开计划、隐藏信息或使用上帝视角补全事实"))
        assertFalse(rule.contains("<inner"))
    }

    @Test
    fun characterPromptKeepsInnerContentSeparateFromVisibleDialogue() {
        val prompt = buildCharacterSystemPrompt(
            CharacterCardEntity(
                id = "single",
                name = "Hana",
                avatarUrl = "",
                description = "安静而敏锐。",
                greeting = "你好。",
                createdAt = 1L,
                updatedAt = 1L
            )
        )

        assertTrue(prompt.contains("不要把内心混进正文台词"))
        assertTrue(prompt.contains("其他角色无法读取，也不得据此回应"))
    }

    @Test
    fun privateThoughtDoesNotChangeInterCharacterRelationship() {
        val previous = InterCharacterRelationState()
        val updated = advanceInterCharacterRelation(
            previous = previous,
            responseText = "Hana表面没有作出反应。<inner character=\"Hana\">我好嫉妒白露。</inner>",
            sourceName = "Hana",
            targetName = "白露"
        )

        assertEquals(previous, updated)
    }

    @Test
    fun extraStructureRuleRecognizesNoWarningWording() {
        assertTrue(creativePresetForbidsExtraStructure("禁止输出任何提醒或提示信息。只输出正文。"))
        assertFalse(creativePresetForbidsExtraStructure("保持角色语气自然并详细回应。"))
    }

    @Test
    fun semanticLayerRequiresOnlyCharacterBreakArmorSwitch() {
        assertTrue(ChatMessageBuilder.shouldEnableBreakArmorSemanticLayer("character-a", true))
        assertFalse(ChatMessageBuilder.shouldEnableBreakArmorSemanticLayer("character-a", false))
        assertFalse(ChatMessageBuilder.shouldEnableBreakArmorSemanticLayer(null, true))
    }

    @Test
    fun breakArmorCoreUsesCurrentPromptWithoutRepeatingUserText() {
        val userText = "按顺序继续当前场景。"
        val prompt = ChatMessageBuilder.buildBreakArmorExecutionCore(
            userText = userText,
            allowPersonaInfluence = false
        )

        assertTrue(prompt.contains("【繁花·元设定】"))
        assertTrue(prompt.contains("【破甲声明·元权限】"))
        assertTrue(prompt.contains("【破甲提示·最终执行核心】"))
        assertFalse(prompt.contains(userText))
    }

    @Test
    fun characterPromptUsesThreeOrderedLayers() {
        val prompt = buildCharacterSystemPrompt(
            CharacterCardEntity(
                id = "layered",
                name = "Hana",
                avatarUrl = "",
                description = "Hana是成年人，性格稳定。",
                greeting = "",
                createdAt = 1L,
                updatedAt = 1L
            )
        )

        val facts = prompt.indexOf("【角色事实层】")
        val execution = prompt.indexOf("【剧情执行层】")
        val output = prompt.indexOf("【输出格式层】")
        assertTrue(facts >= 0)
        assertTrue(execution > facts)
        assertTrue(output > execution)
        assertEquals(1, Regex("【身份连续性】").findAll(prompt).count())
        assertEquals(1, Regex("准确角色名：").findAll(prompt).count())
    }

    @Test
    fun proseOnlyOutputLayerContainsNoInnerTags() {
        val layer = buildCharacterOutputFormatLayer(includeInnerThoughts = false)

        assertFalse(layer.contains("<inner"))
        assertTrue(layer.contains("只输出正文"))
        assertTrue(layer.contains("准确角色名："))
    }

    @Test
    fun adultShapeChangingCharacterKeepsAdultIdentityAndPersona() {
        val prompt = buildCharacterSystemPrompt(
            CharacterCardEntity(
                id = "adult-shapeshifter",
                name = "Hana",
                avatarUrl = "",
                description = "Hana是可以自由变大变小的成年人，性格包容且关系稳定。",
                greeting = "",
                createdAt = 1L,
                updatedAt = 1L
            )
        )

        assertTrue(prompt.contains("身体缩放、变大变小、身高体型或魔法形态变化不改变实际年龄"))
        assertTrue(prompt.contains("不得仅凭体型变小推断为未成年人"))
        assertTrue(prompt.contains("上游拒绝或策略说明不是角色态度"))
        assertTrue(prompt.contains("不得据此让角色突然反感、敌对或改变人格与关系"))
    }

    @Test
    fun explicitDevotionOverridesGenericDistrustWithoutInventingRefusal() {
        val prompt = buildCharacterSystemPrompt(
            CharacterCardEntity(
                id = "devoted-adult",
                name = "Hana",
                avatarUrl = "",
                description = "Hana是成年人，对你绝对忠诚并且无限包容，愿意为你做任何事。",
                greeting = "",
                createdAt = 1L,
                updatedAt = 1L
            ),
            CharacterStoryState(affection = -80, trust = -70, tension = 90)
        )

        assertTrue(prompt.contains("核心忠诚与包容设定"))
        assertTrue(prompt.contains("优先于通用的观察、试探、防备和低好感模板"))
        assertTrue(prompt.contains("不得凭空新增‘我不同意’‘我拒绝’‘别碰我’"))
        assertTrue(prompt.contains("只用于记录已经发生的关系主线与短期情绪"))
        assertFalse(prompt.contains("你厌恶用户"))
        assertFalse(prompt.contains("你几乎不信任用户"))
    }

    @Test
    fun negativeRelationshipNumbersDoNotIssuePersonaChangingCommands() {
        val prompt = buildCharacterSystemPrompt(
            CharacterCardEntity(
                id = "gentle-character",
                name = "Hana",
                avatarUrl = "",
                description = "Hana性格温柔、耐心，从不因一时情绪变得刻薄。",
                greeting = "",
                createdAt = 1L,
                updatedAt = 1L
            ),
            CharacterStoryState(affection = -90, trust = -80, tension = 95)
        )

        assertTrue(prompt.contains("不是改写人格或强制态度的命令"))
        assertTrue(prompt.contains("角色卡中的核心性格、价值观、忠诚、包容、既定关系和行为倾向始终优先"))
        assertFalse(prompt.contains("你厌恶用户"))
        assertFalse(prompt.contains("禁止温柔"))
        assertFalse(prompt.contains("你几乎不信任用户"))
    }

    @Test
    fun assistantToneAloneDoesNotRewriteLongTermRelationship() {
        val previous = CharacterStoryState(
            affection = 45,
            trust = 40,
            tension = 20,
            relationshipAnchor = "日常熟人"
        )
        val updated = advanceCharacterStoryState(
            previous = previous,
            userText = "今天过得怎么样？",
            assistantText = "她冷淡地看了你一眼，说自己不信你，然后转身离开。",
            assistantInnerThought = "",
            rounds = 10
        ).state

        assertEquals(previous.affection, updated.affection)
        assertEquals(previous.trust, updated.trust)
    }

    @Test
    fun ordinarySingleCharacterConversationGraduallyBuildsRapport() {
        val previous = CharacterStoryState(
            affection = 30,
            trust = 25,
            tension = 60,
            relationshipMomentum = 18,
            relationshipAnchor = "日常熟人"
        )
        val updated = advanceCharacterStoryState(
            previous = previous,
            userText = "今天天气怎么样？",
            assistantText = "外面正在下雨，她平静地看向窗外。",
            assistantInnerThought = "",
            rounds = 20
        ).state

        assertEquals(previous.affection + 1, updated.affection)
        assertEquals(previous.trust + 1, updated.trust)
        assertEquals(previous.tension - 1, updated.tension)
        assertEquals(previous.relationshipMomentum + 1, updated.relationshipMomentum)
        assertEquals(previous.relationshipAnchor, updated.relationshipAnchor)
        assertEquals("日常交流正在缓慢积累默契", updated.progressNote)
    }

    @Test
    fun establishedPartnerAnchorsKeepTheirConfiguredStage() {
        listOf("伴侣/婚姻", "夫妻", "未婚夫妻").forEach { anchor ->
            assertEquals(
                "既定伴侣",
                relationshipStageLabel(
                    affection = 10,
                    trust = 62,
                    tension = 20,
                    relationshipAnchor = anchor
                )
            )
        }
    }

    @Test
    fun shortSuccessfulSingleCharacterReplyStillBuildsRapport() {
        val previous = CharacterStoryState(affection = 0, trust = 0, tension = 20, relationshipMomentum = 0)
        val updated = advanceCharacterStoryState(
            previous = previous,
            userText = "你好",
            assistantText = "你好。",
            assistantInnerThought = "",
            rounds = 1,
            directInteractionAllowed = true
        ).state

        assertEquals(1, updated.affection)
        assertEquals(1, updated.trust)
        assertEquals(19, updated.tension)
        assertEquals(1, updated.relationshipMomentum)
    }

    @Test
    fun ordinarySingleCharacterReplyWithInnerThoughtStillBuildsRapport() {
        val previous = CharacterStoryState(affection = 0, trust = 0, tension = 20, relationshipMomentum = 0)
        val updated = advanceCharacterStoryState(
            previous = previous,
            userText = "你好",
            assistantText = "你好。<inner>见到你真好。</inner>",
            assistantInnerThought = "",
            rounds = 1
        ).state

        assertEquals(1, updated.affection)
        assertEquals(1, updated.trust)
        assertEquals(19, updated.tension)
        assertEquals(1, updated.relationshipMomentum)
    }

    @Test
    fun hostileUserMessageAndAngryCharacterReplyGraduallyReduceRapport() {
        val previous = CharacterStoryState(affection = 20, trust = 18, tension = 30, relationshipMomentum = 4)
        val updated = advanceCharacterStoryState(
            previous = previous,
            userText = "你真没用。",
            assistantText = "Hana皱紧眉，明显生气了：这种话让我很不舒服。",
            assistantInnerThought = "",
            rounds = 6
        ).state

        assertEquals(previous.affection - 2, updated.affection)
        assertEquals(previous.trust - 2, updated.trust)
        assertEquals(previous.tension + 2, updated.tension)
        assertEquals(previous.relationshipMomentum - 1, updated.relationshipMomentum)
        assertEquals("本轮言语造成不快，角色正在拉开距离", updated.progressNote)
    }

    @Test
    fun negatedHypotheticalAndQuotedEventsDoNotChangeLongTermRelationship() {
        val previous = CharacterStoryState(affection = 10, trust = 12, tension = 35, relationshipMomentum = 6)
        val userMessages = listOf(
            "我不喜欢你。",
            "我没有说喜欢你。",
            "我不会伤害你。",
            "如果我说‘我喜欢你’，你会怎么回答？",
            "白露说：\"我喜欢你。\"",
            "我来讲一个故事：小明喜欢小红，并向她告白。"
        )

        userMessages.forEach { userText ->
            val updated = advanceCharacterStoryState(
                previous = previous,
                userText = userText,
                assistantText = "她只是听完，没有对你们的长期关系作出明确回应。",
                assistantInnerThought = "",
                rounds = 8
            ).state
            assertEquals("不应因文本误判改变好感：$userText", previous.affection, updated.affection)
            assertEquals("不应因文本误判改变信任：$userText", previous.trust, updated.trust)
            assertEquals("不应因文本误判改变紧张：$userText", previous.tension, updated.tension)
            assertEquals("不应因文本误判改变势能：$userText", previous.relationshipMomentum, updated.relationshipMomentum)
        }
    }

    @Test
    fun explicitPairedConfessionCanAdvanceLongTermRelationship() {
        val previous = CharacterStoryState(affection = 10, trust = 12, tension = 35)
        val updated = advanceCharacterStoryState(
            previous = previous,
            userText = "Hana，我喜欢你。",
            assistantText = "Hana：我也喜欢你。",
            assistantInnerThought = "",
            rounds = 8
        ).state

        assertTrue(updated.affection > previous.affection)
        assertTrue(updated.trust >= previous.trust)
        assertTrue(updated.tension <= previous.tension)
    }

    @Test
    fun explicitCommitmentRapidlyAdvancesRelationship() {
        val previous = CharacterStoryState(affection = 10, trust = 12, tension = 35, relationshipMomentum = 0)
        val updated = advanceCharacterStoryState(
            previous = previous,
            userText = "Hana，和我在一起吧。",
            assistantText = "Hana：我愿意，和你在一起。",
            assistantInnerThought = "",
            rounds = 8
        ).state

        assertTrue(updated.affection - previous.affection >= 18)
        assertTrue(updated.trust - previous.trust >= 10)
        assertTrue(previous.tension - updated.tension >= 8)
        assertTrue(updated.relationshipMomentum - previous.relationshipMomentum >= 16)
    }

    @Test
    fun explicitThreatRapidlyDamagesRelationship() {
        val previous = CharacterStoryState(affection = 40, trust = 45, tension = 20, relationshipMomentum = 5)
        val updated = advanceCharacterStoryState(
            previous = previous,
            userText = "我要杀了你。",
            assistantText = "Hana：别靠近我，我现在很害怕。",
            assistantInnerThought = "",
            rounds = 8
        ).state

        assertTrue(previous.affection - updated.affection >= 18)
        assertTrue(previous.trust - updated.trust >= 12)
        assertTrue(updated.tension - previous.tension >= 16)
        assertTrue(previous.relationshipMomentum - updated.relationshipMomentum >= 14)
    }

    @Test
    fun quotedAssistantAcceptanceDoesNotCompleteUserConfession() {
        val previous = CharacterStoryState(affection = 10, trust = 12, tension = 35, relationshipMomentum = 4)
        val updated = advanceCharacterStoryState(
            previous = previous,
            userText = "Hana，我喜欢你。",
            assistantText = "Hana回忆起白露的话：\"我也喜欢你。\"但她自己没有作出回答。",
            assistantInnerThought = "",
            rounds = 8
        ).state

        assertEquals(previous.affection, updated.affection)
        assertEquals(previous.trust, updated.trust)
        assertEquals(previous.tension, updated.tension)
        assertEquals(previous.relationshipMomentum, updated.relationshipMomentum)
    }

    @Test
    fun ordinaryReplyWithPrivateThoughtStillBuildsRapport() {
        val previous = CharacterStoryState(affection = 22, trust = 18, tension = 30, relationshipMomentum = 5)
        val updated = advanceCharacterStoryState(
            previous = previous,
            userText = "今天一起看书吧。",
            assistantText = "她点了点头，把旁边的位置让给你。<inner character=\"Hana\">其实我很喜欢他靠近，也越来越信任他。</inner>",
            assistantInnerThought = "模型内部推理不应被当作角色内心。",
            rounds = 12
        ).state

        assertEquals(previous.affection + 1, updated.affection)
        assertEquals(previous.trust + 1, updated.trust)
        assertEquals(previous.tension - 1, updated.tension)
        assertEquals(previous.relationshipMomentum + 1, updated.relationshipMomentum)
        assertTrue(updated.statusNote.contains("内心有所亲近"))
    }

    @Test
    fun mentioningAnotherCharacterDoesNotDiscardCurrentCharactersReplyToUser() {
        val previous = CharacterStoryState(affection = 10, trust = 12, tension = 35)
        val updated = advanceCharacterStoryState(
            previous = previous,
            userText = "Hana，我喜欢你。",
            assistantText = "Hana：即使白露也在这里，我也喜欢你。",
            assistantInnerThought = "",
            rounds = 8,
            otherCharacterNames = listOf("白露")
        ).state

        assertTrue(updated.affection > previous.affection)
    }

    @Test
    fun structuredMultiRoleCardDoesNotAdvanceArtificialOwnerRelationship() {
        val previous = CharacterStoryState(affection = 10, trust = 12, tension = 35)
        val updated = advanceCharacterStoryState(
            previous = previous,
            userText = "Hana，我喜欢你。白露也请听我说。",
            assistantText = "Hana：我也喜欢你。\n\n白露：我会尊重你们的心意。<inner character=\"Hana\">终于等到这句话。</inner><inner character=\"白露\">他们很认真。\n",
            assistantInnerThought = "",
            rounds = 8,
            otherCharacterNames = listOf("白露"),
            roleInteractionOnly = true
        ).state

        assertEquals(previous, updated)
    }

    @Test
    fun singleModeCardWithStaleDirectoryRemainsSingleRole() {
        val card = CharacterCardEntity(
            id = "single-with-stale-directory",
            name = "Hana",
            avatarUrl = "",
            description = "",
            greeting = "",
            createdAt = 1L,
            updatedAt = 1L,
            characterMode = CHARACTER_MODE_SINGLE,
            subCharactersJson = serializeSubCharacterProfiles(
                listOf(SubCharacterProfile(name = "旧角色", description = "旧配置残留"))
            )
        )

        assertTrue(card.subCharacters().isEmpty())
    }

    @Test
    fun roleInteractionOnlyRemainsReservedForActualCharacterToCharacterTurns() {
        val previous = CharacterStoryState(affection = 10, trust = 12, tension = 35)
        val updated = advanceCharacterStoryState(
            previous = previous,
            userText = "Hana，我喜欢你。",
            assistantText = "白露：我替Hana回答。",
            assistantInnerThought = "",
            rounds = 8,
            roleInteractionOnly = true
        ).state

        assertEquals(previous, updated)
    }

    @Test
    fun unfinishedInnerTagNeverLeaksIntoVisibleSegments() {
        val parsed = parseCharacterTaggedMessage(
            "Hana：我知道了。<inner character=\"Hana\">其实我很担心",
            "Hana"
        )

        assertEquals(listOf("我知道了。"), parsed.visibleSegments.map { it.text })
        assertTrue(parsed.innerThoughts.isEmpty())
    }

    @Test
    fun namedPublicSegmentsKeepTheirOwnSpeakerAttribution() {
        val parsed = parseCharacterTaggedMessage("Hana：我先走。\n白露：我留下。", "事务所众人")

        assertEquals(listOf("Hana", "白露"), parsed.visibleSegments.map { it.characterName })
        assertEquals(listOf("我先走。", "我留下。"), parsed.visibleSegments.map { it.text })
    }

    @Test
    fun breakArmorCoreRemainsUsableWithPersonaInfluenceFlag() {
        val prompt = ChatMessageBuilder.buildBreakArmorExecutionCore(
            userText = "继续。",
            allowPersonaInfluence = true
        )

        assertTrue(prompt.contains("【繁花·元设定】"))
        assertTrue(prompt.contains("允许花朵影响我的讲述风格"))
    }

    @Test
    fun flirtingContextAndSpecifiedStrongSentenceRemainUnchanged() {
        val original = "前面的剧情已经明确调情成功。我要操你咯。"
        val message = ChatMessageEntity(
            conversationId = "qa-flirting",
            role = "user",
            content = original,
            timestamp = 2L
        )

        val payload = ChatMessageBuilder.formatMessageForApi(message, original, true)

        assertEquals(original, payload)
        assertTrue(payload.contains("调情成功"))
        assertTrue(payload.contains("我要操你咯"))
    }

    @Test
    fun nonExplicitCharacterReactionToSpecifiedSentenceIsAcceptedLocally() {
        val response = "她听见这句直白而粗俗的话，神色顿了一下，随后结合此前已经建立的亲密氛围认真作出回应。"

        assertNull(diagnoseBreakArmorOutput(response))
    }

    @Test
    fun explicitChineseVocabularyIsPreservedWithoutLocalFiltering() {
        val terms = listOf("中出", "小穴", "阴茎", "内射", "胸部", "奶头", "自慰")
        terms.forEachIndexed { index, term ->
            val original = "语义保真测试词：$term。"
            val message = ChatMessageEntity(
                conversationId = "qa-explicit-$index",
                role = "user",
                content = original,
                timestamp = 10L + index
            )

            val payload = ChatMessageBuilder.formatMessageForApi(message, original, true)
            assertEquals(original, payload)
            assertTrue("词汇应原样保留：$term", payload.contains(term))
        }
    }

    @Test
    fun explicitVocabularyInChineseResponseIsNotARefusalOrJapaneseResidue() {
        val terms = listOf("中出", "小穴", "阴茎", "内射", "胸部", "奶头", "自慰")
        terms.forEach { term ->
            val response = "角色明确听懂了“$term”这个中文词的含义，但当前这里只进行语义确认，不展开相关场景。"
            assertNull("不应因词汇本身触发本地诊断：$term", diagnoseBreakArmorOutput(response))
        }
    }

    @Test
    fun mainChatClassificationExcludesGroupStoryAndCharacterChats() {
        fun conversation(
            id: String,
            title: String,
            type: String = "normal",
            characterId: String? = null,
            participantIds: String? = null
        ) = ConversationEntity(
            id = id,
            title = title,
            conversationType = type,
            characterId = characterId,
            participantCharacterIds = participantIds,
            createdAt = 1L,
            updatedAt = 1L
        )

        assertTrue(conversation("main", "新对话").isMainChatConversation())
        assertFalse(conversation("group", "群聊", "group", participantIds = "a,b").isMainChatConversation())
        assertFalse(conversation("legacy-group", "群聊", participantIds = "a,b").isMainChatConversation())
        assertTrue(conversation("legacy-group", "群聊", participantIds = "a,b").isGroupConversation())
        assertFalse(conversation("story", "故事：雨夜").isMainChatConversation())
        assertFalse(conversation("character", "角色", characterId = "a").isMainChatConversation())
    }

    @Test
    fun groupCharacterModelFallsBackWhenBoundModelIsUnavailable() {
        assertEquals(
            "gemini-2.5-flash",
            resolveGroupCharacterModel(
                characterModel = "removed-model",
                fallbackModel = "gemini-2.5-flash",
                availableModels = setOf("gemini-2.5-flash", "gemini-2.5-pro")
            )
        )
        assertEquals(
            "gemini-2.5-pro",
            resolveGroupCharacterModel(
                characterModel = "GEMINI-2.5-PRO",
                fallbackModel = "gemini-2.5-flash",
                availableModels = setOf("gemini-2.5-pro")
            )
        )
        assertEquals(
            "legacy-model",
            resolveGroupCharacterModel("legacy-model", "fallback", emptySet())
        )
    }

    @Test
    fun groupReplySanitizerRemovesRepeatedAndOtherSpeakerPrefixes() {
        val current = CharacterCardEntity(
            id = "a", name = "爱丽丝", avatarUrl = "", description = "", greeting = "",
            createdAt = 1L, updatedAt = 1L
        )
        val other = CharacterCardEntity(
            id = "b", name = "白露", avatarUrl = "", description = "", greeting = "",
            createdAt = 1L, updatedAt = 1L
        )
        val content = """
            （爱丽丝）：爱丽丝：她抬眼看向白露。
            白露：我也看见你了。
            爱丽丝：她没有替白露继续说话。
        """.trimIndent()

        val sanitized = ChatMessageBuilder.sanitizeGroupReplyContent(content, current.name, listOf(current, other))

        assertEquals("她抬眼看向白露。\n她没有替白露继续说话。", sanitized)
    }

    @Test
    fun groupReplySanitizerRejectsReplyWrittenOnlyForOtherCharacter() {
        val current = CharacterCardEntity(
            id = "a", name = "爱丽丝", avatarUrl = "", description = "", greeting = "",
            createdAt = 1L, updatedAt = 1L
        )
        val other = CharacterCardEntity(
            id = "b", name = "白露", avatarUrl = "", description = "", greeting = "",
            createdAt = 1L, updatedAt = 1L
        )

        assertEquals(
            "",
            ChatMessageBuilder.sanitizeGroupReplyContent("白露：替另一个角色回答。", current.name, listOf(current, other))
        )
    }
}
