package com.example.keymapper.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.keymapper.data.AppSettings
import com.example.keymapper.data.SettingsRepository
import com.example.keymapper.display.DisplayHelper
import com.example.keymapper.display.DisplayUtils
import com.example.keymapper.display.ScreenInfo
import com.example.keymapper.injection.ShizukuInjector
import com.example.keymapper.injection.TouchInjector
import com.example.keymapper.mapping.MappingEngine
import com.example.keymapper.overlay.HudView
import com.example.keymapper.overlay.OverlayManager
import com.example.keymapper.util.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MappingService : Service(), HudView.Host {
    companion object {
        const val ACTION_STOP = "com.example.keymapper.STOP"
        const val ACTION_EDIT = "com.example.keymapper.EDIT"
        @Volatile var running = false
        private const val CHANNEL = "mapping"
        private const val NOTIF_ID = 1
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var repo: SettingsRepository
    private lateinit var displays: DisplayHelper
    private var wake: PowerManager.WakeLock? = null

    @Volatile private var cfg = AppSettings()
    @Volatile private var info: ScreenInfo = DisplayUtils.DEFAULT
    @Volatile private var areaRect = RectF(0f, 0f, 1920f, 1080f)
    private var touch: TouchInjector? = null
    private var engine: MappingEngine? = null
    private var overlay: OverlayManager? = null
    private var editing = false
    private var paused = false

    private val screenChanged = Runnable { handleScreenChange() }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = scheduleScreenCheck()
        override fun onDisplayRemoved(displayId: Int) = scheduleScreenCheck()
        override fun onDisplayChanged(displayId: Int) = scheduleScreenCheck()
    }
    private val inputListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) { if (!editing && !paused) overlay?.setCapture(true) }
        override fun onInputDeviceRemoved(deviceId: Int) { engine?.releaseAll() }
        override fun onInputDeviceChanged(deviceId: Int) {}
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        repo = SettingsRepository(this)
        displays = DisplayHelper(this)
        createChannel()
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(NOTIF_ID, n)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "keymapper:mapping").apply { acquire() }

        scope.launch {
            repo.flow.collect { s ->
                val hzChanged = touch != null && s.refreshHz != cfg.refreshHz
                cfg = s
                Haptics.enabled = s.haptics
                recomputeArea()
                if (touch == null) setup() else if (hzChanged) rebuild() else overlay?.refresh()
            }
        }
    }

    private fun setup() {
        refreshInfo()
        val injector = ShizukuInjector()
        val mi = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val low = mi.totalMem < 4L * 1024 * 1024 * 1024
        val extra = when (cfg.perfMode) { 1 -> 0L; 2 -> 5L; else -> if (low) 5L else 0L }
        val t = TouchInjector(injector, { info.displayId }, extra)
        touch = t
        val e = MappingEngine(t, { areaRect }, { cfg })
        engine = e
        overlay = OverlayManager(this, displays, this, refreshHz = { cfg.refreshHz },
            onDelta = { dx, dy -> if (!editing) e.onDelta(dx, dy) },
            onButton = { bit, down -> if (!editing) e.onMouseButton(bit, down) },
            onKey = { code, down -> handleKey(code, down) }
        )
        displays.register(displayListener, ui)
        (getSystemService(INPUT_SERVICE) as InputManager).registerInputDeviceListener(inputListener, ui)
        rebuild()
    }

    private fun refreshInfo() {
        info = try { displays.info(displays.target()) } catch (e: Exception) { info }
        recomputeArea()
    }

    private fun recomputeArea() { areaRect = DisplayUtils.gameArea(info.width, info.height, cfg) }

    private fun scheduleScreenCheck() {
        ui.removeCallbacks(screenChanged)
        ui.postDelayed(screenChanged, 300)
    }

    private fun handleScreenChange() {
        if (touch == null) return
        try {
            val old = info
            refreshInfo()
            val cur = info
            if (cur.displayId != old.displayId || cur.densityDpi != old.densityDpi) rebuild() else overlay?.refresh()
        } catch (e: Exception) {}
    }

    private fun rebuild() {
        val ov = overlay ?: return
        refreshInfo()
        engine?.releaseAll()
        editing = false
        paused = false
        engine?.paused = false
        ov.show()
    }

    private fun handleKey(code: Int, down: Boolean) {
        if (down && code == cfg.editKey) { setEditing(!editing); return }
        if (down && code == cfg.pauseKey && !editing) { paused = !paused; engine?.paused = paused; engine?.releaseAll(); overlay?.setCapture(true); return }
        if (editing) { if (down) overlay?.hud?.let { if (it.armed) it.addButton(code) }; return }
        engine?.onKey(code, down)
    }

    override fun settings(): AppSettings = cfg
    override fun update(s: AppSettings) { cfg = s; recomputeArea(); overlay?.refresh() }
    override fun persist() { scope.launch { repo.save(cfg) } }
    override fun arm() {}
    override fun openConfig(id: Int) { overlay?.openConfig(id) }
    override fun area(): RectF = areaRect
    override fun uiDensity(): Float = DisplayUtils.uiDensity(info, cfg)
    override fun closeAll() { stopSelf() }

    override fun setEditing(on: Boolean) {
        editing = on
        engine?.releaseAll()
        overlay?.setEditing(on)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) { ACTION_STOP -> stopSelf(); ACTION_EDIT -> setEditing(!editing) }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        ui.removeCallbacksAndMessages(null)
        scope.cancel()
        try { displays.unregister(displayListener) } catch (e: Exception) {}
        try { (getSystemService(INPUT_SERVICE) as InputManager).unregisterInputDeviceListener(inputListener) } catch (e: Exception) {}
        overlay?.hide()
        touch?.shutdown()
        wake?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Mapeamento", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("KeyMapper ativo")
            .setContentText("Atalhos de editar HUD e pausar configuráveis no app")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
}
