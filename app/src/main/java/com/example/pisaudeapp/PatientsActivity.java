package com.example.pisaudeapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class PatientsActivity extends AppCompatActivity implements PatientsAdapter.OnPatientClickListener {

    private static final String TAG = "PatientsActivity";
    private RecyclerView recyclerView;
    private PatientsAdapter adapter;
    private ProgressBar progressBar;
    private TextView tvEmpty, tvTitle, tvFilterLabel;
    private Spinner spinnerFilter;
    private ApiService apiService;
    private List<ApiService.Patient> allPatients = new ArrayList<>();
    private List<ApiService.Patient> filteredPatients = new ArrayList<>();
    private LogManager logManager;

    // Estados disponíveis para filtro
    private String[] estados = {
            "Todos",
            "chamada",
            "inicial",
            "faltosos",
            "abandono",
            "levantamento",
            "visita domiciliar"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patients);

        // Inicializar LogManager
        logManager = LogManager.getInstance();

        // Configurar toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Lista de Pacientes");

        // Inicializar views
        recyclerView = findViewById(R.id.recycler_view);
        progressBar = findViewById(R.id.progress_bar);
        tvEmpty = findViewById(R.id.tv_empty);
        tvTitle = findViewById(R.id.tv_title);
        tvFilterLabel = findViewById(R.id.tv_filter_label);
        spinnerFilter = findViewById(R.id.spinner_filter);

        // Configurar Spinner (dropdown)
        setupFilterSpinner();

        // Configurar RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PatientsAdapter(filteredPatients, this);
        recyclerView.setAdapter(adapter);

        // Inicializar API
        apiService = new ApiService(this);

        // Carregar pacientes
        loadPatients();

        // Adicionar log
        logManager.addLog("📋 Tela de pacientes aberta");
    }

    private void setupFilterSpinner() {
        // Criar adapter para o spinner
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                estados
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilter.setAdapter(spinnerAdapter);

        // Definir "chamada" como seleção padrão
        for (int i = 0; i < estados.length; i++) {
            if (estados[i].equalsIgnoreCase("chamada")) {
                spinnerFilter.setSelection(i);
                break;
            }
        }

        // Listener para quando o filtro mudar
        spinnerFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selectedEstado = estados[position];
                filterPatients(selectedEstado);
                logManager.addLog("🔍 Filtro aplicado: " + selectedEstado);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // Nada a fazer
            }
        });
    }

    private void loadPatients() {
        showLoading(true);
        logManager.addLog("📥 Carregando lista de pacientes...");

        new Thread(() -> {
            apiService.getPatients(new ApiService.ApiCallback() {
                @Override
                public void onSuccess(List<ApiService.Patient> patients) {
                    runOnUiThread(() -> {
                        allPatients.clear();
                        allPatients.addAll(patients);

                        // Filtrar com estado padrão "chamada"
                        filterPatients("chamada");

                        showLoading(false);
                        logManager.addLog("✅ " + patients.size() + " pacientes carregados");
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText("Erro ao carregar pacientes: " + error);
                        tvTitle.setText("Erro no carregamento");
                        showLoading(false);
                        logManager.addLog("❌ Erro ao carregar pacientes: " + error);
                    });
                }
            });
        }).start();
    }

    private void filterPatients(String estado) {
        filteredPatients.clear();

        if (estado.equalsIgnoreCase("Todos")) {
            filteredPatients.addAll(allPatients);
        } else {
            for (ApiService.Patient patient : allPatients) {
                if (patient.stateDescription != null &&
                        patient.stateDescription.toLowerCase().contains(estado.toLowerCase())) {
                    filteredPatients.add(patient);
                }
            }
        }

        adapter.notifyDataSetChanged();

        // Atualizar UI
        if (filteredPatients.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            if (estado.equalsIgnoreCase("Todos")) {
                tvEmpty.setText("Nenhum paciente encontrado");
                tvTitle.setText("Nenhum paciente");
            } else {
                tvEmpty.setText("Nenhum paciente com estado: " + estado);
                tvTitle.setText("0 pacientes com estado: " + estado);
            }
        } else {
            tvEmpty.setVisibility(View.GONE);
            if (estado.equalsIgnoreCase("Todos")) {
                tvTitle.setText(filteredPatients.size() + " Pacientes Encontrados");
            } else {
                tvTitle.setText(filteredPatients.size() + " pacientes com estado: " + estado);
            }
        }

        // Atualizar contador no filtro
        tvFilterLabel.setText("Filtrar por estado (" + filteredPatients.size() + " encontrados)");
    }

    @Override
    public void onPatientClick(ApiService.Patient patient) {
        // Quando clicar em um paciente, mostra opções
        showPatientOptions(patient);
    }

    @Override
    public void onCallClick(ApiService.Patient patient) {
        // Chamar diretamente
        makePhoneCall(patient);
    }

    private void showPatientOptions(ApiService.Patient patient) {
        String[] options;

        if (patient.contact != null && !patient.contact.trim().isEmpty()) {
            options = new String[]{
                    "Ligar para " + patient.contact,
                    "Ver detalhes",
                    "Enviar SMS",
                    "Cancelar"
            };
        } else {
            options = new String[]{
                    "Ver detalhes",
                    "Paciente sem telefone",
                    "Cancelar"
            };
        }

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle(patient.fullname)
                .setItems(options, (dialog, which) -> {
                    if (patient.contact != null && !patient.contact.trim().isEmpty()) {
                        if (which == 0) {
                            makePhoneCall(patient);
                        } else if (which == 1) {
                            showPatientDetails(patient);
                        } else if (which == 2) {
                            sendSmsToPatient(patient);
                        }
                    } else {
                        if (which == 0) {
                            showPatientDetails(patient);
                        }
                    }
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void makePhoneCall(ApiService.Patient patient) {
        if (patient.contact == null || patient.contact.trim().isEmpty()) {
            logManager.addLog("📭 " + patient.fullname + " não tem telefone cadastrado");
            return;
        }

        String phoneNumber = patient.contact.trim().replace("+", "").replace(" ", "");

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Confirmar Chamada")
                .setMessage("Deseja ligar para " + patient.fullname + "?\n\nTelefone: " + phoneNumber)
                .setPositiveButton("Ligar", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:" + phoneNumber));
                    startActivity(intent);
                    logManager.addLog("📞 Chamando " + patient.fullname + " (" + phoneNumber + ")");
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showPatientDetails(ApiService.Patient patient) {
        String details = "Nome: " + patient.fullname + "\n\n" +
                "Telefone: " + (patient.contact != null ? patient.contact : "Não informado") + "\n\n" +
                "Gênero: " + (patient.gender != null ? patient.gender : "Não informado") + "\n\n" +
                "Estado: " + (patient.stateDescription != null ? patient.stateDescription : "Não informado") + "\n\n" +
                "Mensagem: " + (patient.textMessageDescription != null ? patient.textMessageDescription : "Não informada");

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Detalhes do Paciente")
                .setMessage(details)
                .setPositiveButton("OK", null)
                .setNeutralButton("Ligar", (dialog, which) -> {
                    if (patient.contact != null && !patient.contact.trim().isEmpty()) {
                        makePhoneCall(patient);
                    }
                })
                .show();
    }

    private void sendSmsToPatient(ApiService.Patient patient) {
        if (patient.contact == null || patient.contact.trim().isEmpty()) {
            logManager.addLog("📭 " + patient.fullname + " não tem telefone para SMS");
            return;
        }

        // Aqui você pode implementar o envio de SMS individual
        // Por enquanto, apenas mostra uma mensagem
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Enviar SMS")
                .setMessage("Enviar SMS para " + patient.fullname + "?\n\n" +
                        "Telefone: " + patient.contact)
                .setPositiveButton("Enviar", (dialog, which) -> {
                    // Implementar envio de SMS aqui
                    logManager.addLog("📱 SMS enviado para " + patient.fullname);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            recyclerView.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.GONE);
            spinnerFilter.setVisibility(View.GONE);
            tvFilterLabel.setVisibility(View.GONE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            spinnerFilter.setVisibility(View.VISIBLE);
            tvFilterLabel.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        logManager.addLog("🔄 Retornando à lista de pacientes");
    }
}