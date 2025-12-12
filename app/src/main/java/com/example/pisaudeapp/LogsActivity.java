package com.example.pisaudeapp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class LogsActivity extends AppCompatActivity implements LogManager.LogUpdateListener {

    private TextView logsTextView;
    private LogManager logManager;
    private Handler handler;
    private boolean isActivityActive = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logs);

        // Inicializar Handler
        handler = new Handler(Looper.getMainLooper());

        // Configurar toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Logs de Execução");

        // Configurar TextView para scroll
        logsTextView = findViewById(R.id.logs_text_view);
        logsTextView.setMovementMethod(new ScrollingMovementMethod());

        // Inicializar LogManager
        logManager = LogManager.getInstance();

        // Registrar-se como listener
        logManager.registerListener(this);

        // Carregar logs existentes
        updateLogsDisplay();

        // Configurar botão de copiar
        Button btnCopy = findViewById(R.id.btn_copy);
        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyLogsToClipboard();
            }
        });

        // Botão para limpar logs
        Button btnClear = findViewById(R.id.btn_clear);
        if (btnClear != null) {
            btnClear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clearLogs();
                }
            });
        }

        // Botão para atualizar
        Button btnRefresh = findViewById(R.id.btn_refresh);
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    updateLogsDisplay();
                }
            });
        }
    }

    private void updateLogsDisplay() {
        String logs = logManager.getAllLogsAsString();
        if (logs.isEmpty()) {
            logs = "Nenhum log disponível.\nO sistema está aguardando ações...";
        }
        logsTextView.setText(logs);

        // Scroll automático para o topo (logs mais recentes)
        if (logsTextView.getLayout() != null) {
            int scrollAmount = logsTextView.getLayout().getLineTop(0);
            logsTextView.scrollTo(0, scrollAmount);
        }
    }

    @Override
    public void onLogsUpdated(final String newLog) {
        if (!isActivityActive) return;

        handler.post(new Runnable() {
            @Override
            public void run() {
                // Adicionar novo log no início
                String currentText = logsTextView.getText().toString();
                if (currentText.equals("Nenhum log disponível.\nO sistema está aguardando ações...")) {
                    currentText = "";
                }

                String newText = newLog + "\n" + currentText;
                logsTextView.setText(newText);

                // Manter scroll no topo
                if (logsTextView.getLayout() != null) {
                    int scrollAmount = logsTextView.getLayout().getLineTop(0);
                    logsTextView.scrollTo(0, scrollAmount);
                }
            }
        });
    }

    @Override
    public void onLogsCleared() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                logsTextView.setText("Logs limpos.\nAguardando novos eventos...");
                Toast.makeText(LogsActivity.this, "Logs limpos", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void copyLogsToClipboard() {
        String logsText = logsTextView.getText().toString();
        if (logsText.isEmpty()) {
            Toast.makeText(this, "Nenhum log para copiar", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Logs do Sistema", logsText);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Logs copiados para área de transferência", Toast.LENGTH_SHORT).show();
    }

    private void clearLogs() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Limpar Logs")
                .setMessage("Deseja realmente limpar todos os logs?")
                .setPositiveButton("Limpar", (dialog, which) -> {
                    logManager.clearLogs();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActivityActive = true;
        updateLogsDisplay();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityActive = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remover listener para evitar memory leaks
        if (logManager != null) {
            logManager.unregisterListener(this);
        }
    }
}