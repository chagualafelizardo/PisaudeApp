package com.example.pisaudeapp;

import android.Manifest;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.pisaudeapp.ApiService;
import com.example.pisaudeapp.ApiService.Patient;
import com.example.pisaudeapp.ApiService.ApiCallback;
import com.example.pisaudeapp.ApiService.SmsCallback;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Button btnLoadPatients, btnSendAllSMS;
    private TextView tvStatus, tvStats;
    private ProgressBar progressBar;
    private LinearLayout layoutProgress;

    private ApiService apiService;
    private List<Patient> patientsList;

    private int smsSent = 0;
    private int smsFailed = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar views
        initViews();

        // Inicializar ApiService
        apiService = new ApiService(this);

        // Solicitar permissões
        requestPermissions();

        // Configurar botões
        setupButtons();
    }

    private void initViews() {
        btnLoadPatients = findViewById(R.id.btnLoadPatients);
        btnSendAllSMS = findViewById(R.id.btnSendAllSMS);
        tvStatus = findViewById(R.id.tvStatus);
        tvStats = findViewById(R.id.tvStats);
        progressBar = findViewById(R.id.progressBar);
        layoutProgress = findViewById(R.id.layoutProgress);

        // Inicialmente desabilitar botão de enviar SMS
        btnSendAllSMS.setEnabled(false);
    }

    private void requestPermissions() {
        String[] permissions = {
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_SMS
        };

        ActivityCompat.requestPermissions(this, permissions, 100);
    }

    private void setupButtons() {
        // Botão para carregar pacientes
        btnLoadPatients.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadPatients();
            }
        });

        // Botão para enviar todos os SMS
        btnSendAllSMS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSendAllConfirmation();
            }
        });
    }

    private void loadPatients() {
        showLoading(true);
        tvStatus.setText("Carregando pacientes...");

        apiService.getPatients(new ApiCallback() {
            @Override
            public void onSuccess(List<Patient> patients) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showLoading(false);
                        patientsList = patients;

                        if (patients.isEmpty()) {
                            tvStatus.setText("Nenhum paciente encontrado");
                            btnSendAllSMS.setEnabled(false);
                        } else {
                            tvStatus.setText("Encontrados " + patients.size() + " pacientes");
                            btnSendAllSMS.setEnabled(true);
                            updateStats();
                        }
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showLoading(false);
                        tvStatus.setText("Erro: " + error);
                        Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void showSendAllConfirmation() {
        if (patientsList == null || patientsList.isEmpty()) {
            Toast.makeText(this, "Nenhum paciente para enviar SMS", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Enviar SMS para Todos")
                .setMessage("Deseja enviar SMS para " + patientsList.size() + " pacientes?")
                .setPositiveButton("Enviar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sendAllSMS();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void sendAllSMS() {
        if (patientsList == null) return;

        // Resetar contadores
        smsSent = 0;
        smsFailed = 0;

        showProgress(true);
        tvStatus.setText("Enviando SMS...");

        apiService.sendBulkSMS(patientsList, new SmsCallback() {
            @Override
            public void onSmsSent(final String phone, final boolean success) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
                            smsSent++;
                        } else {
                            smsFailed++;
                        }

                        updateProgress();

                        // Verificar se terminou
                        if ((smsSent + smsFailed) == patientsList.size()) {
                            showProgress(false);
                            tvStatus.setText("Envio concluído!");

                            String result = "Sucesso: " + smsSent + " | Falhas: " + smsFailed;
                            Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
    }

    private void updateStats() {
        if (patientsList != null) {
            int validPatients = 0;
            for (Patient patient : patientsList) {
                if (patient.isValid()) {
                    validPatients++;
                }
            }

            String stats = "Total: " + patientsList.size() +
                    " | Válidos: " + validPatients;
            tvStats.setText(stats);
        }
    }

    private void updateProgress() {
        int total = patientsList.size();
        int current = smsSent + smsFailed;

        progressBar.setMax(total);
        progressBar.setProgress(current);

        String progressText = "Enviando... " + current + "/" + total +
                " (✓ " + smsSent + " | ✗ " + smsFailed + ")";
        tvStatus.setText(progressText);
    }

    private void showLoading(boolean show) {
        if (show) {
            progressBar.setVisibility(View.VISIBLE);
            btnLoadPatients.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            btnLoadPatients.setEnabled(true);
        }
    }

    private void showProgress(boolean show) {
        if (show) {
            layoutProgress.setVisibility(View.VISIBLE);
            btnLoadPatients.setEnabled(false);
            btnSendAllSMS.setEnabled(false);
        } else {
            layoutProgress.setVisibility(View.GONE);
            btnLoadPatients.setEnabled(true);
            btnSendAllSMS.setEnabled(true);
        }
    }
}