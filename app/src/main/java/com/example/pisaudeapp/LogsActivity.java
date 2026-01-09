package com.example.pisaudeapp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.widget.TooltipCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogsActivity extends AppCompatActivity implements LogManager.LogUpdateListener {

    private TextView logsTextView;
    private ImageButton btnRefresh;
    private ImageButton btnCopy;
    private ImageButton btnClear;
    private LogManager logManager;
    private Handler mainHandler;
    private boolean autoRefresh = true;
    private static final int REFRESH_INTERVAL = 1000; // 1 segundo

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logs); // Use o layout que criamos

        // Inicializar componentes
        initViews();
        setupToolbar();
        setupTooltips();
        setupListeners();

        // Inicializar LogManager
        logManager = LogManager.getInstance();
        mainHandler = new Handler(Looper.getMainLooper());

        // Carregar logs iniciais
        loadInitialLogs();

        // Iniciar atualização automática
        startAutoRefresh();
    }

    private void initViews() {
        logsTextView = findViewById(R.id.logs_text_view);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnCopy = findViewById(R.id.btn_copy);
        btnClear = findViewById(R.id.btn_clear);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Logs do Sistema");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
    }

    private void setupTooltips() {
        // Configurar tooltips para todos os botões
        TooltipCompat.setTooltipText(btnRefresh, "Atualizar logs");
        TooltipCompat.setTooltipText(btnCopy, "Copiar todos os logs para área de transferência");
        TooltipCompat.setTooltipText(btnClear, "Limpar todos os logs");
    }

    private void setupListeners() {
        // Botão Atualizar
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manualRefresh();
            }
        });

        // Botão Copiar
        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyLogsToClipboard();
            }
        });

        // Botão Limpar
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showClearConfirmation();
            }
        });

        // Long press para mais opções
        btnRefresh.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                toggleAutoRefresh();
                return true;
            }
        });
    }

    private void loadInitialLogs() {
        String allLogs = logManager.getAllLogsAsString();
        if (allLogs.isEmpty()) {
            logsTextView.setText("📋 Nenhum log disponível.\nOs logs aparecerão aqui automaticamente.");
            logsTextView.setTextColor(getResources().getColor(android.R.color.darker_gray));
        } else {
            logsTextView.setText(allLogs);
            logsTextView.setTextColor(getResources().getColor(R.color.text_primary));

            // Rolar para o topo (logs mais recentes)
            logsTextView.post(new Runnable() {
                @Override
                public void run() {
                    logsTextView.scrollTo(0, 0);
                }
            });
        }
    }

    private void manualRefresh() {
        loadInitialLogs();
        Toast.makeText(this, "✅ Logs atualizados", Toast.LENGTH_SHORT).show();

        // Feedback visual
        btnRefresh.animate()
                .rotationBy(360)
                .setDuration(500)
                .start();
    }

    private void copyLogsToClipboard() {
        String logsText = logManager.getAllLogsAsString();

        if (logsText.isEmpty()) {
            Toast.makeText(this, "📭 Nenhum log para copiar", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Logs do Sistema", logsText);
        clipboard.setPrimaryClip(clip);

        // Feedback visual
        btnCopy.animate()
                .scaleX(0.8f).scaleY(0.8f)
                .setDuration(200)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        btnCopy.animate()
                                .scaleX(1.0f).scaleY(1.0f)
                                .setDuration(200)
                                .start();
                    }
                })
                .start();

        Toast.makeText(this, "📋 Logs copiados para área de transferência", Toast.LENGTH_LONG).show();
    }

    private void showClearConfirmation() {
        if (logManager.getLogs().isEmpty()) {
            Toast.makeText(this, "📭 Nenhum log para limpar", Toast.LENGTH_SHORT).show();
            return;
        }

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("⚠️ Limpar Logs")
                .setMessage("Tem certeza que deseja limpar todos os logs? Esta ação não pode ser desfeita.")
                .setPositiveButton("Limpar", (dialog, which) -> {
                    clearAllLogs();
                })
                .setNegativeButton("Cancelar", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void clearAllLogs() {
        logManager.clearLogs();

        // Feedback visual
        btnClear.animate()
                .alpha(0.5f)
                .setDuration(300)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        btnClear.animate()
                                .alpha(1.0f)
                                .setDuration(300)
                                .start();
                    }
                })
                .start();

        Toast.makeText(this, "🧹 Todos os logs foram limpos", Toast.LENGTH_SHORT).show();
    }

    private void toggleAutoRefresh() {
        autoRefresh = !autoRefresh;

        if (autoRefresh) {
            startAutoRefresh();
            Toast.makeText(this, "🔄 Atualização automática: LIGADA", Toast.LENGTH_SHORT).show();
        } else {
            stopAutoRefresh();
            Toast.makeText(this, "⏸️ Atualização automática: DESLIGADA", Toast.LENGTH_SHORT).show();
        }

        // Feedback visual no botão
        btnRefresh.setAlpha(autoRefresh ? 1.0f : 0.6f);
    }

    private void startAutoRefresh() {
        autoRefresh = true;
        registerForUpdates();
    }

    private void stopAutoRefresh() {
        autoRefresh = false;
        unregisterFromUpdates();
    }

    private void registerForUpdates() {
        logManager.registerListener(this);
    }

    private void unregisterFromUpdates() {
        logManager.unregisterListener(this);
    }

    // Implementação do LogUpdateListener
    @Override
    public void onLogsUpdated(String newLog) {
        if (autoRefresh) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    String currentText = logsTextView.getText().toString();

                    if (currentText.contains("Nenhum log disponível")) {
                        logsTextView.setText(newLog);
                        logsTextView.setTextColor(getResources().getColor(R.color.text_primary));
                    } else {
                        logsTextView.setText(newLog + "\n" + currentText);
                    }

                    // Limitar o número de linhas visíveis para performance
                    trimLogsTextView();
                }
            });
        }
    }

    @Override
    public void onLogsCleared() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                logsTextView.setText("📋 Nenhum log disponível.\nOs logs aparecerão aqui automaticamente.");
                logsTextView.setTextColor(getResources().getColor(android.R.color.darker_gray));
            }
        });
    }

    private void trimLogsTextView() {
        String text = logsTextView.getText().toString();
        String[] lines = text.split("\n");

        if (lines.length > 200) { // Manter apenas as 200 linhas mais recentes
            StringBuilder trimmed = new StringBuilder();
            for (int i = 0; i < Math.min(lines.length, 200); i++) {
                trimmed.append(lines[i]).append("\n");
            }
            logsTextView.setText(trimmed.toString());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (autoRefresh) {
            registerForUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterFromUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterFromUpdates();
        mainHandler.removeCallbacksAndMessages(null);
    }

    // Método para adicionar log de exemplo (para teste)
    private void addTestLogs() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 1; i <= 10; i++) {
                        Thread.sleep(1000);
                        final int count = i;
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                logManager.addLog("Log de teste #" + count + " - " +
                                        new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
                            }
                        });
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}