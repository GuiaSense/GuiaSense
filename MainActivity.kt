class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding // Usando View Binding
    private var alcanceMaximo = 1.00 // em metros
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupRadioButtons()
        setupSeekBar()
        setupSaveButton()
        updateVibrationInfo()
    }
    
    private fun setupRadioButtons() {
        binding.rgModos.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_perto -> {
                    alcanceMaximo = 1.00
                    binding.seekbarAlcance.progress = 100
                    binding.txtAlcanceAtual.text = "1,00m"
                }
                R.id.rb_medio -> {
                    alcanceMaximo = 1.50
                    binding.seekbarAlcance.progress = 150
                    binding.txtAlcanceAtual.text = "1,50m"
                }
            }
            updateVibrationInfo()
            showFeedback("Modo ${if(checkedId == R.id.rb_perto) "Perto" else "Médio"} selecionado")
        }
    }
    
    private fun setupSeekBar() {
        binding.seekbarAlcance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                alcanceMaximo = progress / 100.0
                binding.txtAlcanceAtual.text = String.format("%.2fm", alcanceMaximo)
                
                // Desmarca os RadioButtons se o usuário ajustar manualmente
                binding.rgModos.clearCheck()
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
                "• < 0,60m → Vibração Forte\n" +
                "• 0,60m - 0,80m → Vibração Média\n" +
                "• 0,80m - 1,00m → Vibração Leve"
            }
            alcanceMaximo == 1.50 -> {
                "📏 Modo Médio (1,50m):\n" +
                "• < 0,40m → Vibração Forte\n" +
                "• 0,40m - 0,80m → Vibração Média\n" +
                "• 0,80m - 1,50m → Vibração Leve"
            }
            else -> {
                "📏 Modo Personalizado (${String.format("%.2f", alcanceMaximo)}m):\n" +
                "• < ${String.format("%.2f", alcanceMaximo * 0.4)}m → Vibração Forte\n" +
                "• ${String.format("%.2f", alcanceMaximo * 0.4)}m - ${String.format("%.2f", alcanceMaximo * 0.7)}m → Vibração Média\n" +
                "• ${String.format("%.2f", alcanceMaximo * 0.7)}m - ${String.format("%.2f", alcanceMaximo)}m → Vibração Leve"
            }
        }
        binding.txtInfoVibracao.text = info
    }
    
    private fun setupSaveButton() {
        binding.btnSalvar.setOnClickListener {
            // Salvar no Firebase ou enviar para o óculos via Bluetooth/WiFi
            saveToFirebase(alcanceMaximo)
            sendToGlasses(alcanceMaximo)
            showFeedback("✅ Configuração salva! Alcance: ${String.format("%.2f", alcanceMaximo)}m")
        }
    }
    
    private fun saveToFirebase(alcance: Double) {
        // Implementar Firebase
        val database = FirebaseDatabase.getInstance().reference
        val config = mapOf(
            "alcance_maximo" to alcance,
            "timestamp" to System.currentTimeMillis()
        )
        database.child("configuracoes").child("usuario_id").setValue(config)
            .addOnSuccessListener {
                Log.d("Firebase", "Configuração salva com sucesso")
            }
    }
    
    private fun sendToGlasses(alcance: Double) {
        // Enviar via Bluetooth ou WiFi para o óculos
        // Exemplo: Enviar comando "SET_RANGE:1.50"
    }
    
    private fun showFeedback(message: String) {
        binding.txtFeedback.text = message
        binding.txtFeedback.visibility = View.VISIBLE
        
        Handler(Looper.getMainLooper()).postDelayed({
            binding.txtFeedback.visibility = View.GONE
        }, 3000)
    }
    
    // Simulação de leitura do sensor (para testes)
    private fun simulateSensorReading(distance: Double) {
        binding.txtDistancia.text = String.format("%.2fm", distance)
        
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
        
        binding.txtIntensidade.text = intensidade
        
        // Atualizar barra visual
        val percentual = (distance / alcanceMaximo).coerceIn(0.0, 1.0)
        val layoutParams = binding.viewIntensidade.layoutParams
        layoutParams.width = (binding.viewBarraIntensidade.width * percentual).toInt()
        binding.viewIntensidade.layoutParams = layoutParams
    }
}