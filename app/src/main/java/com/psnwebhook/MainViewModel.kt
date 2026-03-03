package com.psnwebhook

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.psnwebhook.auth.PsnAuthManager
import com.psnwebhook.model.AppScreen
import com.psnwebhook.model.PresenceConfig
import com.psnwebhook.model.PsnAccount
import com.psnwebhook.model.PsnGame
import com.psnwebhook.psn.PsnApiClient
import com.psnwebhook.service.PresenceService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val android.content.Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "psn_prefs")

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ACCOUNT_KEY = stringPreferencesKey("psn_account")
    private val GAMES_KEY = stringPreferencesKey("psn_games")

    private val _screen = MutableStateFlow(AppScreen.LOGIN)
    val screen: StateFlow<AppScreen> = _screen

    private val _account = MutableStateFlow<PsnAccount?>(null)
    val account: StateFlow<PsnAccount?> = _account

    private val _games = MutableStateFlow<List<PsnGame>>(emptyList())
    val games: StateFlow<List<PsnGame>> = _games

    private val _presence = MutableStateFlow(PresenceConfig())
    val presence: StateFlow<PresenceConfig> = _presence

    private val _trophies = MutableStateFlow(Triple(0, 0, 0))
    val trophies: StateFlow<Triple<Int, Int, Int>> = _trophies

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _customTitleId = MutableStateFlow("")
    val customTitleId: StateFlow<String> = _customTitleId

    init {
        viewModelScope.launch {
            getApplication<Application>().dataStore.data
                .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
                .first()
                .let { prefs ->
                    prefs[ACCOUNT_KEY]?.let { raw ->
                        runCatching {
                            val acc = json.decodeFromString<PsnAccount>(raw)
                            _account.value = acc
                            if (acc.accessToken.isNotEmpty()) {
                                _screen.value = AppScreen.HOME
                            }
                        }
                    }
                    prefs[GAMES_KEY]?.let { raw ->
                        runCatching { _games.value = json.decodeFromString(raw) }
                    }
                }
        }
    }

    fun onLoginResult(resultCode: Int, data: Intent?) {
        if (resultCode == android.app.Activity.RESULT_OK && data != null) {
            val npsso = data.getStringExtra("npsso") ?: ""
            val accessToken = data.getStringExtra("accessToken") ?: ""
            val refreshToken = data.getStringExtra("refreshToken") ?: ""
            val expiresAt = data.getLongExtra("expiresAt", 0L)

            if (accessToken.isEmpty()) {
                _error.value = "Login falhou: token vazio"
                return
            }

            val partial = PsnAccount(npsso = npsso, accessToken = accessToken, refreshToken = refreshToken, expiresAt = expiresAt)
            _account.value = partial
            _screen.value = AppScreen.HOME

            viewModelScope.launch {
                _isLoading.value = true
                try {
                    val profile = PsnApiClient.fetchProfile(accessToken)
                    val full = profile.copy(npsso = npsso, accessToken = accessToken, refreshToken = refreshToken, expiresAt = expiresAt)
                    _account.value = full
                    saveAccount(full)
                    loadGames(accessToken, full.accountId)
                    loadTrophies(accessToken)
                } catch (e: Exception) {
                    _error.value = "Erro ao carregar perfil: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        } else {
            _error.value = "Login cancelado"
        }
    }

    fun refreshData() {
        val acc = _account.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val token = getValidToken(acc)
                val profile = PsnApiClient.fetchProfile(token)
                val updated = acc.copy(
                    onlineId = profile.onlineId,
                    accountId = profile.accountId,
                    avatarUrl = profile.avatarUrl,
                    level = profile.level,
                    presenceState = profile.presenceState
                )
                _account.value = updated
                saveAccount(updated)
                loadGames(token, updated.accountId)
                loadTrophies(token)
            } catch (e: Exception) {
                _error.value = "Erro ao atualizar: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadGames(token: String, accountId: String) {
        try {
            val list = PsnApiClient.fetchRecentGames(token, accountId)
            _games.value = list
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[GAMES_KEY] = json.encodeToString(list)
            }
        } catch (_: Exception) {}
    }

    private suspend fun loadTrophies(token: String) {
        try {
            _trophies.value = PsnApiClient.fetchTrophySummary(token)
        } catch (_: Exception) {}
    }

    fun setCustomTitleId(id: String) {
        _customTitleId.value = id
    }

    fun startPresence(titleId: String, titleName: String) {
        val acc = _account.value ?: return
        _presence.value = PresenceConfig(titleId = titleId, titleName = titleName, isActive = true, startedAt = System.currentTimeMillis())

        viewModelScope.launch {
            val token = try { getValidToken(acc) } catch (_: Exception) { acc.accessToken }
            val ctx = getApplication<Application>()
            val svc = PresenceService.startIntent(ctx, titleId, titleName, token)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(svc)
            } else {
                ctx.startService(svc)
            }
        }
    }

    fun stopPresence() {
        _presence.value = _presence.value.copy(isActive = false)
        try {
            getApplication<Application>().stopService(
                Intent(getApplication(), PresenceService::class.java)
            )
        } catch (_: Exception) {}
    }

    fun logout() {
        stopPresence()
        _account.value = null
        _games.value = emptyList()
        _screen.value = AppScreen.LOGIN
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { it.remove(ACCOUNT_KEY) }
        }
    }

    fun clearError() { _error.value = null }

    private suspend fun getValidToken(acc: PsnAccount): String {
        if (System.currentTimeMillis() < acc.expiresAt - 60_000L) return acc.accessToken
        if (acc.refreshToken.isEmpty()) return acc.accessToken
        return try {
            val (newAccess, newRefresh, newExpiry) = PsnAuthManager.refreshAccessToken(acc.refreshToken)
            val updated = acc.copy(accessToken = newAccess, refreshToken = newRefresh, expiresAt = newExpiry)
            _account.value = updated
            saveAccount(updated)
            newAccess
        } catch (_: Exception) { acc.accessToken }
    }

    private fun saveAccount(acc: PsnAccount) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[ACCOUNT_KEY] = json.encodeToString(acc)
            }
        }
    }
}
