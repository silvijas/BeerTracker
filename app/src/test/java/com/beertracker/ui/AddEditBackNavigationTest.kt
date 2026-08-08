package com.beertracker.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AddEditBackNavigationTest {

    @Test
    fun `back is blocked while saving even with unsaved changes`() {
        assertEquals(
            BackNavigationAction.BLOCK,
            backNavigationAction(isSaving = true, hasUnsavedChanges = true),
        )
    }

    @Test
    fun `back confirms unsaved changes when idle`() {
        assertEquals(
            BackNavigationAction.CONFIRM_DISCARD,
            backNavigationAction(isSaving = false, hasUnsavedChanges = true),
        )
    }

    @Test
    fun `back navigates when idle and unchanged`() {
        assertEquals(
            BackNavigationAction.NAVIGATE,
            backNavigationAction(isSaving = false, hasUnsavedChanges = false),
        )
    }
}
