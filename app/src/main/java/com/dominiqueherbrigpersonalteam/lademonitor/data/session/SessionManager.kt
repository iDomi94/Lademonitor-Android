package com.dominiqueherbrigpersonalteam.lademonitor.data.session

import com.dominiqueherbrigpersonalteam.lademonitor.data.model.AuthResponse
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.AuthUser
import com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ApiClient
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Port of the iOS `SessionManager`: the central auth state that drives the login-vs-main-app
 * switch. A token present at startup counts as an active session; an invalid one falls out on the
 * first API call through the 401 handling in [ApiClient].
 */
object SessionManager {

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    val currentUser: StateFlow<AuthUser?> = _currentUser

    fun init() {
        _isAuthenticated.value = TokenStore.readToken() != null
    }

    fun completeAuthentication(response: AuthResponse) {
        TokenStore.saveToken(response.token)
        _currentUser.value = response.user
        _isAuthenticated.value = true
    }

    suspend fun logout() {
        runCatching { ApiClient.logout() }
        clearLocalSession()
    }

    /** Called on a 401 for an authenticated request: the session is no longer valid. */
    fun invalidateSession() {
        clearLocalSession()
    }

    suspend fun refreshCurrentUserIfNeeded() {
        if (!_isAuthenticated.value || _currentUser.value != null) return
        _currentUser.value = runCatching { ApiClient.fetchMe() }.getOrNull()
    }

    /** Re-fetch the user - e.g. after the address was confirmed in another client. */
    suspend fun reloadCurrentUser() {
        if (!_isAuthenticated.value) return
        _currentUser.value = runCatching { ApiClient.fetchMe() }.getOrNull()
    }

    /**
     * Called by the account settings when an endpoint returns the updated user (address,
     * notifications).
     */
    fun applyUpdatedUser(user: AuthUser) {
        _currentUser.value = user
    }

    /**
     * Change the own password.
     *
     * The server drops ALL sessions of the user - so a possibly hijacked session does not keep
     * running - and immediately issues a new one, whose token it ships along since version 0.14.1.
     * That one is adopted right here: the app stays signed in without a second sign-in.
     *
     * Other devices (Home Assistant, further installations) are signed out afterwards and need new
     * access - the account screen points that out.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val response = ApiClient.changePassword(currentPassword, newPassword)
        completeAuthentication(response)
    }

    /**
     * Delete the own account irrevocably, including all own data on the server. A wrong password
     * (403) keeps throwing without changing anything locally - only after a confirmed success is
     * the local session cleared, as on logout.
     */
    suspend fun deleteAccount(currentPassword: String) {
        ApiClient.deleteAccount(currentPassword)
        clearLocalSession()
    }

    private fun clearLocalSession() {
        TokenStore.deleteToken()
        _currentUser.value = null
        _isAuthenticated.value = false
    }
}
