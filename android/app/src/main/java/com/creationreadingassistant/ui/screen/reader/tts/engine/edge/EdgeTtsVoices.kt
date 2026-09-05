package com.creationreadingassistant.ui.screen.reader.tts.engine.edge

import com.creationreadingassistant.ui.screen.reader.tts.engine.TtsEngineId
import com.creationreadingassistant.ui.screen.reader.tts.engine.TtsVoiceDescriptor

/**
 * Edge TTS 预置中文音色列表（微软 Azure 神经语音 ShortName）。
 *
 * 不做运行时 HTTP 拉取，避免首次进入听书时出现 300-500ms 网络阻塞。
 * 若后续微软新增音色，直接在此文件追加条目即可（冷更新靠 App 升级）。
 *
 * 默认推荐：
 * - 女声：晓晓 (XiaoxiaoNeural) — 情感丰富、自然度最高；
 * - 男声：云扬 (YunyangNeural) — 沉稳播音腔。
 */
internal object EdgeTtsVoices {

    val defaultFemaleId = "zh-CN-XiaoxiaoNeural"
    val defaultMaleId = "zh-CN-YunyangNeural"

    val all: List<TtsVoiceDescriptor> = listOf(
        // ── 简体中文（大陆） ──
        v("zh-CN-XiaoxiaoNeural", "晓晓（推荐·女声）", "zh-CN", "female"),
        v("zh-CN-YunyangNeural", "云扬（推荐·男声）", "zh-CN", "male"),
        v("zh-CN-YunjianNeural", "云健（青年男声）", "zh-CN", "male"),
        v("zh-CN-XiaoyiNeural", "晓伊（少女声）", "zh-CN", "female"),
        v("zh-CN-YunxiNeural", "云希（新闻播报）", "zh-CN", "male"),
        v("zh-CN-XiaohanNeural", "晓涵（温柔女声）", "zh-CN", "female"),
        v("zh-CN-YunfanNeural", "云帆（动漫男声）", "zh-CN", "male"),
        v("zh-CN-XiaoyaoNeural", "晓瑶（情感女声）", "zh-CN", "female"),
        // ── 粤语（香港） ──
        v("zh-HK-HiuMaanNeural", "曉曼（香港女声）", "zh-HK", "female"),
        v("zh-HK-WanLungNeural", "雲龍（香港男声）", "zh-HK", "male"),
        v("zh-HK-HiuGaaiNeural", "曉佳（香港女声）", "zh-HK", "female"),
        // ── 台湾国语 ──
        v("zh-TW-HsiaoChenNeural", "曉臻（台灣女声）", "zh-TW", "female"),
        v("zh-TW-YunJheNeural", "雲哲（台灣男声）", "zh-TW", "male"),
        v("zh-TW-HsiaoYuNeural", "曉雨（台灣女声）", "zh-TW", "female"),
        // ── 英语（作为兜底，用户切英文书时可用） ──
        v("en-US-AriaNeural", "Aria (English)", "en-US", "female"),
        v("en-US-JennyNeural", "Jenny (English)", "en-US", "female"),
        v("en-US-GuyNeural", "Guy (English)", "en-US", "male"),
        v("en-GB-SoniaNeural", "Sonia (British)", "en-GB", "female"),
    )

    fun byIdOrNull(id: String): TtsVoiceDescriptor? = all.firstOrNull { it.id == id }

    fun defaultForLocale(localeTag: String): TtsVoiceDescriptor {
        val pref = when {
            localeTag.startsWith("zh-HK") -> listOf("zh-HK-HiuMaanNeural", defaultFemaleId)
            localeTag.startsWith("zh-TW") -> listOf("zh-TW-HsiaoChenNeural", defaultFemaleId)
            localeTag.startsWith("en") -> listOf("en-US-AriaNeural", defaultFemaleId)
            else -> listOf(defaultFemaleId)
        }
        for (id in pref) byIdOrNull(id)?.let { return it }
        return all.first()
    }

    private fun v(id: String, name: String, locale: String, gender: String) = TtsVoiceDescriptor(
        id = id,
        name = name,
        locale = locale,
        gender = gender,
        engine = TtsEngineId.EDGE,
    )
}
