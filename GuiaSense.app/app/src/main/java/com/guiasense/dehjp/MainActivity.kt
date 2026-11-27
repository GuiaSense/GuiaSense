package com.guiasense.dehjp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.guiasense.dehjp.AlertaVibracao.Nivel
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private companion object { const val TAG = "MainActivity" }

    private var alcanceMaximo = AlertaVibracao.ALCANCE_PADRAO_M

    private lateinit var btnConectar: Button
    private lateinit var txtStatusConexao: TextView
    private lateinit var rbPerto: RadioButton
    private lateinit var rbMedio: RadioButton
    private lateinit var seekbarAlcance: SeekBar
    private lateinit var txtAlcanceAtual: TextView
    private lateinit var txtInfoVibracao: TextView
    private lateinit var btnSalvar: Button
    private lateinit var txtFeedback: TextView
    private lateinit var txtDistancia: TextView
    private lateinit var txtIntensidade: TextView
    private lateinit var viewIntensidade: View
    private lateinit var viewBarraIntensidade: View

    private val handler = Handler(Looper.getMainLooper())
    private val esconderFeedback = Runnable { txtFeedback.visibility = View.GONE }
    private val reavaliarConexao = object : Runnable {
        override fun run() { mostrarEstado(); handler.postDelayed(this, 1_000) }
    }


    private val db by lazy { FirebaseDatabase.getInstance() }
    private var refEstado: DatabaseReference? = null
    private var refConfig: DatabaseReference? = null
    private var refOffset: DatabaseReference? = null
    private var ultimoEstado: EstadoOculos? = null
    private var offsetServidorMs = 0L

    private val pedirPermissaoNotificacao =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
            if (concedida) iniciarMonitoramento()
            else showFeedback("Sem permissão de notificação o relógio não vibra.", erro = true)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnConectar = findViewById(R.id.btn_conectar)
        txtStatusConexao = findViewById(R.id.txt_status_conexao)
        rbPerto = findViewById(R.id.rb_perto)
        rbMedio = findViewById(R.id.rb_medio)
        seekbarAlcance = findViewById(R.id.seekbar_alcance)
        txtAlcanceAtual = findViewById(R.id.txt_alcance_atual)
        txtInfoVibracao = findViewById(R.id.txt_info_vibracao)
        btnSalvar = findViewById(R.id.btn_salvar)
        txtFeedback = findViewById(R.id.txt_feedback)
        txtDistancia = findViewById(R.id.txt_distancia)
        txtIntensidade = findViewById(R.id.txt_intensidade)
        viewIntensidade = findViewById(R.id.view_intensidade)
        viewBarraIntensidade = findViewById(R.id.view_barra_intensidade)

        MonitorService.criarCanais(this)
        setupMonitoramento()
        setupRadioButtons()
        setupSeekBar()
        btnSalvar.setOnClickListener { salvarAlcance(alcanceMaximo) }
        aplicarAlcanceNaTela(alcanceMaximo)

        if (AppConfig.firebaseHabilitado) autenticarEConectar()
        else txtStatusConexao.text = "Modo de teste (sem Firebase)"
    }

    override fun onStart() {
        super.onStart()
        atualizarBotaoMonitoramento()
        handler.post(reavaliarConexao)
    }

    override fun onStop() {
        handler.removeCallbacks(reavaliarConexao)
        super.onStop()
    }

    override fun onDestroy() {
        refEstado?.removeEventListener(listenerEstado)
        refConfig?.removeEventListener(listenerConfig)
        refOffset?.removeEventListener(listenerOffset)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ------------------------------------------------------------ Firebase

    private fun autenticarEConectar() {
        txtStatusConexao.text = "Conectando ao Firebase…"
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) { escutarFirebase(); return }
        auth.signInAnonymously()
            .addOnSuccessListener { escutarFirebase() }
            .addOnFailureListener { e ->
                Log.e(TAG, "Falha no login anônimo", e)
                txtStatusConexao.text = "Erro de autenticação: ${e.localizedMessage}"
            }
    }

    private fun escutarFirebase() {
        refOffset = db.getReference(".info/serverTimeOffset").also { it.addValueEventListener(listenerOffset) }
        refEstado = db.getReference(AlertaVibracao.PATH_ESTADO).also { it.addValueEventListener(listenerEstado) }
        // Carrega o alcance salvo (antes o app sempre abria em 1,00 m) e
        // acompanha mudanças feitas pela página web.
        refConfig = db.getReference(AlertaVibracao.PATH_CONFIG).child("alcance_maximo")
            .also { it.addValueEventListener(listenerConfig) }
    }

    private val listenerOffset = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) { offsetServidorMs = s.getValue(Long::class.java) ?: 0L }
        override fun onCancelled(e: DatabaseError) {}
    }

    private val listenerEstado = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) = aplicarEstado(EstadoOculos.de(s))
        override fun onCancelled(e: DatabaseError) {
            txtStatusConexao.text = "Sem permissão para ler o banco (${e.message})"
        }
    }

    private val listenerConfig = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) {
            val v = s.getValue(Double::class.java) ?: return
            aplicarAlcanceNaTela(AlertaVibracao.limitarAlcance(v))
        }
        override fun onCancelled(e: DatabaseError) {}
    }

    /** Recebe um novo estado do óculos (do Firebase ou, nos testes, simulado). */
    @VisibleForTesting
    internal fun aplicarEstado(estado: EstadoOculos?) {
        ultimoEstado = estado
        mostrarEstado()
    }

    private fun mostrarEstado() {
        val e = ultimoEstado
        if (e == null) {
            txtStatusConexao.text = if (AppConfig.firebaseHabilitado) "Aguardando o óculos enviar dados…"
            else "Modo de teste (sem Firebase)"
            return
        }
        val online = AlertaVibracao.estaOnline(e.atualizadoEmMs, System.currentTimeMillis() + offsetServidorMs)
        txtStatusConexao.text = if (online) "🟢 Óculos conectado (ao vivo)" else "⚫ Óculos offline"
        txtStatusConexao.setTextColor(ContextCompat.getColor(this,
            if (online) android.R.color.holo_green_dark else android.R.color.darker_gray))

        if (!online) return
        txtDistancia.text = AlertaVibracao.textoDistancia(e.distanciaM)
        txtIntensidade.text = e.nivel.rotulo
        atualizarBarra(AlertaVibracao.intensidade(e.distanciaM, alcanceMaximo))
    }

    private fun salvarAlcance(alcance: Double) {
        if (!AppConfig.firebaseHabilitado) {
            showFeedback("Alcance de ${AlertaVibracao.metros(alcance)} salvo (modo de teste)")
            return
        }
        val dados = mapOf<String, Any>(
            "alcance_maximo" to alcance,
            "timestamp" to ServerValue.TIMESTAMP,
        )
        // updateChildren: não apaga outros campos do nó.
        db.getReference(AlertaVibracao.PATH_CONFIG).updateChildren(dados)
            .addOnSuccessListener {
                showFeedback("✅ Alcance de ${AlertaVibracao.metros(alcance)} enviado ao óculos")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro ao salvar configuração", e)
                showFeedback("Erro ao salvar: ${e.localizedMessage}", erro = true)
            }
    }

    // -------------------------------------------------------- Monitoramento

    private fun setupMonitoramento() {
        btnConectar.setOnClickListener {
            if (MonitorService.rodando) {
                MonitorService.parar(this)
                handler.postDelayed({ atualizarBotaoMonitoramento() }, 300)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                pedirPermissaoNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                iniciarMonitoramento()
            }
        }
    }

    private fun iniciarMonitoramento() {
        MonitorService.iniciar(this)
        showFeedback("Alertas ativados: o relógio vai vibrar com os obstáculos")
        handler.postDelayed({ atualizarBotaoMonitoramento() }, 300)
    }

    private fun atualizarBotaoMonitoramento() {
        btnConectar.text = if (MonitorService.rodando) "Parar alertas no relógio"
        else "Iniciar alertas no relógio"
    }

    // ------------------------------------------------------------------- UI

    private fun setupRadioButtons() {
        // Os RadioButtons ficam dentro de cartões (MaterialCardView), não como filhos
        // diretos do RadioGroup — e o RadioGroup só controla filhos diretos. Por isso o
        // listener dele nunca disparava ao tocar e os dois podiam ficar marcados.
        // Tratamos o toque em cada botão e a exclusão mútua em aplicarAlcanceNaTela().
        val selecionar = { valor: Double ->
            aplicarAlcanceNaTela(valor)
            salvarAlcance(valor)
        }
        rbPerto.setOnClickListener { selecionar(1.00) }
        rbMedio.setOnClickListener { selecionar(1.50) }
    }

    private fun setupSeekBar() {
        seekbarAlcance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return  // antes, mudar por código desmarcava o modo escolhido
                alcanceMaximo = AlertaVibracao.limitarAlcance(progress / 100.0)
                atualizarTextosAlcance()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                aplicarAlcanceNaTela(alcanceMaximo)
                salvarAlcance(alcanceMaximo)
            }
        })
    }

    private fun aplicarAlcanceNaTela(valor: Double) {
        alcanceMaximo = valor
        seekbarAlcance.progress = (valor * 100).roundToInt()
        rbPerto.isChecked = abs(valor - 1.00) < 0.001
        rbMedio.isChecked = abs(valor - 1.50) < 0.001
        atualizarTextosAlcance()
    }

    private fun atualizarTextosAlcance() {
        txtAlcanceAtual.text = AlertaVibracao.metros(alcanceMaximo)
        // TalkBack lê "1,50 metros" em vez de uma porcentagem sem sentido.
        ViewCompat.setStateDescription(seekbarAlcance, AlertaVibracao.metros(alcanceMaximo).replace(" m", " metros"))
        txtInfoVibracao.text = AlertaVibracao.textoFaixas(alcanceMaximo)
    }

    private fun atualizarBarra(fracao: Double) {
        viewBarraIntensidade.post {
            val lp = viewIntensidade.layoutParams
            lp.width = (viewBarraIntensidade.width * fracao).toInt()
            viewIntensidade.layoutParams = lp
        }
    }

    private fun showFeedback(message: String, erro: Boolean = false) {
        txtFeedback.text = message
        txtFeedback.setTextColor(if (erro) 0xFFE53935.toInt() else 0xFF4CAF50.toInt())
        txtFeedback.visibility = View.VISIBLE
        handler.removeCallbacks(esconderFeedback)  // antes, mensagens antigas apagavam as novas
        handler.postDelayed(esconderFeedback, 3_000)
    }
}
