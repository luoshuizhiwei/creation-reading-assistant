package com.creationreadingassistant.acceptance

import android.content.Context
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Writes the actual per-method journey observations into app-specific external storage for adb pull. */
internal class Task34Evidence(
    context: Context,
    private val runId: String,
    private val method: String,
) {
    private val file = File(
        File(requireNotNull(context.getExternalFilesDir(null)), "task34-evidence/$runId"),
        "$method.json",
    )
    private val events = JSONArray()

    init {
        check(!file.exists()) { "拒绝覆盖已有任务证据文件：$file" }
    }

    fun record(
        phase: String,
        page: String,
        chapter1Visible: Boolean?,
        chapter2Visible: Boolean?,
        temporaryReturnVisible: Boolean?,
        row: ReadingProgressEntity?,
        uiObserved: List<String> = emptyList(),
    ) {
        val event = JSONObject()
            .put("stage_index", events.length() + 1)
            .put("phase", phase)
            .put("elapsedRealtimeNanos", android.os.SystemClock.elapsedRealtimeNanos())
            .put("page", page)
            .put("chapter1_marker_visible", chapter1Visible ?: JSONObject.NULL)
            .put("chapter2_marker_visible", chapter2Visible ?: JSONObject.NULL)
            .put("temporary_return_action_visible", temporaryReturnVisible ?: JSONObject.NULL)
            .put("ui_observed", JSONArray(uiObserved))
            .put("progress_row", row?.let(::rowJson) ?: JSONObject.NULL)
            .put("progress_row_columns", JSONArray(listOf(
                "book_id", "progress_percent", "last_read_at", "total_reading_time_ms",
                "completion_state", "current_location_json", "payload", "revision",
                "device_id", "updated_at", "completed_at", "deleted_at",
            )))
        events.put(event)
        file.parentFile!!.mkdirs()
        val envelope = JSONObject()
            .put("task", "34-v2")
            .put("run_id", runId)
            .put("method", method)
            .put("events", events)
        file.writeText(envelope.toString(2), Charsets.UTF_8)
    }

    private fun rowJson(row: ReadingProgressEntity) = JSONObject()
        .put("book_id", row.book_id)
        .put("progress_percent", row.progress_percent.toDouble())
        .put("last_read_at", row.last_read_at ?: JSONObject.NULL)
        .put("total_reading_time_ms", row.total_reading_time_ms)
        .put("completion_state", row.completion_state)
        .put("current_location_json", row.current_location_json ?: JSONObject.NULL)
        .put("payload", row.payload ?: JSONObject.NULL)
        .put("revision", row.revision)
        .put("device_id", row.device_id ?: JSONObject.NULL)
        .put("updated_at", row.updated_at)
        .put("completed_at", row.completed_at ?: JSONObject.NULL)
        .put("deleted_at", row.deleted_at ?: JSONObject.NULL)
}
