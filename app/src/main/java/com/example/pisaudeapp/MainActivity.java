package com.example.pisaudeapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "PISaudeApp";
    private static final int PERMISSION_REQUEST_CODE = 100;

    // ===== MENU LATERAL =====
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;

    // ===== COMPONENTES DA HOME =====
    private LinearLayout cardLoadPatients, cardSendSMS;
    private TextView tvStatus, tvStats, tvWelcome, tvLastUpdate, tvProgress;
    private ProgressBar progressBar, progressBarBulk;
    private LinearLayout layoutProgress;

    // Dashboard como TEXTVIEWS
    private TextView tvTotalCount, tvIniciadosCount, tvFaltososCount;
    private TextView tvAbandonoCount, tvLevantamentoCount, tvChamadasCount;

    // Botões de controle
    private Button btnStartService, btnStopService, btnTestAuto;
    private TextView tvServiceStatus;

    // Nova seção de logs
    private TextView tvLogs;
    private LinearLayout layoutLogs;

    // API
    private static final int SMS_PERMISSION_REQUEST_CODE = 1;
    private ApiService apiService;
    private List<ApiService.Patient> patientsList;

    private int smsSent = 0;
    private int smsFailed = 0;
    private boolean isProcessing = false;

    private BroadcastReceiver autoServiceReceiver;
    private Handler handler;

    // ADICIONE ESTAS VARIÁVEIS GLOBAIS
    private static boolean isCycleProcessing = false;
    private static long lastCycleStartTime = 0;
    private static int lastProcessedCycleNumber = -1;

    // Para controle de logs na UI
    private List<String> uiLogs = new ArrayList<>();
    private static final int MAX_UI_LOGS = 50; // Aumentado para mostrar mais logs

    // SmsManager para envio direto de SMS
    private SmsManager smsManager;
    private LogManager logManager;
    private AboutDialog aboutDialog;
    private int requestCode;
    private String[] permissions;
    private int[] grantResults;


    @SuppressLint("NonConstantResourceId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate iniciado");
        setContentView(R.layout.activity_main);

        // Inicializar SmsManager
        smsManager = SmsManager.getDefault();

        // Inicializar LogManager
        logManager = LogManager.getInstance();

        // **ADICIONE ESTA LINHA**: Resetar estado de processamento
        isProcessing = false;

        // Inicializar Handler
        handler = new Handler(Looper.getMainLooper());

        // ========= INICIALIZA VIEWS =========
        initViews();

        // ========= CONFIGURA API =========
        apiService = new ApiService(this);

        // Verificar permissões
        if (!checkSmsPermissions()) {
            // Se não tiver permissão, solicitar
            requestSmsPermissions();
        }

        // Inicializar o diálogo
        aboutDialog = new AboutDialog(this);

        // ========= CONFIGURA MENU =========
        setupMenu();

        // ========= CONFIGURA BOTÕES =========
        setupClickListeners();

        // ========= CONFIGURA RECEIVER =========
        setupAutoServiceReceiver();

        // ========= VERIFICA PERMISSÕES =========
        checkAndRequestPermissions();

        // ========= VERIFICA STATUS DO SERVIÇO =========
        checkAutoServiceStatus();

        Log.d(TAG, "onCreate concluído");
    }

    private void initViews() {
        // Toolbar
        toolbar = findViewById(R.id.toolbar);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        // Cards clicáveis
        cardLoadPatients = findViewById(R.id.cardLoadPatients);
        cardSendSMS = findViewById(R.id.cardSendSMS);

        // Botões de controle
        btnStartService = findViewById(R.id.btnStartService);
        btnStopService = findViewById(R.id.btnStopService);
        btnTestAuto = findViewById(R.id.btnTestAuto);
        tvServiceStatus = findViewById(R.id.tvServiceStatus);

        // TextViews
        tvStatus = findViewById(R.id.tvStatus);
        tvStats = findViewById(R.id.tvStats);
        tvWelcome = findViewById(R.id.tvWelcome);
        tvLastUpdate = findViewById(R.id.tvLastUpdate);
        tvProgress = findViewById(R.id.tvProgress);

        // Progress bars
        progressBar = findViewById(R.id.progressBar);
        progressBarBulk = findViewById(R.id.progressBarBulk);
        layoutProgress = findViewById(R.id.layoutProgress);

        // Dashboard TextViews
        tvTotalCount = findViewById(R.id.tvTotalCount);
        tvIniciadosCount = findViewById(R.id.tvIniciadosCount);
        tvFaltososCount = findViewById(R.id.tvFaltososCount);
        tvAbandonoCount = findViewById(R.id.tvAbandonoCount);
        tvLevantamentoCount = findViewById(R.id.tvLevantamentoCount);
        tvChamadasCount = findViewById(R.id.tvChamadasCount);

        // Nova seção de logs - CORREÇÃO: Agora layoutLogs é o LinearLayout dentro do CardView
        tvLogs = findViewById(R.id.tvLogs);
        layoutLogs = findViewById(R.id.layoutLogs);

        // Estado inicial
        if (cardSendSMS != null) {
            cardSendSMS.setAlpha(0.5f);
        }
        resetDashboard();

        updateLastUpdateTime("Aguardando dados...");

        if (tvWelcome != null) {
            tvWelcome.setText("Gerencie pacientes e envie mensagens SMS");
        }

        // Inicializar logs
        updateLogs("🟢 Sistema iniciado. Aguardando ações...");

        // Mostrar seção de logs
        if (layoutLogs != null) {
            layoutLogs.setVisibility(View.VISIBLE);
        }
    }

    // Verificar se tem permissões de SMS
    private boolean checkSmsPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Versões anteriores não precisam de runtime permission
    }

    // Solicitar permissões de SMS
    private void requestSmsPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.SEND_SMS,
                            Manifest.permission.READ_SMS,
                            Manifest.permission.RECEIVE_SMS
                    },
                    SMS_PERMISSION_REQUEST_CODE);
        }
    }

    private void resetProcessingState() {
        Log.d(TAG, "🔄 Resetando estado de processamento");
        isProcessing = false;
        isCycleProcessing = false;

        runOnUiThread(() -> {
            showProgress(false);
            showLoading(false);

            if (tvStatus != null) {
                tvStatus.setText("Pronto para processar");
            }

            // Reabilitar todos os botões
            if (cardLoadPatients != null) {
                cardLoadPatients.setEnabled(true);
                cardLoadPatients.setAlpha(1.0f);
            }
            if (cardSendSMS != null && patientsList != null && !patientsList.isEmpty()) {
                cardSendSMS.setEnabled(true);
                cardSendSMS.setAlpha(1.0f);
            }
            if (btnStartService != null) {
                btnStartService.setEnabled(true);
                btnStartService.setAlpha(1.0f);
            }
            if (btnStopService != null) {
                btnStopService.setEnabled(true);
                btnStopService.setAlpha(1.0f);
            }
            if (btnTestAuto != null) {
                btnTestAuto.setEnabled(true);
                btnTestAuto.setAlpha(1.0f);
            }
        });
    }

    // Método para atualizar o status do serviço
    private void updateServiceStatus(boolean isRunning) {
        if (tvServiceStatus != null) {
            if (isRunning) {
                tvServiceStatus.setText("Serviço automático ativo");
                tvServiceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
            } else {
                tvServiceStatus.setText("Serviço automático parado");
                tvServiceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            }
        }
    }

    private void setupMenu() {
        // Configura o botão de menu no toolbar
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> {
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }

        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    int id = item.getItemId();

                    // Fecha o drawer
                    if (drawerLayout != null) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                    }

                    if (id == R.id.nav_logs) {
                        // Abrir tela de logs
                        Intent logsIntent = new Intent(MainActivity.this, LogsActivity.class);
                        startActivity(logsIntent);
                        return true;
                    }
                    else if (id == R.id.nav_settings) {
                        showSettingsDialog();
                        return true;
                    }
                    else if (id == R.id.nav_about) {
                        showAboutDialog();
                        return true;
                    }
                    else if (id == R.id.nav_logout) {
                        showLogoutConfirmation();
                        return true;
                    }
                    // Para o dashboard, já estamos na tela principal
                    else if (id == R.id.nav_dashboard) {
                        // Já estamos na dashboard, apenas fecha o drawer
                        return true;
                    }
                    if (id == R.id.nav_patients) {
                        // Abrir tela de pacientes
                        Intent patientsIntent = new Intent(MainActivity.this, PatientsActivity.class);
                        startActivity(patientsIntent);
                        return true;
                    }

                    return false;
                }
            });
        }
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Confirmar Saída")
                .setMessage("Deseja realmente sair do aplicativo?")
                .setPositiveButton("Sair", (dialog, which) -> {
                    // Parar o serviço automático antes de sair
                    stopAutoService();

                    // Adicionar log
                    addUiLog("🚪 Usuário saiu do aplicativo");

                    // Finalizar a aplicação
                    finish();
                })
                .setNegativeButton("Cancelar", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void setupClickListeners() {
        // Card Carregar Pacientes
        if (cardLoadPatients != null) {
            cardLoadPatients.setOnClickListener(v -> {
                if (!isProcessing) {
                    loadPatients();
                } else {
                    addUiLog("⚠️ Aguarde o processamento atual");
                }
            });
        }

        // Card Enviar Todos SMS
        if (cardSendSMS != null) {
            cardSendSMS.setOnClickListener(v -> {
                if (patientsList != null && !patientsList.isEmpty() && !isProcessing) {
                    showSendAllConfirmation();
                } else {
                    addUiLog("⚠️ Carregue pacientes primeiro");
                }
            });
        }

        // Botões de controle do serviço
        if (btnStartService != null) {
            btnStartService.setOnClickListener(v -> {
                Log.d(TAG, "▶️ Botão iniciar serviço pressionado");
                startAutoService();
                addUiLog("▶️ Serviço automático iniciado");
            });
        }

        if (btnStopService != null) {
            btnStopService.setOnClickListener(v -> {
                Log.d(TAG, "⏹️ Botão parar serviço pressionado");
                stopAutoService();
                addUiLog("⏹️ Serviço automático parado");
            });
        }

        if (btnTestAuto != null) {
            btnTestAuto.setOnClickListener(v -> {
                Log.d(TAG, "🔄 Botão testar ciclo pressionado");
                addUiLog("🔄 Iniciando ciclo de teste...");
                if (!isProcessing) {
                    loadPatientsForAutoCycle(999); // Usar número especial para teste
                } else {
                    addUiLog("⚠️ Aguarde o processamento atual");
                }
            });
        }
    }

    private void stopAutoService() {
        try {
            Intent stopIntent = new Intent(this, AutoService.class);
            stopIntent.setAction("STOP_AUTO_SERVICE");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(stopIntent);
            } else {
                startService(stopIntent);
            }
            addUiLog("⏹️ Comando para parar serviço enviado");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao parar serviço", e);
            addUiLog("❌ Erro ao parar serviço");
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void setupAutoServiceReceiver() {
        autoServiceReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.d(TAG, "📡 Broadcast recebido: " + action);

                if ("AUTO_CYCLE_ACTION".equals(action)) {
                    int cycleNumber = intent.getIntExtra("CYCLE_NUMBER", 0);
                    long timestamp = intent.getLongExtra("TIMESTAMP", 0);

                    Log.d(TAG, "🔄 CICLO AUTOMÁTICO #" + cycleNumber + " recebido às " + timestamp);
                    addUiLog("🔄 CICLO AUTOMÁTICO #" + cycleNumber + " recebido");

                    // Evitar processamento duplicado
                    if (cycleNumber == lastProcessedCycleNumber) {
                        Log.d(TAG, "⏭️ Ciclo #" + cycleNumber + " já foi processado, ignorando");
                        return;
                    }

                    // Se o último ciclo foi há menos de 1 minuto, aguardar
                    long timeSinceLastCycle = System.currentTimeMillis() - lastCycleStartTime;
                    if (timeSinceLastCycle < 60 * 1000L && lastCycleStartTime > 0) {
                        Log.d(TAG, "⏳ Aguardando - Último ciclo há " + (timeSinceLastCycle/1000) + "s");
                        return;
                    }

                    // Se já está processando, verificar se está travado
                    if (isCycleProcessing) {
                        long processingTime = System.currentTimeMillis() - lastCycleStartTime;
                        if (processingTime > 5 * 60 * 1000L) { // Mais de 5 minutos
                            Log.w(TAG, "🔓 Desbloqueando ciclo travado após " + (processingTime/1000) + "s");
                            isCycleProcessing = false;
                            isProcessing = false;
                            resetProcessingState();
                        } else {
                            Log.w(TAG, "⏸️ Já processando ciclo há " + (processingTime/1000) + "s");
                            return;
                        }
                    }

                    // Marcar como processando
                    isCycleProcessing = true;
                    lastCycleStartTime = System.currentTimeMillis();
                    lastProcessedCycleNumber = cycleNumber;

                    Log.d(TAG, "✅ Iniciando processamento do ciclo #" + cycleNumber);
                    addUiLog("✅ Iniciando processamento do ciclo #" + cycleNumber);

                    // Pequeno delay para estabilidade
                    handler.postDelayed(() -> {
                        loadPatientsForAutoCycle(cycleNumber);
                    }, 1000);

                } else if ("AUTO_SERVICE_STARTED".equals(action)) {
                    Log.d(TAG, "✅ Serviço automático iniciado");
                    addUiLog("✅ Serviço automático iniciado");
                    runOnUiThread(() -> {
                        updateServiceStatus(true);
                        if (tvStatus != null) {
                            tvStatus.setText("Serviço automático iniciado");
                        }
                    });
                } else if ("AUTO_SERVICE_STOPPED".equals(action)) {
                    Log.d(TAG, "⏹️ Serviço automático parado");
                    addUiLog("⏹️ Serviço automático parado");
                    runOnUiThread(() -> {
                        updateServiceStatus(false);
                        if (tvStatus != null) {
                            tvStatus.setText("Serviço automático parado");
                        }

                        // Resetar flags quando o serviço para
                        isCycleProcessing = false;
                        isProcessing = false;
                        lastProcessedCycleNumber = -1;
                        lastCycleStartTime = 0;
                        resetProcessingState();
                    });
                } else if ("SMS_SENT".equals(action)) {
                    String phone = intent.getStringExtra("PHONE");
                    String patientName = intent.getStringExtra("PATIENT_NAME");
                    boolean success = intent.getBooleanExtra("SUCCESS", false);

                    if (phone != null && patientName != null) {
                        if (success) {
                            addUiLog("📱 SMS enviado para " + patientName + " (" + phone + ")");
                        } else {
                            addUiLog("❌ Falha ao enviar SMS para " + patientName + " (" + phone + ")");
                        }
                    }
                } else if ("SMS_PROGRESS".equals(action)) {
                    int current = intent.getIntExtra("CURRENT", 0);
                    int total = intent.getIntExtra("TOTAL", 0);
                    int sent = intent.getIntExtra("SENT", 0);
                    int failed = intent.getIntExtra("FAILED", 0);

                    runOnUiThread(() -> {
                        updateProgress(sent, failed, total);
                    });
                }
            }
        };

        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction("AUTO_CYCLE_ACTION");
            filter.addAction("AUTO_SERVICE_STARTED");
            filter.addAction("AUTO_SERVICE_STOPPED");
            filter.addAction("SMS_SENT");
            filter.addAction("SMS_PROGRESS");

            // Prioridade alta para garantir recebimento
            filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);

            Log.d(TAG, "📡 Registrando BroadcastReceiver com prioridade alta...");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(autoServiceReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(autoServiceReceiver, filter);
            }

            Log.d(TAG, "✅ BroadcastReceiver registrado");
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao registrar BroadcastReceiver", e);
            addUiLog("❌ Erro na configuração do serviço automático");
        }
    }

    private void checkAutoServiceStatus() {
        try {
            Log.d(TAG, "🔍 Verificando status do AutoService");
            Intent checkIntent = new Intent(this, AutoService.class);
            checkIntent.setAction("CHECK_STATUS");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(checkIntent);
            } else {
                startService(checkIntent);
            }

            Log.d(TAG, "✅ Intent de verificação enviada para AutoService");
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao verificar AutoService", e);
        }
    }

    private void checkAndRequestPermissions() {
        String[] permissions;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Manifest.permission.FOREGROUND_SERVICE,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Manifest.permission.FOREGROUND_SERVICE
            };
        }

        boolean allGranted = true;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            Log.d(TAG, "Solicitando permissões...");
            addUiLog("📋 Solicitando permissões...");
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        } else {
            Log.d(TAG, "Todas as permissões já concedidas");
            addUiLog("✅ Todas as permissões concedidas");
            startAutoService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult");

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Log.d(TAG, "Todas as permissões concedidas");
                addUiLog("✅ Todas as permissões concedidas");
                startAutoService();
            } else {
                addUiLog("❌ Permissões necessárias para funcionamento!");
            }
        }
    }

    private void startAutoService() {
        try {
            Log.d(TAG, "🚀 Iniciando AutoService...");
            addUiLog("🚀 Iniciando AutoService...");

            Intent serviceIntent = new Intent(this, AutoService.class);
            serviceIntent.setAction("START_AUTO_SERVICE");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }

            Log.d(TAG, "✅ AutoService iniciado com sucesso");
            addUiLog("✅ AutoService iniciado com sucesso");

            runOnUiThread(() -> {
                updateServiceStatus(true);
                if (tvStatus != null) {
                    tvStatus.setText("Serviço automático iniciado");
                }
                if (tvWelcome != null) {
                    int interval = AutoService.getInterval(MainActivity.this);
                    tvWelcome.setText("Serviço automático ativo - Ciclo a cada " + interval + " minutos");
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "❌ Falha ao iniciar AutoService", e);
            addUiLog("❌ Falha ao iniciar AutoService: " + e.getMessage());
            runOnUiThread(() -> {
                updateServiceStatus(false);
                if (tvStatus != null) {
                    tvStatus.setText("Falha ao iniciar serviço");
                }
            });
        }
    }

    // ============ MÉTODOS PRINCIPAIS ============

    private void loadPatients() {
        if (isProcessing) {
            addUiLog("⚠️ Aguarde o processamento atual");
            return;
        }

        isProcessing = true;
        runOnUiThread(() -> {
            showLoading(true);
            if (tvStatus != null) {
                tvStatus.setText("Carregando pacientes...");
            }
            resetDashboard();
            addUiLog("📋 Carregando pacientes...");
        });

        new Thread(() -> {
            try {
                apiService.getPatients(new ApiService.ApiCallback() {
                    @Override
                    public void onSuccess(List<ApiService.Patient> patients) {
                        patientsList = patients;
                        Log.d(TAG, "Pacientes carregados manualmente: " + patients.size());
                        addUiLog("✅ " + patients.size() + " pacientes carregados");

                        runOnUiThread(() -> {
                            showLoading(false);
                            isProcessing = false;

                            if (patients.isEmpty()) {
                                if (tvStatus != null) {
                                    tvStatus.setText("Nenhum paciente encontrado");
                                }
                                if (cardSendSMS != null) {
                                    cardSendSMS.setAlpha(0.5f);
                                }
                                addUiLog("📭 Nenhum paciente encontrado");
                            } else {
                                if (tvStatus != null) {
                                    tvStatus.setText("✅ " + patients.size() + " pacientes carregados");
                                }
                                if (cardSendSMS != null) {
                                    cardSendSMS.setAlpha(1.0f);
                                    cardSendSMS.setEnabled(true);
                                }
                                updateStats();
                                updateDashboard(patients);
                                updateLastUpdateTime("Carregamento manual");
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Erro ao carregar pacientes: " + error);
                        addUiLog("❌ Erro ao carregar pacientes: " + error);

                        runOnUiThread(() -> {
                            showLoading(false);
                            isProcessing = false;

                            if (tvStatus != null) {
                                tvStatus.setText("❌ Erro: " + error);
                            }
                        });
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Exceção ao carregar pacientes", e);
                addUiLog("❌ Exceção ao carregar pacientes: " + e.getMessage());

                runOnUiThread(() -> {
                    showLoading(false);
                    isProcessing = false;

                    if (tvStatus != null) {
                        tvStatus.setText("Erro no carregamento");
                    }
                });
            }
        }).start();
    }

    private void loadPatientsForAutoCycle(int cycleNumber) {
        Log.d(TAG, "🔄 [Ciclo #" + cycleNumber + "] Iniciando carregamento automático");
        addUiLog("🔄 [Ciclo #" + cycleNumber + "] Iniciando carregamento automático");

        // Verificar se já está processando (double-check)
        if (isProcessing) {
            Log.w(TAG, "⚠️ [Ciclo #" + cycleNumber + "] Já está processando, ignorando");
            addUiLog("⚠️ [Ciclo #" + cycleNumber + "] Já está processando, ignorando");
            isCycleProcessing = false; // Liberar o lock
            return;
        }

        isProcessing = true;

        // TIMEOUT: 10 minutos para todo o processo
        handler.postDelayed(() -> {
            if (isProcessing) {
                Log.w(TAG, "⏰ [Ciclo #" + cycleNumber + "] TIMEOUT - Resetando");
                addUiLog("⏰ [Ciclo #" + cycleNumber + "] TIMEOUT - Resetando");
                resetProcessingState();
                isCycleProcessing = false;
            }
        }, 10 * 60 * 1000L);

        runOnUiThread(() -> {
            if (tvStatus != null) {
                tvStatus.setText("🔄 Ciclo #" + cycleNumber + " em execução...");
            }
            showProgress(true);
        });

        // Usar uma instância local da ApiService
        ApiService autoApiService = new ApiService(this);

        autoApiService.getPatients(new ApiService.ApiCallback() {
            @Override
            public void onSuccess(List<ApiService.Patient> patients) {
                Log.d(TAG, "✅ [Ciclo #" + cycleNumber + "] " + patients.size() + " pacientes encontrados");
                addUiLog("✅ [Ciclo #" + cycleNumber + "] " + patients.size() + " pacientes encontrados");

                runOnUiThread(() -> {
                    if (patients.isEmpty()) {
                        Log.w(TAG, "📭 [Ciclo #" + cycleNumber + "] Nenhum paciente encontrado");
                        addUiLog("📭 [Ciclo #" + cycleNumber + "] Nenhum paciente encontrado");
                        finalizeCycle(cycleNumber, 0, 0, true);
                        return;
                    }

                    // Remover duplicados baseado no telefone
                    List<ApiService.Patient> uniquePatients = removeDuplicatePatients(patients);
                    Log.d(TAG, "🔍 [Ciclo #" + cycleNumber + "] " +
                            patients.size() + " -> " + uniquePatients.size() + " (sem duplicados)");
                    addUiLog("🔍 [Ciclo #" + cycleNumber + "] " + uniquePatients.size() + " pacientes únicos");

                    patientsList = uniquePatients;

                    updateStats();
                    updateDashboard(uniquePatients);
                    updateLastUpdateTime("Ciclo #" + cycleNumber);

                    // Iniciar envio de SMS
                    Log.d(TAG, "📤 [Ciclo #" + cycleNumber + "] Iniciando envio para " + uniquePatients.size() + " pacientes");
                    addUiLog("📤 [Ciclo #" + cycleNumber + "] Iniciando envio para " + uniquePatients.size() + " pacientes");
                    sendAllSMSAuto(uniquePatients, cycleNumber);
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ [Ciclo #" + cycleNumber + "] Erro: " + error);
                addUiLog("❌ [Ciclo #" + cycleNumber + "] Erro: " + error);

                runOnUiThread(() -> {
                    finalizeCycle(cycleNumber, 0, 0, false);

                    // Tentar novamente após 1 minuto
                    handler.postDelayed(() -> {
                        if (!isProcessing && !isCycleProcessing) {
                            Log.d(TAG, "🔄 [Ciclo #" + cycleNumber + "] Tentando novamente...");
                            addUiLog("🔄 [Ciclo #" + cycleNumber + "] Tentando novamente...");
                            loadPatientsForAutoCycle(cycleNumber);
                        }
                    }, 60 * 1000L);
                });
            }
        });
    }

    private List<ApiService.Patient> removeDuplicatePatients(List<ApiService.Patient> patients) {
        List<ApiService.Patient> uniqueList = new ArrayList<>();
        Set<String> seenContacts = new HashSet<>();

        for (ApiService.Patient patient : patients) {
            if (patient.contact != null && !patient.contact.trim().isEmpty()) {
                String contactKey = patient.contact.trim().replace("+", "").replace(" ", "");
                if (!seenContacts.contains(contactKey)) {
                    seenContacts.add(contactKey);
                    uniqueList.add(patient);
                }
            }
        }

        return uniqueList;
    }

    private void finalizeCycle(int cycleNumber, int successCount, int failureCount, boolean success) {
        Log.d(TAG, "🎯 [Ciclo #" + cycleNumber + "] FINALIZADO: " +
                successCount + " sucessos, " + failureCount + " falhas");

        String logMessage = success ?
                "✅ Ciclo #" + cycleNumber + " concluído (" + successCount + " SMS)" :
                "❌ Ciclo #" + cycleNumber + " falhou";
        addUiLog(logMessage);

        isProcessing = false;
        isCycleProcessing = false;

        runOnUiThread(() -> {
            showProgress(false);
            showLoading(false);

            if (tvStatus != null) {
                tvStatus.setText(success ?
                        "✅ Ciclo #" + cycleNumber + " concluído (" + successCount + " SMS)" :
                        "❌ Ciclo #" + cycleNumber + " falhou");
            }
        });
    }

    private void sendAllSMSAuto(List<ApiService.Patient> patients, int cycleNumber) {
        if (patients == null || patients.isEmpty()) {
            Log.d(TAG, "📭 [Ciclo #" + cycleNumber + "] Nenhum paciente para SMS");
            addUiLog("📭 [Ciclo #" + cycleNumber + "] Nenhum paciente para SMS");
            finalizeCycle(cycleNumber, 0, 0, true);
            return;
        }

        Log.d(TAG, "🚀 [Ciclo #" + cycleNumber + "] Enviando SMS para " + patients.size() + " pacientes");
        addUiLog("🚀 [Ciclo #" + cycleNumber + "] Enviando SMS para " + patients.size() + " pacientes");

        // Contadores
        final int[] sent = {0};
        final int[] failed = {0};
        final int totalPatients = patients.size();

        runOnUiThread(() -> {
            if (tvStatus != null) {
                tvStatus.setText("📤 [Ciclo #" + cycleNumber + "] Enviando " + totalPatients + " SMS...");
            }
        });

        // Método recursivo para envio sequencial
        sendSmsSequentially(patients, cycleNumber, 0, sent, failed, totalPatients);
    }

    // Método auxiliar recursivo para envio sequencial
    private void sendSmsSequentially(final List<ApiService.Patient> patients,
                                     final int cycleNumber,
                                     final int index,
                                     final int[] sent,
                                     final int[] failed,
                                     final int totalPatients) {

        if (index >= totalPatients) {
            // Todos os SMS foram processados
            Log.d(TAG, "✅ [Ciclo #" + cycleNumber + "] Envio concluído: " +
                    sent[0] + "/" + totalPatients);
            addUiLog("✅ [Ciclo #" + cycleNumber + "] Envio concluído: " + sent[0] + "/" + totalPatients);

            // Enviar broadcast com progresso final
            Intent progressIntent = new Intent("SMS_PROGRESS");
            progressIntent.putExtra("CURRENT", totalPatients);
            progressIntent.putExtra("TOTAL", totalPatients);
            progressIntent.putExtra("SENT", sent[0]);
            progressIntent.putExtra("FAILED", failed[0]);
            sendBroadcast(progressIntent);

            finalizeCycle(cycleNumber, sent[0], failed[0], true);
            return;
        }

        // CORREÇÃO: Criar cópias finais das variáveis
        final ApiService.Patient currentPatient = patients.get(index);
        final String phoneNumber = currentPatient.contact != null ?
                currentPatient.contact.trim().replace("+", "").replace(" ", "") : "";

        if (phoneNumber.isEmpty()) {
            Log.w(TAG, "📭 [Ciclo #" + cycleNumber + "] Paciente sem telefone: " + currentPatient.fullname);
            addUiLog("📭 Paciente sem telefone: " + currentPatient.fullname);

            // Processar próximo paciente imediatamente
            sendSmsSequentially(patients, cycleNumber, index + 1, sent, failed, totalPatients);
            return;
        }

        // Verificar se o número é válido (mínimo 9 dígitos)
        if (phoneNumber.length() < 9) {
            Log.w(TAG, "❌ [Ciclo #" + cycleNumber + "] Número inválido: " + phoneNumber);
            addUiLog("❌ Número inválido: " + currentPatient.fullname);
            failed[0]++;

            // CORREÇÃO: Usar variáveis finais
            final String patientName = currentPatient.fullname != null ? currentPatient.fullname : "Paciente";

            // Enviar broadcast para atualizar UI
            Intent smsIntent = new Intent("SMS_SENT");
            smsIntent.putExtra("PHONE", phoneNumber);
            smsIntent.putExtra("PATIENT_NAME", patientName);
            smsIntent.putExtra("SUCCESS", false);
            sendBroadcast(smsIntent);

            // Processar próximo paciente após breve pausa
            handler.postDelayed(() -> {
                sendSmsSequentially(patients, cycleNumber, index + 1, sent, failed, totalPatients);
            }, 500);
            return;
        }

        Log.d(TAG, "📲 [Ciclo #" + cycleNumber + "] " + (index + 1) + "/" + totalPatients + ": " + currentPatient.fullname);
        addUiLog("📲 Enviando para: " + currentPatient.fullname + " (" + phoneNumber + ")");

        // Gerar mensagem personalizada
        final String message = generateSmsMessage(currentPatient);

        // CORREÇÃO: Criar cópias finais para uso no thread
        final String finalPatientName = currentPatient.fullname != null ? currentPatient.fullname : "Paciente";

        try {
            // Enviar SMS usando SmsManager
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);

            Log.d(TAG, "✅ SMS enviado para: " + phoneNumber);
            sent[0]++;

            // Enviar broadcast para atualizar UI
            Intent smsIntent = new Intent("SMS_SENT");
            smsIntent.putExtra("PHONE", phoneNumber);
            smsIntent.putExtra("PATIENT_NAME", finalPatientName);
            smsIntent.putExtra("SUCCESS", true);
            sendBroadcast(smsIntent);

        } catch (Exception e) {
            Log.e(TAG, "❌ Falha ao enviar SMS para: " + phoneNumber, e);
            failed[0]++;

            Intent smsIntent = new Intent("SMS_SENT");
            smsIntent.putExtra("PHONE", phoneNumber);
            smsIntent.putExtra("PATIENT_NAME", finalPatientName);
            smsIntent.putExtra("SUCCESS", false);
            sendBroadcast(smsIntent);
        }

        // Atualizar progresso na UI
        runOnUiThread(() -> {
            updateProgress(sent[0], failed[0], totalPatients);
        });

        // Enviar broadcast com progresso
        Intent progressIntent = new Intent("SMS_PROGRESS");
        progressIntent.putExtra("CURRENT", index + 1);
        progressIntent.putExtra("TOTAL", totalPatients);
        progressIntent.putExtra("SENT", sent[0]);
        progressIntent.putExtra("FAILED", failed[0]);
        sendBroadcast(progressIntent);

        // Chamar recursivamente para o próximo paciente após 2 segundos
        handler.postDelayed(() -> {
            sendSmsSequentially(patients, cycleNumber, index + 1, sent, failed, totalPatients);
        }, 2000);
    }

    private String generateSmsMessage(ApiService.Patient patient) {
        String estado = patient.stateDescription != null ? patient.stateDescription : "tratamento";
        String nome = patient.fullname != null ? patient.fullname.split(" ")[0] : "Paciente";

        return String.format(Locale.getDefault(),
                "Olá %s,\n\n" +
                        "Esta é uma mensagem do Centro de Saúde.\n" +
                        "Seu estado atual: %s.\n" +
                        "Por favor, mantenha seu tratamento atualizado.\n\n" +
                        "Atenciosamente,\nEquipe de Saúde",
                nome, estado);
    }

    private void sendAllSMS() {
        if (patientsList == null || patientsList.isEmpty()) {
            addUiLog("⚠️ Nenhum paciente para enviar SMS");
            return;
        }

        if (isProcessing) {
            addUiLog("⚠️ Aguarde o processamento atual");
            return;
        }

        isProcessing = true;
        smsSent = 0;
        smsFailed = 0;

        final int totalPatients = patientsList.size();

        runOnUiThread(() -> {
            showProgress(true);
            if (tvStatus != null) {
                tvStatus.setText("Enviando SMS manualmente...");
            }
            addUiLog("📤 Iniciando envio manual para " + totalPatients + " pacientes");
        });

        // Envio sequencial manual
        sendSmsSequentially(patientsList, 0, 0, new int[]{0}, new int[]{0}, totalPatients);
    }

    private void showSendAllConfirmation() {
        if (patientsList == null || patientsList.isEmpty()) {
            addUiLog("⚠️ Nenhum paciente para enviar SMS");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Enviar SMS em Massa")
                .setMessage("Deseja enviar SMS para " + patientsList.size() + " pacientes?\n\n" +
                        "Esta operação pode levar alguns minutos.")
                .setPositiveButton("Enviar", (dialog, which) -> {
                    addUiLog("📤 Confirmado envio para " + patientsList.size() + " pacientes");
                    sendAllSMS();
                })
                .setNegativeButton("Cancelar", null)
                .setIcon(android.R.drawable.ic_dialog_email)
                .show();
    }

    private void updateDashboard(final List<ApiService.Patient> patients) {
        if (patients == null || patients.isEmpty()) {
            resetDashboard();
            return;
        }

        int total = patients.size();
        int iniciados = 0;
        int faltosos = 0;
        int abandono = 0;
        int levantamento = 0;
        int chamada = 0;

        for (ApiService.Patient p : patients) {
            String state = p.stateDescription != null ? p.stateDescription.toLowerCase() : "";

            if (state.startsWith("inic")) iniciados++;
            if (state.contains("falt")) faltosos++;
            if (state.contains("abando")) abandono++;
            if (state.contains("levant")) levantamento++;
            if (state.contains("chamand") || state.contains("chamad")) chamada++;
        }

        final int finalTotal = total;
        final int finalIniciados = iniciados;
        final int finalFaltosos = faltosos;
        final int finalAbandono = abandono;
        final int finalLevantamento = levantamento;
        final int finalChamada = chamada;

        runOnUiThread(() -> {
            if (tvTotalCount != null) tvTotalCount.setText(String.valueOf(finalTotal));
            if (tvIniciadosCount != null) tvIniciadosCount.setText(String.valueOf(finalIniciados));
            if (tvFaltososCount != null) tvFaltososCount.setText(String.valueOf(finalFaltosos));
            if (tvAbandonoCount != null) tvAbandonoCount.setText(String.valueOf(finalAbandono));
            if (tvLevantamentoCount != null) tvLevantamentoCount.setText(String.valueOf(finalLevantamento));
            if (tvChamadasCount != null) tvChamadasCount.setText(String.valueOf(finalChamada));
        });
    }

    private void resetDashboard() {
        runOnUiThread(() -> {
            if (tvTotalCount != null) tvTotalCount.setText("0");
            if (tvIniciadosCount != null) tvIniciadosCount.setText("0");
            if (tvFaltososCount != null) tvFaltososCount.setText("0");
            if (tvAbandonoCount != null) tvAbandonoCount.setText("0");
            if (tvLevantamentoCount != null) tvLevantamentoCount.setText("0");
            if (tvChamadasCount != null) tvChamadasCount.setText("0");
        });
    }

    private void updateStats() {
        if (patientsList != null && tvStats != null) {
            int valid = 0;
            for (ApiService.Patient p : patientsList) {
                if (p.isValid()) valid++;
            }

            tvStats.setText("Total: " + patientsList.size() + " | Válidos: " + valid);
        }
    }

    private void updateProgress(int sent, int failed, int total) {
        if (progressBarBulk == null || tvProgress == null) {
            return;
        }

        int done = sent + failed;
        progressBarBulk.setMax(total);
        progressBarBulk.setProgress(done);

        tvProgress.setText(done + "/" + total + " enviados (✓ " + sent + " | ✗ " + failed + ")");
    }

    private void updateLastUpdateTime(String action) {
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        String date = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());

        if (tvLastUpdate != null) {
            tvLastUpdate.setText(action + ": " + time + " - " + date);
        }
    }

    // ============ MÉTODOS DE LOGS NA UI ============

    private void addUiLog(String message) {
        logManager.addLog(message);

        // Opcional: manter os logs na UI da MainActivity também
        runOnUiThread(() -> {
            // Se você quiser manter os logs locais também
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String logEntry = "[" + time + "] " + message;

            uiLogs.add(0, logEntry);
            if (uiLogs.size() > MAX_UI_LOGS) {
                uiLogs = uiLogs.subList(0, MAX_UI_LOGS);
            }
            updateLogsUI();
            updateLogsCount();
        });
    }

    private void updateLogsUI() {
        runOnUiThread(() -> {
            if (tvLogs != null) {
                StringBuilder logsText = new StringBuilder();
                for (String log : uiLogs) {
                    logsText.append(log).append("\n");
                }
                tvLogs.setText(logsText.toString());

                // Mostrar seção de logs se estiver oculta
                if (layoutLogs != null && layoutLogs.getVisibility() != View.VISIBLE) {
                    layoutLogs.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void updateLogsCount() {
        runOnUiThread(() -> {
            TextView tvLogsCount = findViewById(R.id.tvLogsCount);
            if (tvLogsCount != null) {
                tvLogsCount.setText(uiLogs.size() + " logs");
            }
        });
    }

    private void updateLogs(String message) {
        addUiLog(message);
    }

    // ============ MÉTODOS DE VISUALIZAÇÃO ============

    private void showLoading(boolean show) {
        runOnUiThread(() -> {
            if (progressBar != null) {
                progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            }
            if (cardLoadPatients != null) {
                cardLoadPatients.setEnabled(!show);
                cardLoadPatients.setAlpha(show ? 0.5f : 1.0f);
            }
            if (cardSendSMS != null && show) {
                cardSendSMS.setAlpha(0.5f);
            }
        });
    }

    private void showProgress(boolean show) {
        runOnUiThread(() -> {
            if (layoutProgress != null) {
                layoutProgress.setVisibility(show ? View.VISIBLE : View.GONE);
            }
            if (cardLoadPatients != null) {
                cardLoadPatients.setEnabled(!show);
                cardLoadPatients.setAlpha(show ? 0.5f : 1.0f);
            }
            if (cardSendSMS != null) {
                cardSendSMS.setEnabled(!show);
                cardSendSMS.setAlpha(show ? 0.5f : 1.0f);
            }

            if (btnStartService != null) {
                btnStartService.setEnabled(!show);
                btnStartService.setAlpha(show ? 0.5f : 1.0f);
            }
            if (btnStopService != null) {
                btnStopService.setEnabled(!show);
                btnStopService.setAlpha(show ? 0.5f : 1.0f);
            }
            if (btnTestAuto != null) {
                btnTestAuto.setEnabled(!show);
                btnTestAuto.setAlpha(show ? 0.5f : 1.0f);
            }
        });
    }

    // ============ DIALOGS ============

    private void showSettingsDialog() {
        String[] intervals = {"5 minutos", "15 minutos", "30 minutos", "1 hora", "2 horas"};
        new AlertDialog.Builder(this)
                .setTitle("Configurar Intervalo")
                .setSingleChoiceItems(intervals, 1, (dialog, which) -> {
                    int minutes = getMinutesFromIndex(which);
                    AutoService.updateInterval(this, minutes);
                    if (tvStatus != null) {
                        tvStatus.setText("Intervalo: " + minutes + " minutos");
                    }
                    if (tvWelcome != null) {
                        tvWelcome.setText("Serviço ativo - Próximo ciclo em " + minutes + " minutos");
                    }
                    addUiLog("⏰ Intervalo atualizado para " + minutes + " minutos");
                    dialog.dismiss();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showAboutDialog() {
        aboutDialog.show();
    }

    private int getMinutesFromIndex(int index) {
        switch (index) {
            case 0: return 5;
            case 1: return 15;
            case 2: return 30;
            case 3: return 60;
            case 4: return 120;
            default: return 15;
        }
    }

    // ============ CICLO DE VIDA ============

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        addUiLog("🔄 Aplicativo retomado");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        addUiLog("⏸️ Aplicativo pausado");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy iniciado");

        if (autoServiceReceiver != null) {
            try {
                unregisterReceiver(autoServiceReceiver);
                Log.d(TAG, "BroadcastReceiver desregistrado");
                addUiLog("📡 BroadcastReceiver desregistrado");
            } catch (Exception e) {
                Log.e(TAG, "Erro ao desregistrar receiver", e);
            }
        }

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        Log.d(TAG, "onDestroy concluído");
        addUiLog("🔴 Sistema finalizado");
    }
}