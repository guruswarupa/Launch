package com.guruswarupa.launch.ai.llm

/**
 * One selectable on-device model. Deliberately no Gemma entries: Gemma's weights are gated
 * behind a Hugging Face login + license acceptance, which would mean either every user
 * creating an account or this app redistributing the weights itself. Every model here is
 * Apache-2.0 and hosted ungated by the LiteRT community, so it downloads directly with no
 * account, no key, and no cost to anyone.
 *
 * Each entry's size/hash was verified via `curl -sI -L <downloadUrl>` (size) and the HF
 * API's `siblings[].lfs.sha256` for that exact file (hash) — re-verify both if an entry
 * ever moves to a different file or repo revision.
 */
data class AssistantModelInfo(
    val id: String,
    val displayName: String,
    val description: String,
    val licenseName: String,
    val licenseUrl: String,
    val fileName: String,
    val downloadUrl: String,
    val expectedSizeBytes: Long,
    val expectedSha256: String
)

object AssistantModel {
    /** Verified 2026-08-30. */
    val QWEN_0_5B = AssistantModelInfo(
        id = "qwen2.5-0.5b-instruct",
        displayName = "Qwen2.5 0.5B Instruct",
        description = "Fastest, smallest download. Good for quick questions.",
        licenseName = "Apache License 2.0",
        licenseUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct",
        fileName = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/" +
            "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        expectedSizeBytes = 546_660_344L,
        expectedSha256 = "e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2"
    )

    /** Verified 2026-08-30. */
    val QWEN_1_5B = AssistantModelInfo(
        id = "qwen2.5-1.5b-instruct",
        displayName = "Qwen2.5 1.5B Instruct",
        description = "Larger download, noticeably better answers. Wants more RAM.",
        licenseName = "Apache License 2.0",
        licenseUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct",
        fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/" +
            "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        expectedSizeBytes = 1_597_913_616L,
        expectedSha256 = "8d867a7c93a6acf2892f08e0174e2f6f351ad256b7e3cfb6d6cd9c89794b42e0"
    )

    /** Ordered smallest/fastest first — the order this shows up in Settings. */
    val ALL: List<AssistantModelInfo> = listOf(QWEN_0_5B, QWEN_1_5B)

    val DEFAULT: AssistantModelInfo = QWEN_0_5B

    fun byId(id: String?): AssistantModelInfo = ALL.firstOrNull { it.id == id } ?: DEFAULT
}
