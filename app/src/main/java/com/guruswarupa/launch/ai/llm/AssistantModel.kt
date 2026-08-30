package com.guruswarupa.launch.ai.llm

/**
 * The single on-device model the AI assistant uses. Deliberately not Gemma: Gemma's
 * weights are gated behind a Hugging Face login + license acceptance, which would mean
 * either every user creating an account or this app redistributing the weights itself.
 * Qwen2.5-0.5B-Instruct is Apache-2.0 and hosted ungated by the LiteRT community, so it
 * downloads directly with no account, no key, and no cost to anyone.
 *
 * Verified 2026-08-30 via `curl -sI -L <DOWNLOAD_URL>` (size) and the HF API's
 * `siblings[].lfs.sha256` for this exact file (hash) — re-verify both if this ever moves
 * to a different file or repo revision.
 */
object AssistantModel {
    const val DISPLAY_NAME = "Qwen2.5 0.5B Instruct"
    const val LICENSE_NAME = "Apache License 2.0"
    const val LICENSE_URL = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct"

    const val FILE_NAME = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
    const val DOWNLOAD_URL =
        "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/$FILE_NAME"

    const val EXPECTED_SIZE_BYTES = 546_660_344L
    const val EXPECTED_SHA256 = "e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2"
}
