package sgnv.anubis.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import sgnv.anubis.app.AnubisApp
import sgnv.anubis.app.data.repository.AppRepository
import sgnv.anubis.app.settings.AppSettings
import sgnv.anubis.app.vpn.SelectedVpnClient
import sgnv.anubis.app.vpn.VpnClientManager
import sgnv.anubis.app.vpn.VpnClientType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Short-lived service for home-screen widget toggle.
 * Mirrors StealthTileService.onClick() and relays orchestrator progress text
 * back to the widget in real time.
 */
class StealthWidgetService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE) doToggle()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun doToggle() {
        val willBeActive = !StealthWidgetProvider.isVpnActive(this)

        val app = applicationContext as AnubisApp
        val shizukuManager = app.shizukuManager
        val vpnClientManager = VpnClientManager(this, shizukuManager)
        val repo = AppRepository(app.database.managedAppDao(), this)
        val orchestrator = StealthOrchestrator(this, shizukuManager, vpnClientManager, repo)

        val prefs = AppSettings.prefs(this)
        val pkg = prefs.getString(AppSettings.KEY_VPN_CLIENT_PACKAGE, null)
            ?: VpnClientType.V2RAY_NG.packageName
        val client = SelectedVpnClient.fromPackage(pkg)

        // Immediate feedback before the Shizuku bind delay.
        StealthWidgetProvider.updateAllWidgets(
            this,
            if (willBeActive) "Замораживаю..." else "Отключаю VPN...",
            StealthWidgetProvider.COLOR_WORKING
        )

        shizukuManager.bindUserService()
        vpnClientManager.startMonitoringVpn()

        scope.launch {
            shizukuManager.awaitUserService()

            // Mirror each orchestrator progress step into the widget text.
            val progressJob = launch {
                orchestrator.progressText.filterNotNull().collect { text ->
                    StealthWidgetProvider.updateAllWidgets(
                        this@StealthWidgetService, text, StealthWidgetProvider.COLOR_WORKING
                    )
                }
            }

            if (willBeActive) {
                orchestrator.enable(client)
                VpnMonitorService.start(this@StealthWidgetService)
                if (orchestrator.lastError.value == null) {
                    // startVPN() is fire-and-forget — wait for the network callback
                    // to confirm VPN actually appeared before showing final state.
                    StealthWidgetProvider.updateAllWidgets(
                        this@StealthWidgetService, "Подключаю...", StealthWidgetProvider.COLOR_WORKING
                    )
                    withTimeoutOrNull(VPN_CONNECT_TIMEOUT_MS) {
                        vpnClientManager.vpnActive.first { it }
                    }
                }
            } else {
                vpnClientManager.refreshVpnState()
                vpnClientManager.detectActiveVpnClient()
                val detectedPkg = vpnClientManager.activeVpnPackage.value
                orchestrator.disable(client, detectedPkg)
                VpnMonitorService.stop(this@StealthWidgetService)
            }

            progressJob.cancel()
            vpnClientManager.stopMonitoringVpn()
            // Real state from ConnectivityManager: VPN is either confirmed up (enable path)
            // or confirmed down (disable path confirmed by stopVpn()).
            StealthWidgetProvider.updateAllWidgets(this@StealthWidgetService)
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_TOGGLE = "sgnv.anubis.app.WIDGET_DO_TOGGLE"
        private const val VPN_CONNECT_TIMEOUT_MS = 15_000L

        fun toggle(context: Context) {
            context.startService(
                Intent(context, StealthWidgetService::class.java).apply {
                    action = ACTION_TOGGLE
                }
            )
        }
    }
}
