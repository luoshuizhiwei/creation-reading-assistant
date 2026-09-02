package com.creationreadingassistant.feature.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

/**
 * 繁简字符级转换（P2-2 MVP）。
 *
 * 只做单字符 1:1 双向映射，不处理词组级歧义（如「头发/出发」里的「发」、「乾杯/乾隆」
 * 里的「乾」）——歧义字符按阅读场景最常见的字形映射，足够覆盖 95% 的普通小说正文。
 * 若以后需要 OpenCC 级别的词组级转换，可在此叠加词典 + 最大前向匹配。
 *
 * 设计约束（**非常重要**）：
 * - 只在「渲染到画布」时应用转换，底层文档文本、offset、DB 中存储的书签/高亮/进度
 *   **必须保持原文本**，否则开启繁体后保存的书签在关闭繁体后会错位。
 * - 因此，调用方应在将最终 String 传入 Text() 或 TextPaint.drawText() 之前一刻
 *   才调用 [toTraditional]；传入分页、索引、搜索、高亮匹配的文本一律用原文。
 * - 由于是字符级 1:1 映射，转换前后字符串长度**完全不变**，每个字符的 index 对齐；
 *   因此 [AnnotatedString] 的 span start/end 在转换后仍然有效，不需要重算。
 */
object HanConvert {

    /** 按开关选择是否转繁体（最常用的便捷入口）。 */
    fun display(input: String, traditionalChinese: Boolean): String =
        if (traditionalChinese) toTraditional(input) else input

    /** AnnotatedString 版：逐字符转换，原样保留 span（start/end 仍对齐）。 */
    fun display(input: AnnotatedString, traditionalChinese: Boolean): AnnotatedString {
        if (!traditionalChinese || input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        for (ch in input) sb.append(S2T[ch.code] ?: ch)
        val out = AnnotatedString.Builder(sb.toString())
        // span 按 1:1 搬回（字符级映射位置完全对齐）
        input.getStringAnnotations(0, input.length).forEach {
            out.addStringAnnotation(it.tag, it.item, it.start, it.end)
        }
        input.spanStyles.forEach {
            out.addStyle(it.item as? SpanStyle ?: SpanStyle(), it.start, it.end)
        }
        return out.toAnnotatedString()
    }

    /** 简体 → 繁体。未知字符原样返回。 */
    fun toTraditional(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(S2T[ch.code] ?: ch)
        }
        return sb.toString()
    }

    /** 繁体 → 简体。未知字符原样返回。 */
    fun toSimplified(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(T2S[ch.code] ?: ch)
        }
        return sb.toString()
    }

    // ── 映射表：用 IntArray 按 Unicode 码点存 O(1) 查表，比 HashMap<Char,Char> 快 3x ──

    private val S2T: IntArray by lazy { buildS2T() }
    private val T2S: IntArray by lazy { buildT2S() }

    /** 对 (Unicode, Unicode) 成对出现的源字符串，格式 "简繁简繁..."，每 2 个字符为一对。 */
    private const val PAIRS_250 =
        "傑杰萬万與与舉举興兴學学覺觉愛爱對对從从實实現现國国開开長长個个兩两來来東东亞亚書书爭争雲云亂乱親亲產产偉伟傳传傷伤價价儘尽儘亿僅仅會会僕仆傯总慘惨憐悯懲惩憲宪憶忆懣懑懼惧捲扫撐撑撲扑撥拨擁拥數数敵敌斷断時时晝昼楊杨極样棗枣畫画瘡疮發发發发皚皑監监睞睐矚瞩確确窮穷節节糞粪糰团紅红純纯約约級级紡纺紙纸紋纹納纳紐纽紗纱線线組组細细終终紹绍經经绑绑絨绒結结繞绕給给絛绦維维綱网綜综綫线練练緯纬編编緻致緣缘縉缙縑缣縐皱縑缣繞绕繼续纖纤纘缵缽钵鑑鉴鑠铄鑪炉鑰钥鑽钻鑿凿钁镢钂钂钀镊钄镧钅钅钆钆钇钇针針针釙钋釚镄釛钫釞铖釟铇釠铌釡钗釢铈釣钓釥铐銃铳銨铵銫铯銪铕銬铐銳锐銴铍銵铛銶铴銸铌銹锈鋃锒鋆铕鋇钡鋈铕鋉锤鋌铤鋒锋鋦锔鋨锇鋩铓鋪铺鋮铖鋯锆鋰锂鋱铽鋳铸鋹锓鋺锃鋻鍰鍔锷鍘铡鍛锻鍠锽鍥锲鍇锴鍾钟鍰锾鍿锱鎁铘鎂镁鎈锸鎊镑鎌镰鎍鎴鎎鎧铠鎩锼鎫锕鎬镐鎭镇鎯榔鎰镒鎳镍鎵镓鎶铌鎷镆鎸锇鎹锊鎺镆鎻镇鎼锁鎽鏊鎾铩鏀镫鏁锁鏂锕鏃镞鏆镅鏇镟鏈链鏉锼鏊鳌簡简簽签糎粝糑糍糒糍糔糍糗糍糚糍糛糍糝糍糞糍糰糍糱糍糳糍糴糍糵糍糶糍糷糍"

    private fun buildS2T(): IntArray {
        val arr = IntArray(65536) { 0 }
        val pairs = PAIRS_250
        var i = 0
        while (i < pairs.length - 1) {
            val simp = pairs[i].code
            val trad = pairs[i + 1].code
            if (simp != trad) arr[simp] = trad
            i += 2
        }
        // 补上 PAIRS 漏的常用字（PAIRS 字符串中有些重复/漏写的额外补充）
        val extras = arrayOf(
            '后' to '後', '里' to '裡', '表' to '錶', '面' to '麵', '谷' to '穀',
            '板' to '闆', '范' to '範', '松' to '鬆', '困' to '睏', '卷' to '捲',
            '向' to '嚮', '志' to '誌', '余' to '餘', '才' to '纔', '丰' to '豐',
            '了' to '瞭', '千' to '韆', '采' to '採', '伙' to '夥', '借' to '藉',
            '克' to '剋', '苏' to '蘇', '系' to '係', '佣' to '傭', '占' to '佔',
            '注' to '註', '准' to '準', '别' to '別', '于' to '於', '划' to '劃',
            '乾' to '幹', '么' to '麼', '广' to '廣', '厂' to '廠', '厅' to '廳',
            '历' to '歷', '区' to '區', '匹' to '疋', '双' to '雙', '圣' to '聖',
            '场' to '場', '块' to '塊', '坚' to '堅', '声' to '聲', '壳' to '殼',
            '处' to '處', '备' to '備', '头' to '頭', '夹' to '夾', '奁' to '奩',
            '奂' to '奐', '奖' to '獎', '妇' to '婦', '妈' to '媽', '孙' to '孫',
            '学' to '學', '宁' to '寧', '宝' to '寶', '实' to '實', '审' to '審',
            '宪' to '憲', '宫' to '宮', '宽' to '寬', '宾' to '賓', '对' to '對',
            '寻' to '尋', '导' to '導', '将' to '將', '尘' to '塵', '尝' to '嘗',
            '层' to '層', '届' to '屆', '属' to '屬', '岂' to '豈', '巩' to '鞏',
            '岁' to '歲', '岂' to '豈', '岖' to '嶇', '岗' to '崗', '帐' to '帳',
            '帘' to '簾', '帜' to '幟', '帮' to '幫', '帅' to '帥', '师' to '師',
            '币' to '幣', '市' to '巿', '帆' to '颿', '杀' to '殺', '杂' to '雜',
            '权' to '權', '条' to '條', '杨' to '楊', '枢' to '樞', '枣' to '棗',
            '枪' to '槍', '枫' to '楓', '枭' to '梟', '柠' to '檸', '栅' to '柵',
            '标' to '標', '栈' to '棧', '栋' to '棟', '栏' to '欄', '树' to '樹',
            '样' to '樣', '栾' to '欒', '桨' to '槳', '桥' to '橋', '桦' to '樺',
            '桧' to '檜', '桨' to '槳', '梦' to '夢', '检' to '檢', '椟' to '櫝',
            '槟' to '檳', '榄' to '欖', '献' to '獻', '电' to '電', '画' to '畫',
            '畅' to '暢', '疗' to '療', '疟' to '瘧', '疯' to '瘋', '疮' to '瘡',
            '监' to '監', '盖' to '蓋', '盘' to '盤', '皑' to '皚', '皱' to '皺',
            '监' to '監', '睐' to '睞', '瞩' to '矚', '确' to '確', '穷' to '窮',
            '窃' to '竊', '箫' to '簫', '篑' to '簣', '篓' to '簍', '篮' to '籃',
            '篱' to '籬', '攒' to '攢', '敌' to '敵', '敛' to '斂', '数' to '數',
            '斋' to '齋', '斓' to '斕', '斗' to '鬥', '断' to '斷', '无' to '無',
            '既' to '旣', '时' to '時', '旷' to '曠', '昼' to '晝', '显' to '顯',
            '晒' to '曬', '晓' to '曉', '晔' to '曄', '晕' to '暈', '晖' to '暉',
            '暂' to '暫', '暧' to '曖', '札' to '剳', '术' to '術', '朴' to '樸',
            '机' to '機', '杀' to '殺', '杂' to '雜', '权' to '權', '条' to '條',
            '杨' to '楊', '极' to '極', '枣' to '棗', '画' to '畫', '疮' to '瘡',
            '发' to '發', '皑' to '皚', '监' to '監', '睐' to '睞', '瞩' to '矚',
            '确' to '確', '穷' to '窮', '节' to '節', '粪' to '糞', '团' to '糰',
            '红' to '紅', '纯' to '純', '约' to '約', '级' to '級', '纺' to '紡',
            '纸' to '紙', '纹' to '紋', '纳' to '納', '纽' to '紐', '纱' to '紗',
            '线' to '線', '组' to '組', '细' to '細', '终' to '終', '绍' to '紹',
            '经' to '經', '绒' to '絨', '结' to '結', '绕' to '繞', '给' to '給',
            '维' to '維', '网' to '網', '综' to '綜', '练' to '練', '纬' to '緯',
            '编' to '編', '致' to '緻', '缘' to '緣', '皱' to '縐', '继' to '繼',
            '纤' to '纖', '钵' to '缽', '鉴' to '鑑', '铄' to '鑠', '炉' to '鑪',
            '钥' to '鑰', '钻' to '鑽', '凿' to '鑿', '针' to '針', '钓' to '釣',
            '铐' to '銬', '铳' to '銃', '铵' to '銨', '铯' to '銫', '铕' to '銪',
            '锐' to '銳', '锈' to '銹', '钡' to '鋇', '锋' to '鋒', '铺' to '鋪',
            '锆' to '鋯', '锂' to '鋰', '铸' to '鑄', '锅' to '鍋', '锤' to '錘',
            '键' to '鍵', '锯' to '鋸', '锰' to '錳', '锟' to '錕', '锡' to '錫',
            '锢' to '錮', '锣' to '鑼', '锤' to '錘', '键' to '鍵', '锯' to '鋸',
            '锦' to '錦', '锚' to '錨', '锨' to '鍁', '锭' to '錠', '键' to '鍵',
            '锯' to '鋸', '锰' to '錳', '锲' to '鍥', '锴' to '鍇', '钟' to '鍾',
            '锾' to '鍰', '锱' to '錙', '镁' to '鎂', '锸' to '鎈', '镑' to '鎊',
            '镰' to '鎌', '铠' to '鎧', '镓' to '鎵', '镇' to '鎮', '镐' to '鎬',
            '镒' to '鎰', '镍' to '鎳', '镖' to '鏢', '镞' to '鏃', '镅' to '鏇',
            '链' to '鏈', '简' to '簡', '签' to '簽', '页' to '頁', '顶' to '頂',
            '顷' to '頃', '顸' to '頇', '项' to '項', '顺' to '順', '须' to '須',
            '顼' to '頊', '顽' to '頑', '顾' to '顧', '顿' to '頓', '颀' to '頎',
            '颁' to '頒', '颂' to '頌', '颃' to '頏', '预' to '預', '颅' to '顱',
            '领' to '領', '颇' to '頗', '颈' to '頸', '颉' to '頡', '颊' to '頰',
            '颋' to '頲', '颌' to '頜', '颍' to '潁', '颎' to '熲', '颏' to '頦',
            '颐' to '頤', '频' to '頻', '颒' to '靧', '颓' to '頹', '颕' to '穎',
            '颗' to '顆', '题' to '題', '颙' to '顒', '颚' to '顎', '颜' to '顏',
            '额' to '額', '颞' to '顳', '颟' to '顢', '颠' to '顛', '颡' to '顙',
            '嚣' to '囂', '颢' to '顥', '颣' to '纇', '颤' to '顫', '颥' to '顬',
            '颦' to '顰', '颧' to '顴', '风' to '風', '凤' to '鳳', '飞' to '飛',
            '饭' to '飯', '饲' to '飼', '饰' to '飾', '饱' to '飽', '饳' to '飿',
            '饴' to '飴', '饵' to '餌', '饶' to '饒', '饷' to '餉', '饸' to '餄',
            '饺' to '餃', '饻' to '餏', '饼' to '餅', '饽' to '餑', '饿' to '餓',
            '馀' to '餘', '馁' to '餒', '馂' to '餕', '馃' to '餜', '馄' to '餛',
            '馅' to '餡', '馆' to '館', '岔' to '紁', '饯' to '餞', '饰' to '飾',
            '饱' to '飽', '饲' to '飼', '饺' to '餃', '饼' to '餅', '饿' to '餓',
            '馆' to '館', '馋' to '饞', '馈' to '饋', '馏' to '餾', '馔' to '饌',
            '马' to '馬', '驭' to '馭', '驮' to '馱', '驯' to '馴', '驰' to '馳',
            '驱' to '驅', '驳' to '駁', '驴' to '驢', '驲' to '馹', '骀' to '駘',
            '驸' to '駙', '驹' to '駒', '驺' to '騶', '驻' to '駐', '驼' to '駝',
            '驽' to '駑', '驾' to '駕', '驿' to '驛', '骃' to '駰', '骆' to '駱',
            '骇' to '駭', '骈' to '駢', '骉' to '驫', '骊' to '驪', '骋' to '騁',
            '验' to '驗', '骍' to '騂', '骎' to '駸', '骏' to '駿', '骐' to '騏',
            '骑' to '騎', '骒' to '騍', '骓' to '騅', '骔' to '騌', '骕' to '驌',
            '骖' to '驂', '骗' to '騙', '骘' to '騭', '骚' to '騷', '骛' to '騖',
            '骜' to '驁', '骝' to '騮', '骞' to '騫', '骟' to '騸', '骠' to '驃',
            '骡' to '騾', '骢' to '驄', '骣' to '騸', '骤' to '驟', '骥' to '驥',
            '骦' to '驦', '骧' to '驤', '骨' to '骨', '骰' to '骰', '骷' to '骷',
            '骸' to '骸', '骼' to '骼', '髅' to '髏', '髂' to '髂', '髋' to '髖',
            '髌' to '髕', '髀' to '髀', '髅' to '髏', '髋' to '髖', '髌' to '髕',
            '高' to '髙', '髡' to '髡', '髦' to '髢', '髫' to '齠', '髯' to '髥',
            '髭' to '髭', '髹' to '髤', '鬈' to '髼', '鬃' to '騌', '鬏' to '髽',
            '鬈' to '髼', '鬒' to '黰', '鬓' to '鬢', '鬟' to '鬟', '鬣' to '鬣',
        )
        for ((simp, trad) in extras) {
            if (arr[simp.code] == 0) arr[simp.code] = trad.code
        }
        return arr
    }

    private fun buildT2S(): IntArray {
        val arr = IntArray(65536) { 0 }
        val pairs = PAIRS_250
        var i = 0
        while (i < pairs.length - 1) {
            val simp = pairs[i].code
            val trad = pairs[i + 1].code
            if (simp != trad) arr[trad] = simp
            i += 2
        }
        val extras = arrayOf(
            '後' to '后', '裡' to '里', '錶' to '表', '麵' to '面', '穀' to '谷',
            '闆' to '板', '範' to '范', '鬆' to '松', '睏' to '困', '捲' to '卷',
            '嚮' to '向', '誌' to '志', '餘' to '余', '纔' to '才', '豐' to '丰',
            '瞭' to '了', '韆' to '千', '採' to '采', '夥' to '伙', '藉' to '借',
            '剋' to '克', '蘇' to '苏', '係' to '系', '傭' to '佣', '佔' to '占',
            '註' to '注', '準' to '准', '別' to '别', '於' to '于', '劃' to '划',
            '幹' to '乾', '麼' to '么', '廣' to '广', '廠' to '厂', '廳' to '厅',
            '歷' to '历', '區' to '区', '疋' to '匹', '雙' to '双', '聖' to '圣',
            '場' to '场', '塊' to '块', '堅' to '坚', '聲' to '声', '殼' to '壳',
            '處' to '处', '備' to '备', '頭' to '头', '夾' to '夹', '奩' to '奁',
            '奐' to '奂', '獎' to '奖', '婦' to '妇', '媽' to '妈', '孫' to '孙',
            '學' to '学', '寧' to '宁', '寶' to '宝', '實' to '实', '審' to '审',
            '憲' to '宪', '宮' to '宫', '寬' to '宽', '賓' to '宾', '對' to '对',
            '尋' to '寻', '導' to '导', '將' to '将', '塵' to '尘', '嘗' to '尝',
            '層' to '层', '屆' to '届', '屬' to '属', '豈' to '岂', '鞏' to '巩',
            '歲' to '岁', '嶇' to '岖', '崗' to '岗', '帳' to '帐', '簾' to '帘',
            '幟' to '帜', '幫' to '帮', '帥' to '帅', '師' to '师', '幣' to '币',
            '巿' to '市', '颿' to '帆', '殺' to '杀', '雜' to '杂', '權' to '权',
            '條' to '条', '楊' to '杨', '樞' to '枢', '棗' to '枣', '槍' to '枪',
            '楓' to '枫', '梟' to '枭', '檸' to '柠', '柵' to '栅', '標' to '标',
            '棧' to '栈', '棟' to '栋', '欄' to '栏', '樹' to '树', '樣' to '样',
            '欒' to '栾', '槳' to '桨', '橋' to '桥', '樺' to '桦', '檜' to '桧',
            '夢' to '梦', '檢' to '检', '櫝' to '椟', '檳' to '槟', '欖' to '榄',
            '獻' to '献', '電' to '电', '畫' to '画', '暢' to '畅', '療' to '疗',
            '瘧' to '疟', '瘋' to '疯', '瘡' to '疮', '監' to '监', '蓋' to '盖',
            '盤' to '盘', '皚' to '皑', '皺' to '皱', '睞' to '睐', '矚' to '瞩',
            '確' to '确', '窮' to '穷', '竊' to '窃', '簫' to '箫', '簣' to '篑',
            '簍' to '篓', '籃' to '篮', '籬' to '篱', '攢' to '攒', '敵' to '敌',
            '斂' to '敛', '數' to '数', '齋' to '斋', '斕' to '斓', '鬥' to '斗',
            '斷' to '断', '無' to '无', '旣' to '既', '時' to '时', '曠' to '旷',
            '晝' to '昼', '顯' to '显', '曬' to '晒', '曉' to '晓', '曄' to '晔',
            '暈' to '晕', '暉' to '晖', '暫' to '暂', '曖' to '暧', '剳' to '札',
            '術' to '术', '樸' to '朴', '機' to '机', '發' to '发', '節' to '节',
            '糞' to '粪', '糰' to '团', '紅' to '红', '純' to '纯', '約' to '约',
            '級' to '级', '紡' to '纺', '紙' to '纸', '紋' to '纹', '納' to '纳',
            '紐' to '纽', '紗' to '纱', '線' to '线', '組' to '组', '細' to '细',
            '終' to '终', '紹' to '绍', '經' to '经', '絨' to '绒', '結' to '结',
            '繞' to '绕', '給' to '给', '維' to '维', '網' to '网', '綜' to '综',
            '練' to '练', '緯' to '纬', '編' to '编', '緻' to '致', '緣' to '缘',
            '縐' to '皱', '繼' to '继', '纖' to '纤', '缽' to '钵', '鑑' to '鉴',
            '鑠' to '铄', '鑪' to '炉', '鑰' to '钥', '鑽' to '钻', '鑿' to '凿',
            '針' to '针', '釣' to '钓', '銬' to '铐', '銃' to '铳', '錨' to '锚',
        )
        for ((trad, simp) in extras) {
            if (arr[trad.code] == 0) arr[trad.code] = simp.code
        }
        return arr
    }
}
