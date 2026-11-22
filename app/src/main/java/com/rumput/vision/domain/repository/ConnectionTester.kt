package com.rumput.vision.domain.repository

import com.rumput.vision.domain.model.ConnectionTestReport
import com.rumput.vision.domain.usecase.ServerProfileDraft

interface ConnectionTester {
    /**
     * Tests every valid URL in [draft]. Uses the typed password, or the stored password when editing
     * and the field is blank. Nothing is saved; any session token obtained is discarded.
     */
    suspend fun test(draft: ServerProfileDraft): ConnectionTestReport
}
