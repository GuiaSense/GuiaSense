package com.guiasense.dehjp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.guiasense.dehjp.AlertaVibracao.Nivel

/**
 * Serviço em primeiro plano que escuta o óculos no Firebase e gera notificações
 * de alerta. Cada nível tem um canal com padrão de vibração próprio; o
 * smartwatch pareado (Galaxy Watch / Wear OS) espelha a notificação e vibra
 * no pulso do usuário — mesmo com o app fechado ou a tela desligada.
 */
class MonitorService : Service() {

    companion object {
        private const val TAG = "MonitorService"
        private const val CANAL_SERVICO = "monitoramento"
        private const val ID_NOTIF_SERVICO = 1
        private const val ID_NOTIF_ALERTA = 2

        /** Enquanto o obstáculo continuar Médio/Forte, re-alerta neste intervalo. */
        private const val REPETIR_ALERTA_MS = 3_000L
        /** Nunca alertar mais rápido que isso (o Android também limita). */
        private const val INTERVALO_MIN_ALERTA_MS = 800L

        @Volatile var rodando = false
            private set

        fun iniciar(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))

        fun parar(context: Context) = context.stopService(Intent(context, MonitorService::class.java))

        fun canalDoNivel(n: Nivel) = "alerta_" + n.textoFirebase.lowercase()

        // Padrões distintos para o usuário reconhecer o nível só pela vibração.
        val PADRAO_VIBRACAO = mapOf(
            Nivel.LEVE to longArrayOf(0, 150),
            Nivel.MEDIA to longArrayOf(0, 250, 120, 250),
            Nivel.FORTE to longArrayOf(0, 500, 100, 500, 100, 500),
        )

        /** Cria os canais (idempotente). Chamado também pela Activity. */
        fun criarCanais(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CANAL_SERVICO, "Monitoramento ativo", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Aviso fixo enquanto o GuiaSense escuta o óculos" }
            )
            for ((nivel, padrao) in PADRAO_VIBRACAO) {
                nm.createNotificationChannel(
                    NotificationChannel(canalDoNivel(nivel), "Obstáculo — ${nivel.rotulo}", NotificationManager.IMPORTANCE_HIGH)
                        .apply {
                            description = "Alerta de obstáculo com vibração ${nivel.textoFirebase}"
                            enableVibration(true)
                            vibrationPattern = padrao
                        }
                )
            }
        }
    }

    private var refEstado: DatabaseReference? = null
    private var refOffset: DatabaseReference? = null
    private var offsetServidorMs = 0L
    private var ultimoNivel = Nivel.NENHUMA
    private var ultimoAlertaEm = 0L

    private val listenerOffset = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) { offsetServidorMs = s.getValue(Long::class.java) ?: 0L }
        override fun onCancelled(e: DatabaseError) {}
    }

    private val listenerEstado = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) = processarEstado(s)
        override fun onCancelled(e: DatabaseError) {
            Log.e(TAG, "Leitura cancelada: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        criarCanais(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = notificacaoServico("Aguardando dados do óculos…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ID_NOTIF_SERVICO, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID_NOTIF_SERVICO, notif)
        }
        if (!rodando) {
            rodando = true
            autenticarEEscutar()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        rodando = false
        refEstado?.removeEventListener(listenerEstado)
        refOffset?.removeEventListener(listenerOffset)
        NotificationManagerCompat.from(this).cancel(ID_NOTIF_ALERTA)
        super.onDestroy()
    }

    private fun autenticarEEscutar() {
        if (!AppConfig.firebaseHabilitado) {
            atualizarNotificacaoServico("Modo de teste (sem Firebase)")
            return
        }
        val auth = FirebaseAuth.getInstance()
        val escutar = {
            val db = FirebaseDatabase.getInstance()
            refOffset = db.getReference(".info/serverTimeOffset").also { it.addValueEventListener(listenerOffset) }
            refEstado = db.getReference(AlertaVibracao.PATH_ESTADO).also { it.addValueEventListener(listenerEstado) }
        }
        if (auth.currentUser != null) escutar()
        else auth.signInAnonymously()
            .addOnSuccessListener { escutar() }
            .addOnFailureListener { e ->
                Log.e(TAG, "Falha no login anônimo", e)
                atualizarNotificacaoServico("Erro de autenticação no Firebase")
            }
    }

    private fun processarEstado(s: DataSnapshot) {
        val atualizadoEm = s.child("ultima_atualizacao").getValue(Long::class.java)
        val agoraServidor = System.currentTimeMillis() + offsetServidorMs
        if (!AlertaVibracao.estaOnline(atualizadoEm, agoraServidor)) {
            // Dado antigo (óculos desligado): nunca alertar com informação velha.
            atualizarNotificacaoServico("Óculos offline — aguardando sinal")
            mudarNivel(Nivel.NENHUMA, null)
            return
        }
        val distancia = s.child("ultima_distancia").getValue(Double::class.java)
        val nivel = Nivel.doFirebase(s.child("vibracao_atual").getValue(String::class.java))
        atualizarNotificacaoServico("Conectado ao óculos")
        mudarNivel(nivel, distancia)
    }

    private fun mudarNivel(nivel: Nivel, distancia: Double?) {
        val agora = SystemClock.elapsedRealtime()
        val nm = NotificationManagerCompat.from(this)

        if (nivel == Nivel.NENHUMA) {
            if (ultimoNivel != Nivel.NENHUMA) nm.cancel(ID_NOTIF_ALERTA)
            ultimoNivel = nivel
            return
        }

        val mudou = nivel != ultimoNivel
        val repetir = nivel >= Nivel.MEDIA && agora - ultimoAlertaEm >= REPETIR_ALERTA_MS
        if ((mudou || repetir) && agora - ultimoAlertaEm >= INTERVALO_MIN_ALERTA_MS) {
            alertar(nivel, distancia)
            ultimoAlertaEm = agora
        }
        ultimoNivel = nivel
    }

    private fun alertar(nivel: Nivel, distancia: Double?) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val notif = NotificationCompat.Builder(this, canalDoNivel(nivel))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Obstáculo: ${nivel.rotulo}")
            .setContentText("Distância: ${AlertaVibracao.textoDistancia(distancia)}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVibrate(PADRAO_VIBRACAO[nivel])  // usado em versões que leem da notificação
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setTimeoutAfter(4_000)
            .setContentIntent(abrirApp())
            .build()
        try {
            NotificationManagerCompat.from(this).notify(ID_NOTIF_ALERTA, notif)
        } catch (e: SecurityException) {
            Log.w(TAG, "Sem permissão de notificação", e)
        }
    }

    private fun notificacaoServico(texto: String): Notification =
        NotificationCompat.Builder(this, CANAL_SERVICO)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("GuiaSense monitorando")
            .setContentText(texto)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)  // não precisa aparecer no relógio
            .setContentIntent(abrirApp())
            .build()

    private var textoServicoAtual = ""
    private fun atualizarNotificacaoServico(texto: String) {
        if (texto == textoServicoAtual) return
        textoServicoAtual = texto
        try {
            NotificationManagerCompat.from(this).notify(ID_NOTIF_SERVICO, notificacaoServico(texto))
        } catch (_: SecurityException) { }
    }

    private fun abrirApp(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
