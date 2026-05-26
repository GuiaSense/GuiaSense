package com.guiasense.dehjp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.SeekBar
import android.view.View
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import android.widget.RadioGroup
import android.widget.RadioButton
import android.widget.Button
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private var alcanceMaximo = 1.00 // em metros

    private lateinit var rgModos: RadioGroup
    private lateinit var seekbarAlcance: SeekBar
    private lateinit var txtAlcanceAtual: TextView
    private lateinit var txtInfoVibracao: TextView
    private lateinit var btnSalvar: Button
    private lateinit var txtFeedback: TextView
    private lateinit var txtDistancia: TextView
    private lateinit var txtIntensidade: TextView
    private lateinit var viewIntensidade: View
    private lateinit var viewBarraIntensidade: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.guiasense.dehjp.R.layout.activity_main)

        rgModos = findViewById(com.guiasense.dehjp.R.id.rg_modos)
        seekbarAlcance = findViewById(com.guiasense.dehjp.R.id.seekbar_alcance)
        txtAlcanceAtual = findViewById(com.guiasense.dehjp.R.id.txt_alcance_atual)
        txtInfoVibracao = findViewById(com.guiasense.dehjp.R.id.txt_info_vibracao)
        btnSalvar = findViewById(com.guiasense.dehjp.R.id.btn_salvar)
        txtFeedback = findViewById(com.guiasense.dehjp.R.id.txt_feedback)
        txtDistancia = findViewById(com.guiasense.dehjp.R.id.txt_distancia)
        txtIntensidade = findViewById(com.guiasense.dehjp.R.id.txt_intensidade)
        viewIntensidade = findViewById(com.guiasense.dehjp.R.id.view_intensidade)
        viewBarraIntensidade = findViewById(com.guiasense.dehjp.R.id.view_barra_intensidade)

        setupRadioButtons()
        setupSeekBar()
        setupSaveButton()
        updateVibrationInfo()
    }

    private fun setupRadioButtons() {
        rgModos.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                com.guiasense.dehjp.R.id.rb_perto -> {
                    alcanceMaximo = 1.00
                    seekbarAlcance.progress = 100
                    txtAlcanceAtual.text = "1,00m"
                }
                com.guiasense.dehjp.R.id.rb_medio -> {
                    alcanceMaximo = 1.50
                    seekbarAlcance.progress = 150
                    txtAlcanceAtual.text = "1,50m"
                }
            }
            updateVibrationInfo()
            showFeedback("Modo ${if(checkedId == com.guiasense.dehjp.R.id.rb_perto) "Perto" else "Médio"} selecionado")
        }
    }

    private fun setupSeekBar() {
        seekbarAlcance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                alcanceMaximo = progress / 100.0
                txtAlcanceAtual.text = String.format("%.2fm", alcanceMaximo)

                // Desmarca os RadioButtons se o usuário ajustar manualmente
                rgModos.clearCheck()
                updateVibrationInfo()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateVibrationInfo() {
        val info = when {
            alcanceMaximo == 1.00 -> {
                "📏 Modo Perto (1,00m):\n" +
                        "• &lt; 0,60m → Vibração Forte\n" +
                        "• 0,60m - 0,80m → Vibração Média\n" +
                        "• 0,80m - 1,00m → Vibração Leve"
            }
            alcanceMaximo == 1.50 -> {
                "📏 Modo Médio (1,50m):\n" +
                        "• &lt; 0,40m → Vibração Forte\n" +
                        "• 0,40m - 0,80m → Vibração Média\n" +
                        "• 0,80m - 1,50m → Vibração Leve"
            }
            else -> {
                "📏 Modo Personalizado (${String.format("%.2f", alcanceMaximo)}m):\n" +
                        "• &lt; ${String.format("%.2f", alcanceMaximo * 0.4)}m → Vibração Forte\n" +
                        "• ${String.format("%.2f", alcanceMaximo * 0.4)}m - ${String.format("%.2f", alcanceMaximo * 0.7)}m → Vibração Média\n" +
                        "• ${String.format("%.2f", alcanceMaximo * 0.7)}m - ${String.format("%.2f", alcanceMaximo)}m → Vibração Leve"
            }
        }
        txtInfoVibracao.text = info
    }

    private fun setupSaveButton() {
        btnSalvar.setOnClickListener {
            // Salvar localmente e enviar para o óculos via Bluetooth/WiFi
            // Firebase não está configurado aqui; usando stub
            saveToFirebase(alcanceMaximo)
            sendToGlasses(alcanceMaximo)
            showFeedback("✅ Configuração salva! Alcance: ${String.format("%.2f", alcanceMaximo)}m")
        }
    }

    private fun saveToFirebase(alcance: Double) {
        try {
            val database = FirebaseDatabase.getInstance().reference
            val config = mapOf(
                "alcance_maximo" to alcance,
                "timestamp" to System.currentTimeMillis()
            )
            database.child("configuracoes").child("usuario_id").setValue(config)
                .addOnSuccessListener {
                    Log.d("Firebase", "Configuração salva com sucesso")
                }
                .addOnFailureListener { e ->
                    Log.e("Firebase", "Erro ao salvar configuração", e)
                }
        } catch (e: Exception) {
            Log.e("Firebase", "Falha ao acessar Firebase", e)
        }
    }

    private fun sendToGlasses(alcance: Double) {
        // Enviar via Bluetooth ou WiFi para o óculos
    }

    private fun showFeedback(message: String) {
        txtFeedback.text = message
        txtFeedback.visibility = View.VISIBLE

        Handler(Looper.getMainLooper()).postDelayed({
            txtFeedback.visibility = View.GONE
        }, 3000)
    }

    // Simulação de leitura do sensor (para testes)
    private fun simulateSensorReading(distance: Double) {
        txtDistancia.text = String.format("%.2fm", distance)

        val intensidade = when {
            alcanceMaximo == 1.00 -> {
                when {
                    distance < 0.60 -> "Forte 🔴"
                    distance < 0.80 -> "Média 🟠"
                    distance < 1.00 -> "Leve 🟡"
                    else -> "Nenhuma ⚪"
                }
            }
            alcanceMaximo == 1.50 -> {
                when {
                    distance < 0.40 -> "Forte 🔴"
                    distance < 0.80 -> "Média 🟠"
                    distance < 1.50 -> "Leve 🟡"
                    else -> "Nenhuma ⚪"
                }
            }
            else -> {
                val forte = alcanceMaximo * 0.4
                val media = alcanceMaximo * 0.7
                when {
                    distance < forte -> "Forte 🔴"
                    distance < media -> "Média 🟠"
                    distance < alcanceMaximo -> "Leve 🟡"
                    else -> "Nenhuma ⚪"
                }
            }
        }

        txtIntensidade.text = intensidade

        // Atualizar barra visual
        val percentual = (distance / alcanceMaximo).coerceIn(0.0, 1.0)
        val layoutParams = viewIntensidade.layoutParams
        layoutParams.width = (viewBarraIntensidade.width * percentual).toInt()
        viewIntensidade.layoutParams = layoutParams
    }
}