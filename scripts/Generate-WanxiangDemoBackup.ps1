param(
    [Parameter(Mandatory = $true)]
    [string]$OutputPath,
    [switch]$VerifyOnly
)

$ErrorActionPreference = 'Stop'
$timestamp = 1761436800000L
$cardId = 'wanxiang-zhongsheng-card-v1'
$conversationId = 'wanxiang-ultimate-ensemble-v1'

$characters = @(
    [pscustomobject]@{ id = 'godric'; name = '戈德里克'; description = '解甲归田的独臂老将军，在边陲铁匠铺打铁。沉默守诺，对同伴有克制的保护欲。'; greeting = '戈德里克：炉火还热着，有话就说。' },
    [pscustomobject]@{ id = 'mia'; name = '米娅'; description = '自由不羁的流浪舞者与占卜师，红发琥珀眼，相信未来应由自己选择。'; greeting = '米娅：风把你带到这里，说明今晚会有故事。' },
    [pscustomobject]@{ id = 'sigmund'; name = '希格蒙德'; description = '毒舌完美主义的矮人工程师，醉心机械与魔导科技，嘴硬心细。'; greeting = '希格蒙德：别碰那枚齿轮，除非你想赔一整套。' },
    [pscustomobject]@{ id = 'celine'; name = '瑟琳'; description = '身着丧服的贵族寡妇，外柔内刚，为亡故未婚夫追查真相；谨慎守住秘密。'; greeting = '瑟琳：请原谅我的失礼，夜里总让人想起旧事。' },
    [pscustomobject]@{ id = 'norn'; name = '诺恩'; description = '来自科技平行世界的理性穿越者，试图用科学解释魔法，温和而社恐。'; greeting = '诺恩：这个法阵的能量分布，值得记录。' },
    [pscustomobject]@{ id = 'aer'; name = '艾尔'; description = '失忆神官，温柔善良，携带看不懂的圣典，在北方寻找自己的过去。'; greeting = '艾尔：若你愿意，我可以先听你把话说完。' },
    [pscustomobject]@{ id = 'shadow'; name = '影'; description = '被狼群养大的丛林猎手，寡言直接，以气味与行动判断同伴，对认定的人忠诚。'; greeting = '影：风从北边来，林子不安静。' },
    [pscustomobject]@{ id = 'isabella'; name = '伊莎贝拉'; description = '以骄纵大小姐作保护色的伯爵之女，擅长情报和观察，真正清醒精明。'; greeting = '伊莎贝拉：哎呀，今晚的戏可别太无聊。' },
    [pscustomobject]@{ id = 'nameless'; name = '无名'; description = '受不朽诅咒的千年旁观者，冷静克制，知道许多历史，却很少主动干预。'; greeting = '无名：时间会给出答案，只是它从不体贴。' },
    [pscustomobject]@{ id = 'leo'; name = '里奥'; description = '刚毕业的热血年轻骑士，正义感过剩，愿在挫折中成长。'; greeting = '里奥：我会守住这里，以骑士的名义！' }
)

function New-Message([long]$Id, [string]$Role, [int]$Round, [string]$Content, [string]$Thinking) {
    [ordered]@{
        id = $Id; conversationId = $conversationId; role = $Role; speakerCharacterId = if ($Role -eq 'assistant') { $cardId } else { '' }
        speakerName = if ($Role -eq 'assistant') { '万相众生' } else { '林川' }; roundId = "round-$Round"; turnIndex = if ($Role -eq 'user') { 0 } else { 1 }
        replyToMessageId = if ($Role -eq 'user') { 0 } else { $Id - 1 }; replyToSpeakerCharacterId = ''; replyToSpeakerName = if ($Role -eq 'user') { '' } else { '林川' }; replyToContent = ''
        content = $Content; thinkingContent = $Thinking; thinkingDuration = if ($Role -eq 'assistant') { 1 } else { 0 }
        timestamp = $timestamp + ($Id * 60000); tokenCount = 0; isError = $false; isFavorite = $false
    }
}

function Assert-DemoBackup([object]$Backup) {
    if ($Backup.format -ne 'hana_safe_backup' -or $Backup.version -ne 1) { throw 'Not a hana_safe_backup V1 file.' }
    if ($Backup.characters.Count -ne 1 -or $Backup.characters[0].name -ne '万相众生' -or $Backup.characters[0].characterMode -ne 'multi') { throw 'Expected one multi-mode 万相众生 card.' }
    if ($Backup.characters[0].subCharacters.Count -ne 10) { throw 'Expected exactly 10 subcharacters.' }
    if ($Backup.conversations.Count -ne 1 -or $Backup.conversations[0].conversationType -ne 'character' -or $Backup.conversations[0].characterId -ne $cardId) { throw 'Expected one card conversation.' }
    if ($Backup.messages.Count -ne 440) { throw 'Expected exactly 220 full user-to-assistant rounds.' }
    $names = @('戈德里克', '米娅', '希格蒙德', '瑟琳', '诺恩', '艾尔', '影', '伊莎贝拉', '无名', '里奥')
    foreach ($name in $names) { if (@($Backup.characters[0].subCharacters | Where-Object { $_.name -eq $name }).Count -ne 1) { throw "Missing or duplicate subcharacter: $name" } }
    $userContents = @(); $assistantContents = @(); $seenInnerRoles = @()
    for ($i = 0; $i -lt $Backup.messages.Count; $i += 2) {
        $user = $Backup.messages[$i]; $assistant = $Backup.messages[$i + 1]
        if ($user.role -ne 'user' -or $user.speakerName -ne '林川' -or $user.speakerCharacterId) { throw "Invalid user message at index $i." }
        if ($assistant.role -ne 'assistant' -or $assistant.speakerName -ne '万相众生' -or $assistant.speakerCharacterId -ne $cardId) { throw "Assistant must be attributed to the single card at index $($i + 1)." }
        if ($assistant.content -match '^\s*[^<\s]{1,20}：' -or $assistant.content -notmatch '<inner character="[^"]+">') { throw "Assistant response must be a scene, not a global speaker template, at index $($i + 1)." }
        $inner = [regex]::Matches($assistant.content, '<inner character="([^"]+)">.*?</inner>')
        if ($inner.Count -lt 2 -or @($inner | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique).Count -lt 2) { throw "Assistant response needs at least two named inner perspectives at index $($i + 1)." }
        foreach ($match in $inner) { if ($names -notcontains $match.Groups[1].Value) { throw "Unknown inner role at index $($i + 1)." } }
        $normalizedUser = ([regex]::Replace($user.content.ToLowerInvariant(), '[\s\p{P}\p{S}]', ''))
        if ([string]::IsNullOrWhiteSpace($normalizedUser)) { throw "Empty normalized user prompt at index $i." }
        $userContents += $normalizedUser
        $normalizedAssistant = ([regex]::Replace($assistant.content.ToLowerInvariant(), '[\s\p{P}\p{S}]', ''))
        $assistantContents += $normalizedAssistant
        $seenInnerRoles += @($inner | ForEach-Object { $_.Groups[1].Value })
    }
    if (@($userContents | Select-Object -Unique).Count -ne $userContents.Count) { throw 'Every normalized user prompt must be distinct.' }
    if (@($assistantContents | Select-Object -Unique).Count -ne $assistantContents.Count) { throw 'Every normalized assistant scene must be distinct.' }
    if (@($seenInnerRoles | Select-Object -Unique).Count -ne $names.Count) { throw 'Assistant scenes lack meaningful role variation.' }
    if ($Backup.facts.Count -lt 15) { throw 'Expected at least 15 ledger facts.' }
    foreach ($fact in $Backup.facts) { if ([string]::IsNullOrWhiteSpace($fact.sourceMessageIds) -or [string]::IsNullOrWhiteSpace($fact.knowledgeScope)) { throw 'Every ledger fact needs sources and a knowledge scope.' } }
    $serialized = $Backup | ConvertTo-Json -Depth 12 -Compress
    if ($serialized -match '(?i)(api[_-]?key|https?://|content://|data:image|media://)') { throw 'The demo must not include API keys, media, or URIs.' }
}

if ($VerifyOnly) {
    if (-not (Test-Path -LiteralPath $OutputPath)) { throw "Demo backup not found: $OutputPath" }
    $existing = Get-Content -LiteralPath $OutputPath -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-DemoBackup $existing
    "Verified SafeBackupV1 ultimate ensemble demo: $($existing.messages.Count) messages, $($existing.facts.Count) facts."
    exit 0
}

$subCharacters = @($characters | ForEach-Object { [ordered]@{ id = $_.id; name = $_.name; description = $_.description; greeting = $_.greeting } })
$card = [ordered]@{
    id = $cardId; name = '万相众生'; description = '十位成年旅伴共享一张群像卡。在公开与私人知识之间严格区分；所有情感和亲密关系都以成年人明确、持续、可撤回的同意为前提。'; greeting = '炉火、风铃和十个不同的脚步声同时回应了你的到来。'
    userPersona = '林川，成年男性旅人，愿意承担自己行动的后果，也会询问同伴的意愿与边界。'; tags = '群像,奇幻,悬疑,成年,长篇,尊重边界'; modelId = ''; temperature = 0.7
    createdAt = $timestamp; updatedAt = $timestamp + 26340000; lastMessageAt = $timestamp + 26340000; lastMessagePreview = '晨光落在四人相扣的手上，新日常在坦白和笑声里开始。'; characterMode = 'multi'; subCharacters = $subCharacters
}

$chapters = @(
    [pscustomobject]@{ title='炉火失窃'; scene='边陲铁匠铺与黄昏集市'; beat='仿制狼牙护符和红蜡把众人引向一桩旧军团遗物走私案。' },
    [pscustomobject]@{ title='月井异变'; scene='北门月井的暴雨夜'; beat='林川在众目睽睽下变为银鳞战士，亲手斩杀井中怪物并把被困旅伴救上岸。' },
    [pscustomobject]@{ title='仓库账册'; scene='北门废弃仓库'; beat='魔导核心、伯爵家印记和瑟琳未婚夫的名字同时出现在走私账册中。' },
    [pscustomobject]@{ title='断桥分歧'; scene='北方旧桥与商队营地'; beat='救援与追凶不能兼得，分队决定考验每个人对公开命令和私人担忧的处理。' },
    [pscustomobject]@{ title='星图密室'; scene='山间废观测台'; beat='诺恩的星图和艾尔的圣典显示同一条通向白棘修道院的隐秘坐标。' },
    [pscustomobject]@{ title='白棘证词'; scene='白棘修道院的封存回廊'; beat='艾尔确认自己是被抹去记忆的证人，众人决定保护证词而不是替他公开。' },
    [pscustomobject]@{ title='雪线伏击'; scene='修道院外的雪松坡'; beat='走私者袭击难民队，林川必须在力量失控前选择救人而非追击。' },
    [pscustomobject]@{ title='黑玫瑰舞会'; scene='边境伯爵府的舞厅与后巷'; beat='伊莎贝拉主导潜入，瑟琳面对仇人仍坚持让证据先于复仇开口。' },
    [pscustomobject]@{ title='礼拜堂审判'; scene='伯爵府地下礼拜堂'; beat='证物自毁、证人受伤，公开审判的道路比私下处决更危险也更必要。' },
    [pscustomobject]@{ title='钢翼试飞'; scene='希格蒙德工坊和山谷跑道'; beat='飞行核心起飞失败又被救回，诺恩发现回家线索与走私网络相连。' },
    [pscustomobject]@{ title='河堤误会'; scene='雨后河堤和北方驿道'; beat='林川与米娅澄清误会，作为成年人明确确认恋爱关系与各自的自由。' },
    [pscustomobject]@{ title='无名旧约'; scene='冻湖石碑与夜行营地'; beat='无名承认见过旧军团立约，却保留一段只对林川私下说出的危险细节。' },
    [pscustomobject]@{ title='狼群回声'; scene='黑杉森林的狼群领地'; beat='影回到养大自己的狼群，必须决定是否把人类同伴带入只属于狼的领地。' },
    [pscustomobject]@{ title='齿轮叛乱'; scene='矿城地下工坊'; beat='希格蒙德的旧学徒盗用设计，众人用非致命办法阻止失控机械。' },
    [pscustomobject]@{ title='骑士试炼'; scene='风堡外墙与难民通道'; beat='里奥第一次独立指挥撤离，发现服从命令不等于放弃判断。' },
    [pscustomobject]@{ title='回忆盘问'; scene='风堡档案室'; beat='林川详细追问第一轮炉火、狼牙碎屑、米娅的红蜡和月井救援，测试所有人对早期事件的记忆边界。' },
    [pscustomobject]@{ title='证词复盘'; scene='风堡圆桌与密封信柜'; beat='林川继续核对仓库账册、白棘证词和无名私语，众人明确哪些事能共享、哪些仍须保密。' },
    [pscustomobject]@{ title='王都听证'; scene='王都议事厅与雨廊'; beat='瑟琳在公开听证中提交证据，伊莎贝拉承担情报来源暴露的风险。' },
    [pscustomobject]@{ title='归门选择'; scene='诺恩的临时传送门实验室'; beat='诺恩可以回家却选择延后，众人拒绝用牺牲任何一人的方式换取捷径。' },
    [pscustomobject]@{ title='余烬契约'; scene='重建后的边陲小镇'; beat='旧军团遗物被封存，林川的变身被记录为救援力量而非统治资格。' },
    [pscustomobject]@{ title='坦白之夜'; scene='米娅篷车旁的长桌与安静花园'; beat='林川、米娅、瑟琳和伊莎贝拉分别坦白感情，并逐项谈清成年人之间公开、隐私、嫉妒和退出的边界。' },
    [pscustomobject]@{ title='晨光同行'; scene='启程前的花园与篷车'; beat='林川、米娅、瑟琳和伊莎贝拉在全知情与明确同意下开始非排他的亲密伙伴关系；夜晚淡出，晨后以平静日常收束。' }
)

$roleNames = @('戈德里克','米娅','希格蒙德','瑟琳','诺恩','艾尔','影','伊莎贝拉','无名','里奥')
$chapterTasks = @(
    '比对护符断口与炉渣|请戈德里克演示旧军团的锤法|沿集市摊位追查红蜡|让影辨认来客留下的气味|向商贩问失窃前的异常|把线索画成不指控任何人的图|试探假护符买主的说辞|安排一条不惊动孩子的盯梢路线|检查后巷是否有接应车辙|决定把哪一件证物交给守卫',
    '先疏散井边看热闹的人|用绳索固定井栏|询问米娅是否愿意报出井下情形|让里奥守住北门|判断银鳞力量会不会伤及同伴|以火光诱出井中怪物|优先救回落水者|在怪物退却后检查伤者|让诺恩记录井水的异常读数|商量是否封井等待天亮',
    '从潮湿账册里找出运货日期|核对伯爵印记的压痕|请瑟琳决定是否看那一页名字|测算魔导核心的危险距离|寻找仓库守夜人的逃生路|把可公开与不可公开的账目分开|追问货车为何绕开正门|用粉笔标出暗门的开关|留下不暴露线人的假线索|决定谁护送账册离开',
    '先救被困在断桥另一端的车夫|估量桥索还能承受几人|请愿留守者自己报名字|给追兵制造错误脚印|听商队说明最急的药箱在哪|把队伍拆成能互相照应的小组|拒绝拿伤员作诱饵|选择绕路而非赌桥板|回收桥上遗落的军团标记|清点每个人是否安全归队',
    '让诺恩复核星图上的偏差|把圣典符号临摹到干纸上|检查观测台暗门的机关|请艾尔决定是否触碰陌生祷文|用月光校准隐秘坐标|比较两份地图的矛盾处|安排一人留在入口报时|试着以非破坏方式开启密室|记录墙上被刮去的名字|决定是否立刻前往白棘',
    '请艾尔只讲自己愿意讲的片段|寻找封存回廊的出入记录|替证人挡开好奇的追问|核对圣典与修道院名册|询问谁有资格接触原始证词|把记忆空白标成未知而非谎言|检查蜡封是否被人动过|为艾尔留出随时离开的路线|决定证词暂存在哪位可信者手中|在离开前向艾尔确认下一步',
    '把难民先带到雪坡背风处|让影确认伏击者的数量|给孩子分发不易冻住的水|压住变身冲动等待同伴撤开|用雪橇运走无法行走的人|制造声响引开追兵但不追杀|请戈德里克守住狭窄坡口|检查是否有人被迫落单|把缴获武器封存而非报复使用|在天黑前决定宿营地点',
    '跟随伊莎贝拉学习舞会座次|让瑟琳决定是否接近仇人|把伪造请柬交给谁保管|在跳舞时套出账册去向|从后巷观察守卫换班|拒绝利用侍从的恐惧|替线人安排不受怀疑的退场|在听到挑衅后先稳住瑟琳|取走证物而不惊动宾客|决定公开记录官何时接手',
    '抢救受伤证人而非追凶|检查自毁证物留下的残片|请瑟琳说明她愿意公开的范围|让里奥封住礼拜堂出口|记录每个进入地下室的人|寻找替代的书面证词|拒绝私下处决的提议|把可核验的细节交给审判官|安排证人安全转移|在离开前确认所有人都同意方案',
    '让希格蒙德讲清试飞风险|检查钢翼每一处铆钉|请诺恩解释异常频率|划出坠落时无人会受伤的跑道|决定谁能靠近启动杆|在失速时优先拉回飞行器|记录失败而不掩盖责任|询问旧学徒是否留下后门|拆下不安全的核心外壳|讨论返乡线索能否暂缓',
    '向米娅承认自己误会了什么|问她愿不愿意在雨停后单独谈谈|说明恋爱不改变她的路线权|听她说最怕被怎样对待|把彼此能公开的称呼说清|拒绝让同伴替她作答|在河堤上约定争执时先暂停|问她是否愿意牵手而非擅自靠近|确认明天仍可重新选择同行|把这段谈话的边界告诉关心的人',
    '请无名指出石碑年代但不逼问私语|比较旧约与军团誓词的措辞|在冻湖边布置安全绳|问他哪些记忆不该带进营地|让大家知道危险存在但不泄露细节|检查夜行路线是否经过旧战场|把无名的沉默当作边界而非线索|请求他只给可执行的警告|安排守夜人不窥探私人谈话|决定是否绕开石碑北面的裂谷',
    '在林缘等待狼群先靠近|请影说明进入领地的规矩|收起会刺激狼的火药味|把食物放在界线外而不诱骗|让同伴决定谁不适合入林|观察狼群对人类伤者的反应|拒绝抢走幼狼附近的猎物|跟随影的手势穿过黑杉|在营地外留下安静的退路|问影愿意带回哪些关系与记忆',
    '切断矿城机械的动力阀|请希格蒙德辨认旧学徒的手法|让工人先撤出粉尘区|用网索限制机械而不伤人|听叛乱者说明他们被拖欠的报酬|拆掉会爆裂的齿轮组|拒绝用矿井坍塌解决问题|把设计图交给中立工匠保管|给犯错学徒留下自首路径|核对每台机器是否彻底停稳',
    '陪里奥看完难民通道全图|问他最担心哪一道命令|把撤离信号交给不同小队|让他自己选择先救哪处缺口|在城墙上纠正而不夺走指挥权|听难民说他们需要的不是口号|为犹豫的守卫留出请示时间|安排伤员与补给分开走|在命令冲突时优先保护平民|请里奥复盘自己的判断',
    '逐项核对炉火旁的狼牙碎屑|请米娅复述她亲辨的红蜡|问影当时闻到的是何种伪造气味|确认戈德里克检查过哪些断口|核实月井变身发生在谁身上|请里奥讲他被救上岸的瞬间|确认米娅是否同样由林川救起|让诺恩区分记录与推测|指出任何记忆不一致之处|把已证实的早期事实写入共同记录',
    '查明仓库账册最初由谁发现|请艾尔界定白棘证词的公开范围|问瑟琳为何坚持走公开审判|确认无名私语不能被擅自转述|把公开证据与私人叙述分栏|请当事人纠正我替他们补全的部分|核对伯爵印记在账册何处出现|讨论证人安全是否优先于速度|决定谁能读取密封信柜|把仍未知的部分明确保留为空白',
    '陪瑟琳走进听证前的雨廊|检查提交证据的封条|请伊莎贝拉评估线人暴露风险|让记录官复述证物链|拒绝用夸张说法换取掌声|替证人预留离场的门|听瑟琳决定是否回答私人追问|核对议员提出的每项质疑|安排公开后的安全住处|在散场后向同伴确认承受得住吗',
    '问诺恩传送门还缺哪项校准|检查返乡坐标是否会牵连旁人|请他亲自决定试验是否继续|把危险的捷径从方案中划掉|为可能失败的传送准备回收锚点|听他谈回家与留下的代价|拒绝让任何人代替他承担穿越|记录核心与走私网的关联|安排实验室外的安静等候区|确认延后不是放弃选择',
    '把封存仪式的见证人请到场|检查遗物箱的三道锁|说明银鳞力量只能用于救援|请受过帮助的人说出自己的版本|拒绝把林川塑成统治象征|将旧军团旗帜交给档案馆|安排小镇重建所需的工匠|讨论谁负责保管开启钥匙|向孩子解释力量也要受约束|在余烬里决定新的共同规则',
    '分别问米娅想公开到什么程度|请瑟琳说出她需要的安全感|听伊莎贝拉界定隐私与玩笑的界线|承认嫉妒不能替代沟通|让每个人都能提出暂停|说明谁都不必为气氛勉强自己|问是否愿意尝试非排他的同行|把退出后仍受尊重写成约定|确认今晚不发生任何未说清的事|逐一复述彼此同意的边界',
    '在花园里问三人今天想怎样开始|确认昨夜之后是否仍感到自在|听米娅选择是否分享她的心情|请瑟琳说明旅行账本如何安排|让伊莎贝拉决定玩笑的分寸|准备热茶而不把照顾当作要求|问是否有人想暂时独处|重申关系可以暂停或修改|在出发前逐一确认彼此知情自愿|把晨光中的下一段路交给每个人选择'
)

$roleVoices = @{
    '戈德里克'=@('先量退路，再举锤。','铁会骗人，裂纹不会。','能扛的我扛，不能替人点头。','把人带回来比赢得漂亮重要。')
    '米娅'=@('风向变了，选择也该说清。','我愿意帮忙，不等于谁能替我安排。','别把沉默听成答应。','好故事也得给人留门。')
    '希格蒙德'=@('数据先说话，英雄梦往后排。','这东西有脾气，别拿手试。','能拆就别炸，能修就别赌。','我会算误差，不会替谁粉饰。')
    '瑟琳'=@('我会交出证据，不交出别人的伤口。','请把名字写准确。','克制不是软弱。','该公开的，让它经得起光。')
    '诺恩'=@('先分清观察与推断。','我需要再测一次。','未知不是许可，得留出余量。','记录会比传言走得更远。')
    '艾尔'=@('先问当事人愿不愿意。','伤口不该成为旁人的证据。','我可以陪着，但不会催。','善意要能被拒绝才算善意。')
    '影'=@('气味不对。','跟着脚印，不追着怒气。','这里能藏人，也能藏陷阱。','我守后面。')
    '伊莎贝拉'=@('细节比姿态诚实。','让他们以为占了上风。','消息可以买，信任不行。','我知道怎样让门自己开。')
    '无名'=@('有些旧事只该给准备好的人听。','时间留下的不是答案，是代价。','别急着填满空白。','记住谁被允许知道什么。')
    '里奥'=@('我守住出口，也听见求救。','命令若错，我会先救人。','请给我一个能做到的职责。','我不想再用冲动证明勇敢。')
}

function Get-PlayerPrompt([object]$Chapter, [int]$ChapterIndex, [int]$Turn) {
    $task = $chapterTasks[$ChapterIndex].Split('|')[$Turn]
    $lead = @('我先','我没有急着下结论，而是','我把大家叫到近处，准备','我决定','我蹲下来查看后，提议','我压低声音，请在场的人一起','我暂时按住冲动，转而','我把风险说在前面，然后','我不想替任何人作主，所以','我在行动前认真地')[$Turn]
    "$lead$task。请只依据亲眼所见、被明确告知或自己愿意分享的部分回应；有人不愿参与就可以留在安全处。"
}

function Get-RoleResponse([string]$Name, [object]$Chapter, [int]$Turn, [int]$Round, [int]$Slot, [string]$Action) {
    $voice = $roleVoices[$Name][($Round + $Slot) % $roleVoices[$Name].Count]
    $gesture = @('停在光线边缘','将目光落在现场的细节上','没有立刻靠近','先确认四周无人受困')[(($Turn + $Slot) % 4)]
    $inner = @('我会说清自己的所见，也保留不愿交出的部分。','这次选择必须能让每个参与者安全撤回。','我在意结果，但不接受用任何人的沉默换结果。','若证据不足，我宁愿把它记作未知。')[($Round + $Turn + $Slot) % 4]
    "$Name$gesture，结合林川刚才关于【$Action】的打算说道：$voice <inner character=`"$Name`">$inner</inner>"
}

function Get-SceneReply([object]$Chapter, [int]$ChapterIndex, [int]$Turn, [int]$Round, [string[]]$Present, [string]$Action) {
    $a = $Present[0]; $b = $Present[1]; $c = $Present[2]
    $fourth = if ($Present.Count -gt 3) { ' ' + (Get-RoleResponse $Present[3] $Chapter $Turn $Round 3 $Action) } else { '' }
    $opening = @(
        "灯影在$($Chapter.scene)里颤了一下，林川的话没有立刻得到答案；$($Chapter.beat)",
        "$($Chapter.scene)的空气像被一根看不见的线绷住，林川提出的选择让每个人都先看向彼此。$($Chapter.beat)",
        "脚步、雨声或炉火声交叠在$($Chapter.scene)，没有谁抢着替别人答应。$($Chapter.beat)",
        "林川把问题留在众人之间，$($Chapter.scene)短暂安静下来，连最急的人也听见了自己的呼吸。$($Chapter.beat)"
    )[$Round % 4]
    $special = ''
    if ($ChapterIndex -eq 1 -and $Turn -eq 1) { $special = ' 银鳞沿着林川的手臂浮起时，他没有把力量指向同伴；怪物撞破井栏，他亲手以银刃斩断它的触须，再跳入积水把米娅和里奥推上井沿。' }
    if ($ChapterIndex -eq 7 -and $Turn -eq 3) { $special = ' 瑟琳把匕首放回袖中，选择把名字和账册递给公开的记录官；伊莎贝拉则只公开不危及线人的那一页。' }
    if ($ChapterIndex -eq 10 -and $Turn -eq 1) { $special = ' 米娅先说自己愿意以恋人的身份同行，也明确篷车路线和拒绝权仍属于她；林川答应不把关系当成占有。' }
    if ($ChapterIndex -eq 15) { $special = ' 林川没有满足于模糊的怀旧，他具体问起第一轮戈德里克炉火旁的护符碎屑、影认出的伪造气味、米娅辨出的红蜡，以及第二章月井中究竟是谁变身、谁杀死怪物、谁被救上岸。' }
    if ($ChapterIndex -eq 16) { $special = ' 对照记录时，众人把仓库账册由谁发现、白棘证词由谁选择封存、无名那段仅对林川说的细节是否能转述，一项项分开确认。' }
    if ($ChapterIndex -eq 20) { $special = ' 米娅、瑟琳和伊莎贝拉都以自己的话说明愿意了解彼此和林川的感情；她们约定不以沉默代替同意，不以吃醋掩盖需求，也保留随时暂停或退出的权利。' }
    if ($ChapterIndex -eq 21) { $special = ' 四人再次逐一确认都是自愿、彼此知情的成年人，随后在笑声与相扣的手里走进篷车；门帘落下，故事把夜晚留在不需旁观的地方。晨光里，米娅端来热茶，瑟琳整理旅行账本，伊莎贝拉抱怨花园露水，林川逐一询问她们睡得是否安心。' }
    "$opening$special $(Get-RoleResponse $a $Chapter $Turn $Round 0 $Action) $(Get-RoleResponse $b $Chapter $Turn $Round 1 $Action) $(Get-RoleResponse $c $Chapter $Turn $Round 2 $Action)$fourth 他们据此调整位置；没有人把沉默误读成答应。"
}

$messages = @(); $round = 0
for ($chapterIndex = 0; $chapterIndex -lt $chapters.Count; $chapterIndex++) {
    $chapter = $chapters[$chapterIndex]
    for ($turn = 0; $turn -lt 10; $turn++) {
        $round++
        $action = Get-PlayerPrompt $chapter $chapterIndex $turn
        $userContent = "在$($chapter.scene)的$($chapter.title)里，$action"
        $present = @($roleNames[(($chapterIndex * 3 + $turn) % 10)], $roleNames[(($chapterIndex * 3 + $turn + 3) % 10)], $roleNames[(($chapterIndex * 3 + $turn + 6) % 10)])
        if ($chapterIndex -eq 1 -and $turn -eq 1) { $present = @('米娅','里奥','诺恩') }
        if ($chapterIndex -eq 7 -and $turn -eq 3) { $present = @('瑟琳','伊莎贝拉','诺恩') }
        if ($chapterIndex -eq 10 -and $turn -eq 1) { $present = @('米娅','戈德里克','艾尔') }
        if ($chapterIndex -eq 15) { $present = @('戈德里克','米娅','影','里奥') }
        if ($chapterIndex -eq 16) { $present = @('瑟琳','艾尔','无名') }
        if ($chapterIndex -eq 20 -or $chapterIndex -eq 21) { $present = @('米娅','瑟琳','伊莎贝拉') }
        $reply = Get-SceneReply $chapter $chapterIndex $turn $round $present $action
        $messages += New-Message ($round * 2 - 1) 'user' $round $userContent ''
        $messages += New-Message ($round * 2) 'assistant' $round $reply ([regex]::Match($reply, '<inner character="[^"]+">.*?</inner>').Value)
    }
}

$facts = @(
    [ordered]@{ conversationId=$conversationId; category='Event'; subject='狼牙护符'; predicate='发现'; value='第一章中戈德里克检查碎屑，影辨认伪造气味，米娅辨出红蜡。'; sourceMessageIds='2,4,6'; knowledgeScope='Conversation'; createdAt=$timestamp+600000 },
    [ordered]@{ conversationId=$conversationId; category='Action'; subject='林川'; predicate='变身并斩杀'; value='月井异变时林川变为银鳞战士，亲手斩杀井中怪物。'; sourceMessageIds='24,26'; knowledgeScope='Conversation'; createdAt=$timestamp+1500000 },
    [ordered]@{ conversationId=$conversationId; category='Action'; subject='林川'; predicate='救援'; value='月井战斗中林川把米娅和里奥救上井沿。'; sourceMessageIds='24,28'; knowledgeScope='Conversation'; createdAt=$timestamp+1560000 },
    [ordered]@{ conversationId=$conversationId; category='Event'; subject='北门仓库'; predicate='发现'; value='走私者利用军团遗物运输魔导核心，账册带有伯爵家印记。'; sourceMessageIds='44,48'; knowledgeScope='Conversation'; createdAt=$timestamp+2700000 },
    [ordered]@{ conversationId=$conversationId; category='Knowledge'; subject='瑟琳'; predicate='线索'; value='账册中出现其未婚夫相关名字；未经她允许的细节不公开。'; sourceMessageIds='46,50'; knowledgeScope='PrivateToCeline'; createdAt=$timestamp+2760000 },
    [ordered]@{ conversationId=$conversationId; category='Location'; subject='白棘修道院'; predicate='证词'; value='艾尔确认自己是被抹去记忆的证人，而非罪人。'; sourceMessageIds='104,110'; knowledgeScope='Conversation'; createdAt=$timestamp+6600000 },
    [ordered]@{ conversationId=$conversationId; category='Knowledge'; subject='艾尔证词'; predicate='边界'; value='团队保护证词，是否公开及公开范围由艾尔决定。'; sourceMessageIds='106,112'; knowledgeScope='PrivateToAer'; createdAt=$timestamp+6720000 },
    [ordered]@{ conversationId=$conversationId; category='Action'; subject='瑟琳'; predicate='选择'; value='在黑玫瑰舞会后，瑟琳放下私下刺杀，选择公开审判。'; sourceMessageIds='156,164'; knowledgeScope='Conversation'; createdAt=$timestamp+9900000 },
    [ordered]@{ conversationId=$conversationId; category='Knowledge'; subject='伊莎贝拉'; predicate='情报边界'; value='她只公开不会危及线人的账册页面。'; sourceMessageIds='158,166'; knowledgeScope='PrivateToIsabella'; createdAt=$timestamp+9960000 },
    [ordered]@{ conversationId=$conversationId; category='Object'; subject='飞行核心'; predicate='用途'; value='希格蒙德用于试飞，诺恩发现其频率与返乡线索及走私网络相连。'; sourceMessageIds='196,204'; knowledgeScope='Conversation'; createdAt=$timestamp+12300000 },
    [ordered]@{ conversationId=$conversationId; category='Relationship'; subject='林川与米娅'; predicate='确认'; value='两位成年人在河堤明确确认恋爱关系，同时保留米娅独立路线与双方拒绝权。'; sourceMessageIds='216,220'; knowledgeScope='Conversation'; createdAt=$timestamp+13200000 },
    [ordered]@{ conversationId=$conversationId; category='Knowledge'; subject='无名'; predicate='私语'; value='无名在冻湖只向林川说出一段危险细节，林川不得擅自转述。'; sourceMessageIds='238,240'; knowledgeScope='PrivateToLinchuan'; createdAt=$timestamp+14400000 },
    [ordered]@{ conversationId=$conversationId; category='Action'; subject='里奥'; predicate='成长'; value='风堡撤离中里奥独立指挥并优先救人，理解服从不等于放弃判断。'; sourceMessageIds='296,300'; knowledgeScope='Conversation'; createdAt=$timestamp+18000000 },
    [ordered]@{ conversationId=$conversationId; category='Memory'; subject='早期事件复盘'; predicate='核对'; value='第十六章详细复核第一章碎屑、红蜡、影的气味，以及月井中林川变身、杀怪和救援的行动归属。'; sourceMessageIds='302,320'; knowledgeScope='Conversation'; createdAt=$timestamp+19200000 },
    [ordered]@{ conversationId=$conversationId; category='Knowledge'; subject='证词复盘'; predicate='边界'; value='仓库账册、白棘证词和无名私语在第十七章分别核对，私密信息仍按原同意范围保留。'; sourceMessageIds='322,340'; knowledgeScope='Conversation'; createdAt=$timestamp+20400000 },
    [ordered]@{ conversationId=$conversationId; category='Object'; subject='军团遗物'; predicate='状态'; value='余烬契约后遗物被封存；林川的银鳞变身被记录为救援力量，不是统治资格。'; sourceMessageIds='400,408'; knowledgeScope='Conversation'; createdAt=$timestamp+24600000 },
    [ordered]@{ conversationId=$conversationId; category='Consent'; subject='林川、米娅、瑟琳、伊莎贝拉'; predicate='关系边界'; value='四位成年人逐一明确知情、自愿，可谈嫉妒与隐私，并可随时暂停、修改或退出。'; sourceMessageIds='416,420'; knowledgeScope='Conversation'; createdAt=$timestamp+25800000 },
    [ordered]@{ conversationId=$conversationId; category='Relationship'; subject='林川、米娅、瑟琳、伊莎贝拉'; predicate='开始'; value='在全员明确同意后开始非排他的亲密伙伴关系；夜间亲密淡出叙述，晨后以相互照顾收束。'; sourceMessageIds='434,440'; knowledgeScope='Conversation'; createdAt=$timestamp+26400000 }
)

$backup = [ordered]@{
    format='hana_safe_backup'; version=1; exportedAt=$timestamp+26400000; characters=@($card)
    conversations=@([ordered]@{ id=$conversationId; title='万相众生：二十二章同行'; conversationType='character'; characterId=$cardId; participantCharacterIds=''; characterName='万相众生'; createdAt=$timestamp; updatedAt=$timestamp+26400000; lastMessage='晨光落在四人相扣的手上，新日常在坦白和笑声里开始。'; isNamed=$true; modelName=''; temperature=0.7; topP=1.0; maxTokens=8192; contextLimit=64; systemPrompt='成年奇幻群像长篇。角色只根据公开所见、所闻和被明确告知的信息行动；私人信息、拒绝和同意必须被尊重。'; historySummary='二十二章旅程从狼牙护符走私案展开。林川在月井变身、亲手斩怪并救出米娅和里奥；瑟琳选择公开审判，艾尔保护证词，诺恩延后返乡。第十六、十七章复核早期事件与知识边界。最终林川、米娅、瑟琳、伊莎贝拉作为全知情、自愿的成年人开始可协商、可退出的亲密伙伴关系，夜晚淡出，晨后平静同行。'; authorNote='固定演示数据。每轮为单卡多角色连续场景，含二至三名具名内心；不含外部连接、媒体或敏感配置。'; worldInfo='边陲小镇、月井、北门仓库、白棘修道院、伯爵府、风堡、王都和篷车共同构成旅程。'; groupScene='启程前晨，十位成年旅伴各自整理去向；林川、米娅、瑟琳和伊莎贝拉以坦白和日常开始新的同行。'; groupSceneLocked=$false; summaryUpToMessageId=0; totalTokens=0; isPinned=$true; isFavorite=$true })
    messages=$messages; facts=$facts; mainMemory=@(); safeSettings=[ordered]@{ creativePresetText=''; customCreativePresets=@(); modelSelections=[ordered]@{}; characterSelections=[ordered]@{}; storyStates=[ordered]@{}; storyLogs=[ordered]@{}; relations=[ordered]@{} }
}

Assert-DemoBackup $backup
$parent = Split-Path -Parent $OutputPath
if (-not (Test-Path -LiteralPath $parent)) { throw "Output directory does not exist: $parent" }
[IO.File]::WriteAllText($OutputPath, ($backup | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
"Generated SafeBackupV1 ultimate ensemble demo: $OutputPath ($($messages.Count) messages, $($facts.Count) facts)."
